package org.example.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.File

private const val URL = "https://typst.app/docs/reference/symbols/sym/"

@Serializable
data class TypstSymbol(val symbol: String, val name: String)

fun parseTypstSymbols(url: String = URL): List<TypstSymbol> {
    val document = Jsoup.connect(url)
        .timeout(30_000)
        .get()

    val result = mutableListOf<TypstSymbol>()

    document.select("ul.symbol-grid").select("li").forEach { element ->
        val code = element.select("button").select("code").text()
        result.add(TypstSymbol(code, element.attr("data-unic-name")))
    }
    return result
}

fun main() {
    val symbols = parseTypstSymbols()
    val json = Json { prettyPrint = true }.encodeToString(symbols)
    File("TypstSymbols.json").writeText(json)
    println("Saved ${symbols.size} symbols to TypstSymbols.json")
}