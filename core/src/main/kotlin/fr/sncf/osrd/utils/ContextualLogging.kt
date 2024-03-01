package fr.sncf.osrd.utils

import java.io.FileWriter
import kotlin.time.DurationUnit
import kotlin.time.measureTime

class ContextualLogging {
    private val file = FileWriter("logs.csv", false)
    private var context = mutableMapOf<String, Any>()
    private val expectedKeys =
        listOf(
            "block",
            "time",
            "lookahead",
            "type",
            "execution-time-ms",
            "skip",
            "required-zone-count",
            "signal",
            "zone-number",
            "signaling-type"
        )

    init {
        val line = StringBuilder()
        for (i in expectedKeys.indices) {
            line.append(expectedKeys[i])
            line.append(";")
        }
        line.append("msg\n")
        file.write(line.toString())
    }

    fun <T> withContext(
        type: String,
        msg: String? = null,
        extraContext: Map<String, Any> = mapOf(),
        f: () -> T
    ): T {
        val oldContext = context.toMutableMap()
        context.putAll(extraContext)
        log(type, msg)
        var res: T
        val time = measureTime { res = f() }
        context["execution-time-ms"] = time.toDouble(DurationUnit.MILLISECONDS)
        log("$type-end")
        context = oldContext
        return res
    }

    fun log(type: String, msg: String?, extraContext: Map<String, Any> = mapOf()) {
        val oldContext = context.toMutableMap()
        context.putAll(extraContext)
        context["type"] = type
        val line = StringBuilder()
        for (k in expectedKeys) {
            line.append(context.getOrDefault(k, ""))
            line.append(";")
        }
        for (entry in context) {
            if (!expectedKeys.contains(entry.key)) {
                line.append("(${entry.key}=${entry.value})")
            }
        }
        line.append("$msg\n")
        file.write(line.toString())
        context = oldContext
    }

    companion object {
        var instance = ContextualLogging()
    }
}

fun <T> withContext(
    type: String,
    msg: String? = null,
    extraContext: Map<String, Any> = mapOf(),
    f: () -> T
): T {
    return ContextualLogging.instance.withContext(type, msg, extraContext, f)
}

fun log(type: String, msg: String? = null, extraContext: Map<String, Any> = mapOf()) {
    ContextualLogging.instance.log(type, msg, extraContext)
}
