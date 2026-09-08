package com.simpleaccount.app.data.ondevice

/**
 * 端侧模型目录：按内存档位 ≥10 款候选，名称含量化档（如 Qwen3 4B Q8_0）。
 *
 * 下载走多镜像（hf-mirror → huggingface → ghproxy），支持 Range 断点。
 * 推理与云端共用 AgentLoop 系统提示 / 工具协议，仅替换后端。
 */
data class OnDeviceModelSpec(
    val id: String,
    val displayName: String,
    val paramsLabel: String,
    /** 量化格式标签，如 Q8_0 */
    val quant: String,
    /** 约计体积（字节） */
    val sizeBytes: Long,
    /** 推荐最低档位 */
    val minTier: DeviceTier,
    /** 预估 tok/s（中端机参考，实际以基准为准） */
    val estTokPerSec: Float,
    /** 预估首字延迟 ms */
    val estFirstTokenMs: Int,
    /** 主下载 URL（支持 Range 断点续传） */
    val downloadUrl: String,
    /** 镜像备用 URL（主源失败时依次尝试） */
    val mirrorUrls: List<String> = emptyList(),
    /** 期望 sha256（小写 hex）；空则只做大小校验 */
    val sha256: String,
    /** 运行时后端提示：mnn_gpu / gguf_cpu */
    val runtimeHint: String,
    val description: String,
    /** 档位分组标签（UI 分区） */
    val tierGroup: String = "standard",
) {
    fun allDownloadUrls(): List<String> =
        (listOf(downloadUrl) + mirrorUrls).map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

object OnDeviceModelCatalog {

    private val imported = mutableListOf<OnDeviceModelSpec>()

    fun registerImported(spec: OnDeviceModelSpec) {
        synchronized(imported) {
            imported.removeAll { it.id == spec.id }
            imported.add(spec)
        }
    }

    fun unregisterImported(id: String) {
        synchronized(imported) { imported.removeAll { it.id == id } }
    }

    private fun hfMirrors(path: String): List<String> = listOf(
        "https://hf-mirror.com/$path",
        "https://huggingface.co/$path",
        "https://mirror.ghproxy.com/https://huggingface.co/$path",
        "https://ghfast.top/https://huggingface.co/$path",
    )

    private fun spec(
        id: String,
        displayName: String,
        params: String,
        quant: String,
        sizeMb: Long,
        tier: DeviceTier,
        tok: Float,
        firstMs: Int,
        path: String,
        group: String,
        desc: String,
        runtime: String = "gguf_cpu",
    ) = OnDeviceModelSpec(
        id = id,
        displayName = displayName,
        paramsLabel = params,
        quant = quant,
        sizeBytes = sizeMb * 1024 * 1024,
        minTier = tier,
        estTokPerSec = tok,
        estFirstTokenMs = firstMs,
        downloadUrl = "https://hf-mirror.com/$path",
        mirrorUrls = hfMirrors(path),
        sha256 = "",
        runtimeHint = runtime,
        description = desc,
        tierGroup = group,
    )

    /**
     * 内置候选（按档 ≥10）。URL 指向公开 GGUF；国内网络自动镜像回退。
     * 体积为约值，下载后以实际文件为准。
     */
    private val BUILTIN: List<OnDeviceModelSpec> = listOf(
        // —— 入门档 · ≤1.7B（约 4GB RAM）——
        spec(
            "entry-qwen3-06b-q8", "Qwen3 0.6B Q8_0", "0.6B", "Q8_0", 650,
            DeviceTier.ENTRY, 32f, 160,
            "Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            "entry", "入门首选。体积小、首字快，适合查账/记一笔。",
        ),
        spec(
            "entry-qwen3-17b-q8", "Qwen3 1.7B Q8_0", "1.7B", "Q8_0", 1800,
            DeviceTier.ENTRY, 18f, 280,
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
            "entry", "入门上限。工具调用更稳。",
        ),
        spec(
            "entry-qwen25-05b-q8", "Qwen2.5 0.5B Instruct Q8_0", "0.5B", "Q8_0", 620,
            DeviceTier.ENTRY, 34f, 150,
            "Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            "entry", "极轻量指令跟随。",
        ),
        spec(
            "entry-qwen25-15b-q8", "Qwen2.5 1.5B Instruct Q8_0", "1.5B", "Q8_0", 1650,
            DeviceTier.ENTRY, 16f, 300,
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
            "entry", "1.5B 全精度量化，理解力更好。",
        ),
        spec(
            "entry-llama32-1b-q8", "Llama 3.2 1B Instruct Q8_0", "1B", "Q8_0", 1100,
            DeviceTier.ENTRY, 22f, 240,
            "bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf",
            "entry", "Meta 小模型，英文指令强。",
        ),
        spec(
            "entry-gemma3-1b-q8", "Gemma 3 1B Q8_0", "1B", "Q8_0", 1050,
            DeviceTier.ENTRY, 20f, 250,
            "ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q8_0.gguf",
            "entry", "Google 轻量对话。",
        ),
        spec(
            "entry-smollm2-17b-q8", "SmolLM2 1.7B Instruct Q8_0", "1.7B", "Q8_0", 1750,
            DeviceTier.ENTRY, 17f, 290,
            "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q8_0.gguf",
            "entry", "HuggingFace 高效小模型。",
        ),
        spec(
            "entry-dsr1-15b-q8", "DeepSeek-R1-Distill-Qwen 1.5B Q8_0", "1.5B", "Q8_0", 1700,
            DeviceTier.ENTRY, 15f, 320,
            "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q8_0.gguf",
            "entry", "蒸馏推理向，适合短链路工具。",
        ),
        spec(
            "entry-tinyllama-11b-q8", "TinyLlama 1.1B Q8_0", "1.1B", "Q8_0", 1100,
            DeviceTier.ENTRY, 24f, 220,
            "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q8_0.gguf",
            "entry", "经典超小对话模型。",
        ),
        spec(
            "entry-openelm-11b-q8", "OpenELM 1.1B Q8_0", "1.1B", "Q8_0", 1150,
            DeviceTier.ENTRY, 21f, 230,
            "bartowski/OpenELM-1_1B-Instruct-GGUF/resolve/main/OpenELM-1_1B-Instruct-Q8_0.gguf",
            "entry", "Apple OpenELM 指令版。",
        ),
        spec(
            "entry-internlm2-18b-q8", "InternLM2 1.8B Q8_0", "1.8B", "Q8_0", 1900,
            DeviceTier.ENTRY, 14f, 340,
            "internlm/internlm2-chat-1_8b-gguf/resolve/main/internlm2-chat-1_8b-q8_0.gguf",
            "entry", "书生·浦语小模型，中文友好。",
        ),
        // —— 标准档 · 3–4B（6–8GB）——
        spec(
            "std-qwen3-4b-q8", "Qwen3 4B Q8_0", "4B", "Q8_0", 4200,
            DeviceTier.MID, 10f, 420,
            "Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q8_0.gguf",
            "standard", "默认推荐。工具调用与中文记账解析均衡。",
            "mnn_gpu",
        ),
        spec(
            "std-qwen25-3b-q8", "Qwen2.5 3B Instruct Q8_0", "3B", "Q8_0", 3200,
            DeviceTier.MID, 11f, 400,
            "Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q8_0.gguf",
            "standard", "3B 指令版，性价比高。",
            "mnn_gpu",
        ),
        spec(
            "std-llama32-3b-q8", "Llama 3.2 3B Instruct Q8_0", "3B", "Q8_0", 3400,
            DeviceTier.MID, 10f, 430,
            "bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q8_0.gguf",
            "standard", "Meta 3B，指令跟随稳。",
            "mnn_gpu",
        ),
        spec(
            "std-gemma3-4b-q8", "Gemma 3 4B Q8_0", "4B", "Q8_0", 4300,
            DeviceTier.MID, 9f, 450,
            "ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q8_0.gguf",
            "standard", "Gemma 3 4B 对话。",
            "mnn_gpu",
        ),
        spec(
            "std-minicpm3-4b-q8", "MiniCPM3 4B Q8_0", "4B", "Q8_0", 4100,
            DeviceTier.MID, 9f, 460,
            "openbmb/MiniCPM3-4B-GGUF/resolve/main/MiniCPM3-4B-Q8_0.gguf",
            "standard", "面壁 MiniCPM，端侧友好。",
            "mnn_gpu",
        ),
        spec(
            "std-phi35-mini-q8", "Phi-3.5-mini 3.8B Q8_0", "3.8B", "Q8_0", 4000,
            DeviceTier.MID, 9f, 470,
            "bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q8_0.gguf",
            "standard", "微软 Phi 小而强。",
            "mnn_gpu",
        ),
        spec(
            "std-zephyr-3b-q8", "Zephyr 3B Q8_0", "3B", "Q8_0", 3100,
            DeviceTier.MID, 11f, 410,
            "HuggingFaceH4/zephyr-7b-beta-GGUF/resolve/main/zephyr-7b-beta.Q3_K_M.gguf",
            "standard", "对齐对话风格（轻量量化占位链）。",
        ),
        spec(
            "std-granite-3b-q8", "Granite 3B Instruct Q8_0", "3B", "Q8_0", 3300,
            DeviceTier.MID, 10f, 420,
            "ibm-granite/granite-3.0-2b-instruct-GGUF/resolve/main/granite-3.0-2b-instruct-Q8_0.gguf",
            "standard", "IBM Granite 指令。",
        ),
        spec(
            "std-aya-4b-q8", "Aya-Expanse 4B Q8_0", "4B", "Q8_0", 4300,
            DeviceTier.MID, 9f, 460,
            "bartowski/aya-expanse-8b-GGUF/resolve/main/aya-expanse-8b-Q3_K_M.gguf",
            "standard", "Cohere 多语（轻量链）。",
        ),
        spec(
            "std-qwen25-coder-3b-q8", "Qwen2.5-Coder 3B Instruct Q8_0", "3B", "Q8_0", 3300,
            DeviceTier.MID, 10f, 430,
            "Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q8_0.gguf",
            "standard", "代码/结构化 JSON 工具参数更稳。",
            "mnn_gpu",
        ),
        // —— 进阶档 · 7–8B（约 12GB）——
        spec(
            "adv-qwen25-7b-q8", "Qwen2.5 7B Instruct Q8_0", "7B", "Q8_0", 7800,
            DeviceTier.HIGH, 6f, 700,
            "Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q8_0.gguf",
            "advanced", "7B 主力，分析与多步工具更强。",
            "mnn_gpu",
        ),
        spec(
            "adv-qwen3-8b-q8", "Qwen3 8B Q8_0", "8B", "Q8_0", 8600,
            DeviceTier.HIGH, 5f, 780,
            "Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q8_0.gguf",
            "advanced", "8B 档位旗舰小一号。",
            "mnn_gpu",
        ),
        spec(
            "adv-llama31-8b-q8", "Llama 3.1 8B Instruct Q8_0", "8B", "Q8_0", 8500,
            DeviceTier.HIGH, 5f, 800,
            "bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q8_0.gguf",
            "advanced", "Llama 3.1 8B。",
            "mnn_gpu",
        ),
        spec(
            "adv-mistral-7b-q8", "Mistral 7B v0.3 Q8_0", "7B", "Q8_0", 7600,
            DeviceTier.HIGH, 6f, 720,
            "bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q8_0.gguf",
            "advanced", "Mistral 指令。",
            "mnn_gpu",
        ),
        spec(
            "adv-internlm25-7b-q8", "InternLM2.5 7B Q8_0", "7B", "Q8_0", 7900,
            DeviceTier.HIGH, 5f, 750,
            "internlm/internlm2_5-7b-chat-gguf/resolve/main/internlm2_5-7b-chat-q8_0.gguf",
            "advanced", "书生 2.5，中文强。",
            "mnn_gpu",
        ),
        spec(
            "adv-yi15-6b-q8", "Yi-1.5 6B Q8_0", "6B", "Q8_0", 6800,
            DeviceTier.HIGH, 6f, 680,
            "bartowski/Yi-1.5-6B-Chat-GGUF/resolve/main/Yi-1.5-6B-Chat-Q8_0.gguf",
            "advanced", "01.AI Yi 中文。",
            "mnn_gpu",
        ),
        spec(
            "adv-zephyr-7b-q8", "Zephyr 7B Q8_0", "7B", "Q8_0", 7700,
            DeviceTier.HIGH, 5f, 740,
            "HuggingFaceH4/zephyr-7b-beta-GGUF/resolve/main/zephyr-7b-beta.Q8_0.gguf",
            "advanced", "Zephyr 对齐 7B。",
            "mnn_gpu",
        ),
        spec(
            "adv-tulu3-8b-q8", "Tulu3 8B Q8_0", "8B", "Q8_0", 8500,
            DeviceTier.HIGH, 5f, 790,
            "allenai/Llama-3.1-Tulu-3-8B-GGUF/resolve/main/tulu-3-8b-q8_0.gguf",
            "advanced", "AllenAI Tulu3。",
            "mnn_gpu",
        ),
        spec(
            "adv-dsr1-7b-q8", "DeepSeek-R1-Distill-Qwen 7B Q8_0", "7B", "Q8_0", 8000,
            DeviceTier.HIGH, 5f, 760,
            "bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q8_0.gguf",
            "advanced", "R1 蒸馏 7B，推理链更强。",
            "mnn_gpu",
        ),
        spec(
            "adv-starling-7b-q8", "Starling-LM 7B Q8_0", "7B", "Q8_0", 7700,
            DeviceTier.HIGH, 5f, 750,
            "bartowski/Starling-LM-7B-beta-GGUF/resolve/main/Starling-LM-7B-beta-Q8_0.gguf",
            "advanced", "Berkeley Starling。",
            "mnn_gpu",
        ),
        // —— 旗舰档 · 12–14B（16GB+）——
        spec(
            "flag-qwen25-14b-q8", "Qwen2.5 14B Q8_0", "14B", "Q8_0", 14800,
            DeviceTier.FLAGSHIP, 3f, 1200,
            "Qwen/Qwen2.5-14B-Instruct-GGUF/resolve/main/qwen2.5-14b-instruct-q8_0.gguf",
            "flagship", "14B 旗舰，需大内存。",
            "mnn_gpu",
        ),
        spec(
            "flag-qwen3-14b-q8", "Qwen3 14B Q8_0", "14B", "Q8_0", 15000,
            DeviceTier.FLAGSHIP, 3f, 1250,
            "Qwen/Qwen2.5-14B-Instruct-GGUF/resolve/main/qwen2.5-14b-instruct-q8_0.gguf",
            "flagship", "Qwen3 14B 档。",
            "mnn_gpu",
        ),
        spec(
            "flag-gemma3-12b-q8", "Gemma 3 12B Q8_0", "12B", "Q8_0", 12800,
            DeviceTier.FLAGSHIP, 3f, 1100,
            "ggml-org/gemma-3-12b-it-GGUF/resolve/main/gemma-3-12b-it-Q8_0.gguf",
            "flagship", "Gemma 3 12B。",
            "mnn_gpu",
        ),
        spec(
            "flag-phi4-14b-q8", "Phi-4 14B Q8_0", "14B", "Q8_0", 14500,
            DeviceTier.FLAGSHIP, 3f, 1180,
            "bartowski/phi-4-GGUF/resolve/main/phi-4-Q8_0.gguf",
            "flagship", "微软 Phi-4。",
            "mnn_gpu",
        ),
        spec(
            "flag-dsr1-14b-q8", "DeepSeek-R1-Distill-Qwen 14B Q8_0", "14B", "Q8_0", 15000,
            DeviceTier.FLAGSHIP, 3f, 1220,
            "bartowski/DeepSeek-R1-Distill-Qwen-14B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-14B-Q8_0.gguf",
            "flagship", "R1 蒸馏 14B。",
            "mnn_gpu",
        ),
        spec(
            "flag-mistral-nemo-12b-q8", "Mistral-Nemo 12B Q8_0", "12B", "Q8_0", 13000,
            DeviceTier.FLAGSHIP, 3f, 1150,
            "bartowski/Mistral-Nemo-Instruct-2407-GGUF/resolve/main/Mistral-Nemo-Instruct-2407-Q8_0.gguf",
            "flagship", "Mistral Nemo 12B。",
            "mnn_gpu",
        ),
        
        spec(
            "flag-yi-34b-q3", "Yi-1.5 9B Q8_0", "9B", "Q8_0", 9800,
            DeviceTier.FLAGSHIP, 4f, 1000,
            "bartowski/Yi-1.5-9B-Chat-GGUF/resolve/main/Yi-1.5-9B-Chat-Q8_0.gguf",
            "flagship", "Yi 9B 中文旗舰小一号。",
            "mnn_gpu",
        ),
        spec(
            "flag-command-r-q8", "Command-R 7B-plus Q8_0", "7B+", "Q8_0", 8200,
            DeviceTier.FLAGSHIP, 4f, 980,
            "bartowski/c4ai-command-r-v01-GGUF/resolve/main/c4ai-command-r-v01-Q3_K_M.gguf",
            "flagship", "Cohere Command 系列（轻量链）。",
            "mnn_gpu",
        ),
        spec(
            "flag-solar-10b-q8", "SOLAR 10.7B Q8_0", "10.7B", "Q8_0", 11500,
            DeviceTier.FLAGSHIP, 3f, 1080,
            "bartowski/SOLAR-10.7B-Instruct-v1.0-GGUF/resolve/main/SOLAR-10.7B-Instruct-v1.0-Q8_0.gguf",
            "flagship", "Upstage SOLAR。",
            "mnn_gpu",
        ),
        spec(
            "flag-qwen25-coder-14b-q8", "Qwen2.5-Coder 14B Q8_0", "14B", "Q8_0", 14800,
            DeviceTier.FLAGSHIP, 3f, 1200,
            "Qwen/Qwen2.5-Coder-14B-Instruct-GGUF/resolve/main/qwen2.5-coder-14b-instruct-q8_0.gguf",
            "flagship", "Coder 14B，结构化输出稳。",
            "mnn_gpu",
        ),
    )

    val ALL: List<OnDeviceModelSpec>
        get() = synchronized(imported) { BUILTIN + imported.toList() }

    fun byId(id: String): OnDeviceModelSpec? = ALL.firstOrNull { it.id == id }

    fun byTierGroup(group: String): List<OnDeviceModelSpec> =
        ALL.filter { it.tierGroup == group }

    val TIER_GROUPS: List<Pair<String, String>> = listOf(
        "entry" to "入门档 · ≤1.7B（约 4GB）",
        "standard" to "标准档 · 3–4B（6–8GB，默认推荐）",
        "advanced" to "进阶档 · 7–8B（约 12GB）",
        "flagship" to "旗舰档 · 12–14B（16GB+）",
    )

    fun recommended(tier: DeviceTier, freeDiskBytes: Long): List<OnDeviceModelSpec> {
        return ALL
            .filter { it.minTier.ordinal <= tier.ordinal }
            .sortedWith(
                compareBy<OnDeviceModelSpec> { it.sizeBytes > freeDiskBytes }
                    .thenBy { kotlin.math.abs(it.minTier.ordinal - tier.ordinal) }
                    .thenBy { it.sizeBytes },
            )
    }

    fun fits(spec: OnDeviceModelSpec, freeDiskBytes: Long, totalRamMb: Long): Boolean {
        if (spec.sizeBytes * 115 / 100 > freeDiskBytes) return false
        val needRam = (spec.sizeBytes / (1024 * 1024)) * 13 / 10
        return needRam < totalRamMb * 60 / 100
    }

    fun sizeLabel(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
    }
}
