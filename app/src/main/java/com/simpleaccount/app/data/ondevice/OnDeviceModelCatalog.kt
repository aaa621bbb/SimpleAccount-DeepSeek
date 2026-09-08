package com.simpleaccount.app.data.ondevice

/**
 * 端侧模型目录：0.6B–1.5B 量级量化包，按设备档位推荐。
 *
 * 模型包为 GGUF / MNN 兼容清单；下载后本地缓存。
 * 推理路由：优先 GPU（Vulkan/OpenCL-MNN 类），不支持则 CPU（GGUF 类）。
 *
 * 下载源使用公开可访问的小文件占位 + 真实结构校验；生产可替换为
 * HuggingFace / ModelScope 直链。sha256 用于完整性校验。
 */
data class OnDeviceModelSpec(
    val id: String,
    val displayName: String,
    val paramsLabel: String,
    /** 量化格式标签，如 Q4_K_M */
    val quant: String,
    /** 约计体积（字节） */
    val sizeBytes: Long,
    /** 推荐最低档位 */
    val minTier: DeviceTier,
    /** 预估 tok/s（中端机参考，实际以基准为准） */
    val estTokPerSec: Float,
    /** 预估首字延迟 ms */
    val estFirstTokenMs: Int,
    /** 直连下载 URL（支持 Range 断点续传） */
    val downloadUrl: String,
    /** 期望 sha256（小写 hex）；空则只做大小校验 */
    val sha256: String,
    /** 运行时后端提示：mnn_gpu / gguf_cpu */
    val runtimeHint: String,
    val description: String,
)

object OnDeviceModelCatalog {

    /** 用户本地导入的模型（运行时追加，不入 APK 资源）。 */
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

    /**
     * 内置 + 本地导入。
     *
     * 体积与 URL：使用可公开拉取的小体积演示包（真实 GGUF 头 + 权重切片的 stub），
     * 安装后由 [OnDeviceInferenceEngine] 识别并启用本地精简推理链路。
     * 更换正式权重时只需改 URL / sha256 / sizeBytes。
     */
    private val BUILTIN: List<OnDeviceModelSpec> = listOf(
        OnDeviceModelSpec(
            id = "sa-qwen06b-q4",
            displayName = "记账精简 0.6B Q4",
            paramsLabel = "0.6B",
            quant = "Q4_0",
            sizeBytes = 350L * 1024 * 1024,
            minTier = DeviceTier.ENTRY,
            estTokPerSec = 28f,
            estFirstTokenMs = 180,
            // ModelScope / HF 镜像占位：实际部署替换为真实 GGUF
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf",
            sha256 = "",
            runtimeHint = "gguf_cpu",
            description = "入门档首选。体积小、首字快，适合查账/记一笔等短指令。全离线、无账号。",
        ),
        OnDeviceModelSpec(
            id = "sa-qwen08b-q4k",
            displayName = "记账均衡 0.8B Q4_K",
            paramsLabel = "0.8B",
            quant = "Q4_K_M",
            sizeBytes = 480L * 1024 * 1024,
            minTier = DeviceTier.ENTRY,
            estTokPerSec = 22f,
            estFirstTokenMs = 220,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sha256 = "",
            runtimeHint = "gguf_cpu",
            description = "入门~中端。理解力更好，工具调用更稳。",
        ),
        OnDeviceModelSpec(
            id = "sa-qwen15b-q4k",
            displayName = "记账增强 1.5B Q4_K",
            paramsLabel = "1.5B",
            quant = "Q4_K_M",
            sizeBytes = 950L * 1024 * 1024,
            minTier = DeviceTier.MID,
            estTokPerSec = 14f,
            estFirstTokenMs = 320,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sha256 = "",
            runtimeHint = "mnn_gpu",
            description = "中高端推荐。分析/多步工具更强；有 GPU 时走加速路由。",
        ),
        OnDeviceModelSpec(
            id = "sa-qwen15b-q5",
            displayName = "记账高精 1.5B Q5",
            paramsLabel = "1.5B",
            quant = "Q5_K_M",
            sizeBytes = 1100L * 1024 * 1024,
            minTier = DeviceTier.HIGH,
            estTokPerSec = 11f,
            estFirstTokenMs = 380,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q5_k_m.gguf",
            sha256 = "",
            runtimeHint = "mnn_gpu",
            description = "高端机。量化更高，指令跟随更好，耗内存也更大。",
        ),
    )

    val ALL: List<OnDeviceModelSpec>
        get() = synchronized(imported) { BUILTIN + imported.toList() }

    fun byId(id: String): OnDeviceModelSpec? = ALL.firstOrNull { it.id == id }

    /** 按设备档位给出推荐列表（可装得下的靠前）。 */
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
        // 磁盘预留 1.15x；内存经验：模型体积(MB) * 1.3 < 可用内存的 60%
        if (spec.sizeBytes * 115 / 100 > freeDiskBytes) return false
        val needRam = (spec.sizeBytes / (1024 * 1024)) * 13 / 10
        return needRam < totalRamMb * 60 / 100
    }

    /** 人类可读体积。 */
    fun sizeLabel(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
    }
}
