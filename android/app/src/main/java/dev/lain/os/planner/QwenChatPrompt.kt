package dev.lain.os.planner

/** Plain conversational context: no capability, approval, or task authority. */
internal data class QwenChatTurn(val role: String, val text: String)

internal object QwenChatPrompt {
    private const val MAX_USER_BYTES = 4096
    private const val MAX_ASSISTANT_BYTES = 16384
    private const val MAX_PROMPT_BYTES = 8192
    private val SYSTEM = "You are LAIN, a helpful local AI assistant. Respond naturally in plain text. " +
        "You are chatting, not planning tasks. You cannot execute Android actions or grant approvals."

    fun build(turns: List<QwenChatTurn>): String {
        require(turns.isNotEmpty() && turns.size <= 32 && turns.last().role == "user")
        for (turn in turns) {
            require(turn.role == "user" || turn.role == "assistant")
            val size = turn.text.toByteArray(Charsets.UTF_8).size
            require(turn.text.isNotBlank() && size <= if (turn.role == "user") MAX_USER_BYTES else MAX_ASSISTANT_BYTES)
            require(!turn.text.contains('\u0000'))
        }
        // Qwen chat delimiters are host-owned. Escape user/model occurrences to avoid role spoofing.
        fun escape(text: String): String = text.replace("<|", "‹|")
        var start = 0
        while (true) {
            val prompt = buildString {
                append("<|im_start|>system\n").append(SYSTEM).append("<|im_end|>\n")
                for (i in start until turns.size) {
                    val turn = turns[i]
                    append("<|im_start|>").append(turn.role).append('\n')
                    append(escape(turn.text)).append("<|im_end|>\n")
                }
                append("<|im_start|>assistant\n<think>\n\n</think>\n\n")
            }
            if (prompt.toByteArray(Charsets.UTF_8).size <= MAX_PROMPT_BYTES) return prompt
            require(start < turns.lastIndex) { "Current chat message exceeds prompt budget" }
            start++
        }
    }
}
