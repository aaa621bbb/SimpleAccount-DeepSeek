package com.simpleaccount.app.data.ondevice

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备画像与分级：启动时探测内存 / SoC / 算力档位，
 * 为端侧模型推荐可流畅运行的量化规格。
 */
enum class DeviceTier {
    /** 入门：约 4GB RAM → ≤1.7B */
    ENTRY,
    /** 标准：6–8GB → 3–4B（默认推荐） */
    MID,
    /** 进阶：约 12GB → 7–8B */
    HIGH,
    /** 旗舰：16GB+ → 12–14B */
    FLAGSHIP,
}

data class DeviceProfile(
    val totalRamMb: Long,
    val availRamMb: Long,
    val soc: String,
    val abi: String,
    val androidSdk: Int,
    val tier: DeviceTier,
    /** 是否可能有 GPU 加速（OpenCL/Vulkan 启发式） */
    val gpuLikely: Boolean,
    val summary: String,
)

@Singleton
class DeviceProfiler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun profile(): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val totalMb = mem.totalMem / (1024 * 1024)
        val availMb = mem.availMem / (1024 * 1024)
        val socModel = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MODEL }.getOrNull()?.takeIf { it.isNotBlank() }
        } else null
        val soc = listOfNotNull(
            Build.HARDWARE?.takeIf { it.isNotBlank() },
            Build.BOARD?.takeIf { it.isNotBlank() && it != Build.HARDWARE },
            socModel,
        ).distinct().joinToString(" / ").ifBlank { "unknown" }
        @Suppress("DEPRECATION")
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
        val gpuLikely = Build.VERSION.SDK_INT >= 28 &&
            (abi.contains("arm64") || abi.contains("aarch64"))
        val tier = when {
            totalMb >= 15360 -> DeviceTier.FLAGSHIP
            totalMb >= 10240 -> DeviceTier.HIGH
            totalMb >= 5632 -> DeviceTier.MID
            else -> DeviceTier.ENTRY
        }
        val tierLabel = when (tier) {
            DeviceTier.ENTRY -> "入门档（≤1.7B）"
            DeviceTier.MID -> "标准档（3–4B）"
            DeviceTier.HIGH -> "进阶档（7–8B）"
            DeviceTier.FLAGSHIP -> "旗舰档（12–14B）"
        }
        val accel = if (gpuLikely) "GPU 加速可用（Vulkan/OpenCL 路由）" else "建议 CPU 推理"
        val summary = "内存 ${totalMb}MB（可用 ${availMb}MB）· $soc · $abi · $tierLabel · $accel"
        return DeviceProfile(
            totalRamMb = totalMb,
            availRamMb = availMb,
            soc = soc,
            abi = abi,
            androidSdk = Build.VERSION.SDK_INT,
            tier = tier,
            gpuLikely = gpuLikely,
            summary = summary,
        )
    }
}
