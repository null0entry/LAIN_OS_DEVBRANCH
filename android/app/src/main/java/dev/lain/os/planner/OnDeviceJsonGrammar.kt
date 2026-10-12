package dev.lain.os.planner

import org.json.JSONObject
import org.json.JSONTokener

/** Decoder syntax constraints only; the trusted Python registry/policy still validates proposals. */
internal object OnDeviceJsonGrammar {
    private const val MAX_GRAMMAR_BYTES = 16_384
    private val NAME = Regex("[a-z][a-z0-9_.]{0,127}")
    private val JSON = """
        ws ::= [ \t\n\r]{0,8}
        string ::= "\"" char* "\""
        char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
        object ::= "{" ws (string ws ":" ws value (ws "," ws string ws ":" ws value)*)? ws "}"
        array ::= "[" ws (value (ws "," ws value)*)? ws "]"
        value ::= object | array | string | number | "true" | "false" | "null"
        number ::= "-"? ("0" | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [+-]? [0-9]+)?
    """.trimIndent()

    fun planner(payload: String): String {
        val tokener = JSONTokener(payload)
        val request = tokener.nextValue() as? JSONObject ?: error("invalid planner request")
        require(tokener.nextClean() == '\u0000')
        val capabilities = request.getJSONArray("capabilities")
        require(capabilities.length() <= 64)
        val names = (0 until capabilities.length()).map {
            val name = capabilities.getJSONObject(it).get("name") as? String
                ?: throw IllegalArgumentException("invalid capability name")
            require(NAME.matches(name))
            name
        }
        require(names.distinct().size == names.size)
        val root = if (names.isEmpty()) "root ::= terminal" else "root ::= continued | terminal"
        val terminal = """
            terminal ::= "{" ws "\"status\"" ws ":" ws ("\"complete\"" | "\"blocked\"") ws "," ws "\"reason\"" ws ":" ws string ws "," ws "\"actions\"" ws ":" ws "[" ws "]" ws "}" ws
        """.trimIndent()
        val proposals = if (names.isEmpty()) "" else "\n" + """
            continued ::= "{" ws "\"status\"" ws ":" ws "\"continue\"" ws "," ws "\"reason\"" ws ":" ws string ws "," ws "\"actions\"" ws ":" ws "[" ws action ws "]" ws "}" ws
            action ::= "{" ws "\"type\"" ws ":" ws capability ws "," ws "\"arguments\"" ws ":" ws object ws "}"
        """.trimIndent() + "\ncapability ::= " + names.joinToString(" | ") {
            JSONObject.quote(JSONObject.quote(it))
        }
        return bounded(root + "\n" + terminal + proposals + "\n" + JSON)
    }

    fun routing(): String = bounded("""
        root ::= "{" ws "\"disposition\"" ws ":" ws (task | conversational) ws "}" ws
        task ::= "\"task\"" ws "," ws "\"response_text\"" ws ":" ws "\"\"" ws "," ws "\"reason\"" ws ":" ws reason
        conversational ::= ("\"chat\"" | "\"clarify\"") ws "," ws "\"response_text\"" ws ":" ws response ws "," ws "\"reason\"" ws ":" ws reason
        reason ::= "\"" char{1,48} "\""
        response ::= "\"" char{0,200} "\""
    """.trimIndent() + "\n" + JSON)

    private fun bounded(grammar: String): String {
        require(grammar.toByteArray(Charsets.UTF_8).size <= MAX_GRAMMAR_BYTES)
        return grammar
    }
}
