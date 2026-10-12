package dev.lain.os.planner

internal data class RecommendedModel(
    val id: String,
    val displayName: String,
    val url: String,
    val expectedSha256: String,
    val expectedBytes: Long,
)

internal object RecommendedModelCatalog {
    private val recommended = RecommendedModel(
        id = "Qwen3-1.7B-Q4_K_M.gguf",
        displayName = "Qwen3 1.7B Q4_K_M",
        url = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        expectedSha256 = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
        expectedBytes = 1_282_439_264L,
    )

    fun default(): RecommendedModel = recommended
}
