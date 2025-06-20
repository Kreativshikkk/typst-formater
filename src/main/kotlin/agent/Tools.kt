package org.example.agent

import ai.grazie.model.task.library.v2.code.fleet.FleetGenerateCodeTask.Schema.prompt
import ai.jetbrains.code.prompt.executor.clients.grazie.koog.model.JetBrainsAIModels
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.utils.TypstSymbol
import org.example.utils.simpleGrazieExecutor
import java.io.File
import kotlin.math.min

fun ratio(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0

    val maxLength = maxOf(a.length, b.length)
    return 1.0 - (levenstein(a, b).toDouble() / maxLength)
}

fun levenstein(a: String, b: String): Int {
    val m = a.length
    val n = b.length

    if (m == 0) return n
    if (n == 0) return m

    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
        }
        prev = curr.also { curr = prev }
    }
    return prev[n]
}

object Tools : ToolSet {
    val text = object {}.javaClass.getResource("/TypstSymbols.json")?.readText()
        ?: error("TypstSymbols.json not found in resources")

    val list = Json.decodeFromString<List<TypstSymbol>>(text)

    @Tool
    @LLMDescription(
        """
        The tool retrieves math symbols from the typst documentation.
        It accepts a query string and returns some suitable (according to Levenstein distance) symbols' code.
        The search is done by 'data-unic-name' attribute of the symbol, so the query should be roughly similar to the name of the symbol.
    """
    )
    fun retrieveTypstMathSymbols(query: String, ratio: Double = 0.7): List<String> {
        return list.filter { ratio(query, it.name) >= ratio }.map { it.symbol }
    }

    fun compileTypstCode(
        code: String
    ): String {
        val tempDir = kotlin.io.path.createTempDirectory("typst_compile_").toFile()
        try {
            val inputFile = File(tempDir, "input.typ")
            val outputFile = File(tempDir, "output.pdf")

            inputFile.writeText(code)

            val process = ProcessBuilder("typst", "compile", inputFile.absolutePath, outputFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            return if (exitCode == 0) {
                outputFile.copyTo(File("./output.pdf"), overwrite = true)
                ""
            } else {
                "Compilation failed:\n$output"
            }
        } catch (e: Exception) {
            return "Error: ${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun callValidatingAgent(
        code: String
    ): String? {
        val nonFormattedText = object {}.javaClass.getResource("/UserPrompt.txt")?.readText()
            ?: error("UserPrompt.txt not found in resources")
        val systemPromptValidating = object {}.javaClass.getResource("/SystemPromptValidating.txt")?.readText()
            ?: error("SystemPromptValidating.txt not found in resources")
        print(code)
        val validatingAgent = ValidatingAgent(systemPromptValidating).createValidatingAgent()
        val promptExecutor = simpleGrazieExecutor(System.getenv("JWT_TOKEN"))
        var userPrompt = ""
        for (i in 0..5) {
            val nextMessages = runBlocking {
                validatingAgent.runAndGetResult("Non-formatted text: $nonFormattedText\n\n Formatted text:$code")
            }
            userPrompt += nextMessages.toString()
        }
        val nextMessages = runBlocking {
            promptExecutor.execute(
                prompt = prompt("cooking") {
                        system("You are given 5 LLM responses. Write a summarized response, including the most common thoughts.")
                        user(userPrompt)
        },
        model = JetBrainsAIModels.Anthropic.Sonnet_3_7,
        tools = emptyList()
        )
    }
        return nextMessages.toString()
    }
}