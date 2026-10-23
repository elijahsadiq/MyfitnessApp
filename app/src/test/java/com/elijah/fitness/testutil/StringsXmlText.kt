package com.elijah.fitness.testutil

import com.elijah.fitness.util.TextResolver
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [TextResolver] for JVM unit tests. Reads the app's real
 * `res/values/strings.xml` and `plurals.xml` from disk and maps the
 * generated `R.string` / `R.plurals` ids back to their names, so a test
 * checks the exact copy the app ships — no second English copy to drift.
 *
 * Plurals follow English rules (1 → "one", anything else → "other").
 */
object StringsXmlText : TextResolver {

    private val valuesDir: File by lazy {
        listOf("src/main/res/values", "app/src/main/res/values")
            .map(::File)
            .firstOrNull { File(it, "strings.xml").isFile }
            ?: error("Can't find res/values from ${File(".").absolutePath}")
    }

    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        listOf("strings.xml", "plurals.xml").forEach { name ->
            val file = File(valuesDir, name)
            if (file.isFile) parse(file)
        }
    }

    private val stringNames: Map<Int, String> by lazy { idNames("string") }
    private val pluralNames: Map<Int, String> by lazy { idNames("plurals") }

    override fun string(id: Int, vararg args: Any): String {
        val name = stringNames[id] ?: error("No R.string with id $id")
        val template = strings[name] ?: error("No <string name=\"$name\">")
        return format(template, args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = pluralNames[id] ?: error("No R.plurals with id $id")
        val items = plurals[name] ?: error("No <plurals name=\"$name\">")
        val template = (if (count == 1) items["one"] else null)
            ?: items["other"]
            ?: error("<plurals name=\"$name\"> has no 'other' item")
        return format(template, args)
    }

    private fun format(template: String, args: Array<out Any>): String =
        if (args.isEmpty()) template else String.format(Locale.getDefault(), template, *args)

    private fun idNames(type: String): Map<Int, String> =
        Class.forName("com.elijah.fitness.R\$$type").fields
            .associate { it.getInt(null) to it.name }

    private fun parse(file: File) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            strings[el.getAttribute("name")] = unescape(el.textContent)
        }
        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            val byQuantity = mutableMapOf<String, String>()
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                byQuantity[item.getAttribute("quantity")] = unescape(item.textContent)
            }
            plurals[el.getAttribute("name")] = byQuantity
        }
    }

    /** The subset of Android's resource-string escaping the app uses. */
    private fun unescape(raw: String): String {
        val trimmed = raw.trim()
        val quoted = trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
        val body = if (quoted) trimmed.substring(1, trimmed.length - 1)
        else trimmed.replace(Regex("\\s+"), " ")
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                val next = body[i + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> if (i + 5 < body.length) {
                        out.append(body.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(next) // \' \" \\ \@ \?
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
