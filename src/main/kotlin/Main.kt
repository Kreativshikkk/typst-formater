package org.example

import kotlinx.coroutines.runBlocking
import org.example.agent.createFormattingAgent
import java.io.File


fun main(args: Array<String>) {
    val toWrite = args[0].toBoolean()
    val userPrompt = object {}.javaClass.getResource("/UserPrompt.txt")?.readText()
        ?: error("UserPrompt.txt not found in resources")
    val systemPrompt = object {}.javaClass.getResource("/SystemPromptFormatting.txt")?.readText()
        ?: error("SystemPromptFormatting.txt not found in resources")
    val agent = createFormattingAgent(systemPrompt)
    val output = runBlocking { agent.runAndGetResult(userPrompt) }
    if (toWrite) {
        val inputFile = File("input.typ")
        val outputFile = File("output.pdf")

        inputFile.writeText(output.toString())
        ProcessBuilder("typst", "compile", inputFile.absolutePath, outputFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    }
    println(output)
}