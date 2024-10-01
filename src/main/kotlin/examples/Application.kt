package examples

import org.slf4j.LoggerFactory.getLogger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.Locale.CHINESE
import java.util.Locale.ENGLISH
import java.util.Locale.FRENCH
import java.util.Locale.GERMAN
import java.util.Locale.ITALIAN
import java.util.Locale.JAPANESE
import java.util.concurrent.CompletableFuture.supplyAsync
import kotlin.time.measureTime

@SpringBootApplication
class Application {

    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient = builder.build()
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
@RequestMapping("/translate")
class TranslationController(
    private val translator: Translator
) {

    @PostMapping
    fun translate(@RequestBody request: TranslationRequest): TranslationResponse {
        val translations = request.targetLanguages.sortedBy { it.language }
            .map { language -> language to asyncTranslate(request.sourceLanguage, language, request.text) }
            .associate { (language, supplier) -> language to supplier.get() }
        return TranslationResponse(translations)
    }

    private fun asyncTranslate(sourceLanguage: Locale, language: Locale, text: String) =
        supplyAsync { translator.translate(sourceLanguage, language, text) }

    data class TranslationRequest(
        val sourceLanguage: Locale,
        val targetLanguages: Set<Locale>,
        val text: String
    ) {
        init {
            require(targetLanguages.isNotEmpty()) { "You need to provide at least one target language!" }
            require(text.isNotBlank()) { "The text to be translated is not allowed to be blank!" }
        }
    }

    data class TranslationResponse(
        val translations: Map<Locale, String>
    )
}

@Component
class Translator(
    private val chatClient: ChatClient
) {

    private val log = getLogger(javaClass)

    fun translate(sourceLocale: Locale, targetLocale: Locale, text: String): String {
        val sourceLanguage = languageName(sourceLocale)
        val targetLanguage = languageName(targetLocale)

        val systemPrompt = "You are a $sourceLanguage to $targetLanguage translator."
        val userPrompt = "Translate: $text"

        var result: String?
        val duration = measureTime {
            result = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content()
        }

        log.debug(
            buildString {
                appendLine()
                appendLine("S: $systemPrompt")
                appendLine("U: $userPrompt")
                appendLine("R: $result")
                appendLine("D: $duration")
            }
        )

        return result!!
    }
}

private fun languageName(locale: Locale): String =
    locale.getDisplayLanguage(ENGLISH)
