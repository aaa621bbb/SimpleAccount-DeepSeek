package com.simpleaccount.app.data.ondevice

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 单个模型的本地下载/就绪状态。 */
data class ModelLocalState(
    val spec: OnDeviceModelSpec,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = spec.sizeBytes,
    val status: Status = Status.NOT_DOWNLOADED,
    val error: String? = null,
    val progress: Float = 0f,
    val filePath: String? = null,
) {
    enum class Status {
        NOT_DOWNLOADED,
        DOWNLOADING,
        VERIFYING,
        READY,
        FAILED,
    }
}

/**
 * 端侧模型下载管理：直连模型源、断点续传、指数退避重试、
 * 磁盘空间预检、sha256 完整性校验、本地缓存与切换。
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiler: DeviceProfiler,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val rootDir: File by lazy {
        File(context.filesDir, "ondevice_models").also { it.mkdirs() }
    }

    private val _states = MutableStateFlow<Map<String, ModelLocalState>>(emptyMap())
    val states: StateFlow<Map<String, ModelLocalState>> = _states.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private var cancelFlags = mutableMapOf<String, Boolean>()

    fun modelsDir(): File = rootDir

    fun freeDiskBytes(): Long = try {
        val s = StatFs(rootDir.absolutePath)
        s.availableBlocksLong * s.blockSizeLong
    } catch (_: Exception) {
        0L
    }

    fun refresh() {
        restoreImported()
        val map = LinkedHashMap<String, ModelLocalState>()
        for (spec in OnDeviceModelCatalog.ALL) {
            map[spec.id] = inspect(spec)
        }
        _states.value = map
        // 恢复已选
        val marker = File(rootDir, "active.txt")
        if (marker.exists()) {
            val id = marker.readText().trim()
            if (map[id]?.status == ModelLocalState.Status.READY) _activeId.value = id
        }
        if (_activeId.value == null) {
            map.values.firstOrNull { it.status == ModelLocalState.Status.READY }?.let {
                _activeId.value = it.spec.id
            }
        }
    }

    private fun inspect(spec: OnDeviceModelSpec): ModelLocalState {
        val finalFile = File(rootDir, "${spec.id}.bin")
        val partFile = File(rootDir, "${spec.id}.part")
        return when {
            finalFile.exists() && finalFile.length() > 0 -> {
                ModelLocalState(
                    spec = spec,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length().coerceAtLeast(spec.sizeBytes),
                    status = ModelLocalState.Status.READY,
                    progress = 1f,
                    filePath = finalFile.absolutePath,
                )
            }
            partFile.exists() && partFile.length() > 0 -> {
                val done = partFile.length()
                val total = spec.sizeBytes.coerceAtLeast(done)
                ModelLocalState(
                    spec = spec,
                    downloadedBytes = done,
                    totalBytes = total,
                    status = ModelLocalState.Status.NOT_DOWNLOADED,
                    progress = (done.toFloat() / total).coerceIn(0f, 0.99f),
                    filePath = partFile.absolutePath,
                    error = "未完成（可断点续传）",
                )
            }
            else -> ModelLocalState(spec = spec)
        }
    }

    fun activeModelFile(): File? {
        val id = _activeId.value ?: return null
        val f = File(rootDir, "$id.bin")
        return f.takeIf { it.exists() && it.length() > 0 }
    }

    fun activeSpec(): OnDeviceModelSpec? =
        _activeId.value?.let { OnDeviceModelCatalog.byId(it) }

    suspend fun setActive(id: String) = withContext(Dispatchers.IO) {
        val st = _states.value[id]
        if (st?.status != ModelLocalState.Status.READY) return@withContext
        _activeId.value = id
        File(rootDir, "active.txt").writeText(id)
    }

    suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        cancelFlags[id] = true
        File(rootDir, "$id.bin").delete()
        File(rootDir, "$id.part").delete()
        File(rootDir, "$id.meta.json").delete()
        OnDeviceModelCatalog.unregisterImported(id)
        if (_activeId.value == id) {
            _activeId.value = null
            File(rootDir, "active.txt").delete()
        }
        refresh()
    }

    fun cancelDownload(id: String) {
        cancelFlags[id] = true
    }

    /**
     * 下载模型：磁盘预检 → Range 断点续传 → 指数退避重试 → sha256 校验 → 落盘就绪。
     */
    suspend fun download(id: String, maxRetries: Int = 4): Boolean = withContext(Dispatchers.IO) {
        val spec = OnDeviceModelCatalog.byId(id) ?: return@withContext false
        if (spec.downloadUrl.isBlank()) {
            // 本地导入模型无下载地址：若已就绪直接成功，否则提示改走导入
            val final = File(rootDir, "$id.bin")
            if (final.exists() && final.length() > 0) {
                patch(id) {
                    it.copy(
                        status = ModelLocalState.Status.READY,
                        progress = 1f,
                        downloadedBytes = final.length(),
                        filePath = final.absolutePath,
                        error = null,
                    )
                }
                setActive(id)
                return@withContext true
            }
            patch(id) {
                it.copy(
                    status = ModelLocalState.Status.FAILED,
                    error = "该模型为本地导入，无下载地址。请用「从本机导入」注册。",
                )
            }
            return@withContext false
        }
        cancelFlags[id] = false

        // 磁盘空间预检（1.15x + 50MB 余量）
        val need = spec.sizeBytes * 115 / 100 + 50L * 1024 * 1024
        val free = freeDiskBytes()
        if (free in 1 until need) {
            patch(id) {
                it.copy(
                    status = ModelLocalState.Status.FAILED,
                    error = "磁盘空间不足：需要约 ${fmtMb(need)}，可用 ${fmtMb(free)}",
                )
            }
            return@withContext false
        }

        val part = File(rootDir, "$id.part")
        val final = File(rootDir, "$id.bin")
        if (final.exists() && final.length() > 0) {
            patch(id) {
                it.copy(
                    status = ModelLocalState.Status.READY,
                    progress = 1f,
                    downloadedBytes = final.length(),
                    filePath = final.absolutePath,
                    error = null,
                )
            }
            setActive(id)
            return@withContext true
        }

        var attempt = 0
        var lastError: String? = null
        while (attempt <= maxRetries) {
            if (cancelFlags[id] == true) {
                patch(id) { it.copy(status = ModelLocalState.Status.NOT_DOWNLOADED, error = "已取消") }
                return@withContext false
            }
            attempt++
            patch(id) {
                it.copy(
                    status = ModelLocalState.Status.DOWNLOADING,
                    error = if (attempt > 1) "重试 $attempt/$maxRetries…" else null,
                )
            }
            try {
                val existing = if (part.exists()) part.length() else 0L
                val reqBuilder = Request.Builder().url(spec.downloadUrl).header("User-Agent", "SimpleAccount/2.31")
                if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
                val resp = client.newCall(reqBuilder.build()).execute()
                if (!resp.isSuccessful && resp.code != 206) {
                    resp.close()
                    throw IOException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IOException("空响应")
                val totalFromHeader = resp.header("Content-Range")
                    ?.substringAfter("/")
                    ?.toLongOrNull()
                    ?: (if (resp.code == 206) existing + (body.contentLength().takeIf { it > 0 } ?: 0)
                    else body.contentLength().takeIf { it > 0 } ?: spec.sizeBytes)
                val append = resp.code == 206 && existing > 0
                if (!append && part.exists()) part.delete()

                RandomAccessFile(part, "rw").use { raf ->
                    if (append) raf.seek(existing) else raf.setLength(0)
                    val buf = ByteArray(64 * 1024)
                    var downloaded = if (append) existing else 0L
                    body.byteStream().use { input ->
                        while (true) {
                            if (cancelFlags[id] == true) throw IOException("已取消")
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            downloaded += n
                            val total = totalFromHeader.coerceAtLeast(downloaded)
                            val p = (downloaded.toFloat() / total).coerceIn(0f, 0.99f)
                            patch(id) {
                                it.copy(
                                    status = ModelLocalState.Status.DOWNLOADING,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    progress = p,
                                    error = null,
                                )
                            }
                        }
                    }
                }
                resp.close()

                // 完整性校验
                patch(id) { it.copy(status = ModelLocalState.Status.VERIFYING, progress = 0.99f) }
                if (spec.sha256.isNotBlank()) {
                    val actual = sha256Of(part)
                    if (!actual.equals(spec.sha256, ignoreCase = true)) {
                        part.delete()
                        throw IOException("完整性校验失败（sha256 不匹配）")
                    }
                } else if (part.length() < 1024) {
                    // 无 sha 时至少要求不是空/错误页
                    part.delete()
                    throw IOException("下载文件过小，可能不是有效模型包")
                }

                if (final.exists()) final.delete()
                if (!part.renameTo(final)) {
                    part.copyTo(final, overwrite = true)
                    part.delete()
                }
                patch(id) {
                    it.copy(
                        status = ModelLocalState.Status.READY,
                        progress = 1f,
                        downloadedBytes = final.length(),
                        totalBytes = final.length(),
                        filePath = final.absolutePath,
                        error = null,
                    )
                }
                setActive(id)
                return@withContext true
            } catch (e: Exception) {
                lastError = e.message ?: "下载失败"
                if (cancelFlags[id] == true || lastError == "已取消") {
                    patch(id) { it.copy(status = ModelLocalState.Status.NOT_DOWNLOADED, error = "已取消") }
                    return@withContext false
                }
                // 指数退避：1s, 2s, 4s, 8s
                val backoff = (1L shl (attempt - 1).coerceAtMost(4)) * 1000L
                patch(id) {
                    it.copy(
                        status = ModelLocalState.Status.DOWNLOADING,
                        error = "$lastError，${backoff / 1000}s 后重试…",
                    )
                }
                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        patch(id) {
            it.copy(status = ModelLocalState.Status.FAILED, error = lastError ?: "下载失败")
        }
        false
    }

    private fun patch(id: String, f: (ModelLocalState) -> ModelLocalState) {
        val cur = _states.value.toMutableMap()
        val base = cur[id] ?: OnDeviceModelCatalog.byId(id)?.let { ModelLocalState(it) } ?: return
        cur[id] = f(base)
        _states.value = cur
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun fmtMb(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
    }

    fun deviceProfile() = profiler.profile()

    /**
     * 从本地文件导入模型包并注册到目录。
     * 复制到 ondevice_models/{id}.bin，生成/更新 [OnDeviceModelSpec] 元数据后标记 READY。
     */
    suspend fun importLocalFile(
        sourcePath: String,
        displayName: String? = null,
        paramsLabel: String = "自定义",
        description: String = "用户本地导入的模型包。",
    ): String? = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        if (!src.exists() || !src.isFile || src.length() < 1024) return@withContext null
        val base = displayName?.trim()?.ifBlank { null }
            ?: src.nameWithoutExtension.take(32).ifBlank { "local-model" }
        val id = "local-" + base.replace(Regex("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]"), "_")
            .take(40).ifBlank { "import" } + "-" + (src.length() % 9973)
        val final = File(rootDir, "$id.bin")
        try {
            src.inputStream().use { input ->
                final.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            final.delete()
            return@withContext null
        }
        val spec = OnDeviceModelSpec(
            id = id,
            displayName = base,
            paramsLabel = paramsLabel,
            quant = src.extension.ifBlank { "bin" }.uppercase(),
            sizeBytes = final.length(),
            minTier = DeviceTier.ENTRY,
            estTokPerSec = 10f,
            estFirstTokenMs = 400,
            downloadUrl = "",
            sha256 = "",
            runtimeHint = "gguf_cpu",
            description = description,
        )
        OnDeviceModelCatalog.registerImported(spec)
        // 写入 sidecar 元数据，refresh 后可恢复
        runCatching {
            File(rootDir, "$id.meta.json").writeText(
                """{"id":"$id","displayName":"${spec.displayName}","paramsLabel":"${spec.paramsLabel}","quant":"${spec.quant}","sizeBytes":${spec.sizeBytes},"description":${org.json.JSONObject.quote(spec.description)}}"""
            )
        }
        refresh()
        setActive(id)
        id
    }

    /** 启动时扫描已导入的 sidecar，重新挂到目录。 */
    fun restoreImported() {
        rootDir.listFiles()?.filter { it.name.endsWith(".meta.json") }?.forEach { meta ->
            runCatching {
                val j = org.json.JSONObject(meta.readText())
                val id = j.getString("id")
                val bin = File(rootDir, "$id.bin")
                if (!bin.exists()) return@forEach
                val spec = OnDeviceModelSpec(
                    id = id,
                    displayName = j.optString("displayName", id),
                    paramsLabel = j.optString("paramsLabel", "自定义"),
                    quant = j.optString("quant", "BIN"),
                    sizeBytes = j.optLong("sizeBytes", bin.length()),
                    minTier = DeviceTier.ENTRY,
                    estTokPerSec = 10f,
                    estFirstTokenMs = 400,
                    downloadUrl = "",
                    sha256 = "",
                    runtimeHint = "gguf_cpu",
                    description = j.optString("description", "用户本地导入的模型包。"),
                )
                OnDeviceModelCatalog.registerImported(spec)
            }
        }
    }
}
