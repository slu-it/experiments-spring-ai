package examples

import org.slf4j.LoggerFactory.getLogger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
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

@Component
class TestApplicationRunner(
    private val translator: Translator
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        val text = "Hallo, wie geht es Ihnen heute? Es ist ja echt ein wunderschöner Tag."

        val total = measureTime {
            listOf(ENGLISH, FRENCH, ITALIAN, JAPANESE, CHINESE)
                .map { language -> language to supplyAsync { translator.translate(GERMAN, language, text) } }
                .map { (language, supplier) -> language to supplier.get() }
                .forEach { (language, translation) ->
                    println("${languageName(language)}: $translation")
                }
        }
        println("total: $total")
    }
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

        log.info(
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
