package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.ondevice.BenchmarkResult
import com.simpleaccount.app.data.ondevice.DeviceProfile
import com.simpleaccount.app.data.ondevice.ModelLocalState
import com.simpleaccount.app.data.ondevice.OnDeviceInferenceEngine
import com.simpleaccount.app.data.ondevice.OnDeviceModelCatalog
import com.simpleaccount.app.data.ondevice.OnDeviceModelManager
import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnDeviceUiState(
    val profile: DeviceProfile? = null,
    val freeDiskLabel: String = "—",
    val models: List<ModelLocalState> = emptyList(),
    val recommendedIds: Set<String> = emptySet(),
    val fitsIds: Set<String> = emptySet(),
    val activeId: String? = null,
    val benchmarking: Boolean = false,
    val benchmark: BenchmarkResult? = null,
)

@HiltViewModel
class OnDeviceModelViewModel @Inject constructor(
    private val manager: OnDeviceModelManager,
    private val engine: OnDeviceInferenceEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnDeviceUiState())
    val state: StateFlow<OnDeviceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(manager.states, manager.activeId) { map, active -> map to active }
                .collect { (map, active) ->
                    val profile = manager.deviceProfile()
                    val free = manager.freeDiskBytes()
                    val recommended = OnDeviceModelCatalog.recommended(profile.tier, free).map { it.id }.toSet()
                    val fits = OnDeviceModelCatalog.ALL
                        .filter { OnDeviceModelCatalog.fits(it, free, profile.totalRamMb) }
                        .map { it.id }
                        .toSet()
                    // 保持目录顺序
                    val ordered = OnDeviceModelCatalog.ALL.map { spec ->
                        map[spec.id] ?: ModelLocalState(spec)
                    }
                    _state.value = _state.value.copy(
                        profile = profile,
                        freeDiskLabel = manager.fmtMb(free),
                        models = ordered,
                        recommendedIds = recommended,
                        fitsIds = fits,
                        activeId = active,
                    )
                }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            manager.refresh()
            // 同步设置里记住的模型 id
            val saved = settingsRepository.onDeviceModelId()
            if (saved.isNotBlank()) {
                val st = manager.states.value[saved]
                if (st?.status == ModelLocalState.Status.READY) {
                    manager.setActive(saved)
                }
            }
        }
    }

    fun download(id: String) {
        viewModelScope.launch {
            val ok = manager.download(id)
            if (ok) {
                settingsRepository.setOnDeviceModelId(id)
                // 下载成功后可自动切到端侧（用户仍可在 AI 设置改回云端）
            }
        }
    }

    fun cancel(id: String) = manager.cancelDownload(id)

    fun delete(id: String) {
        viewModelScope.launch {
            if (manager.activeId.value == id) engine.unload()
            manager.deleteModel(id)
            if (settingsRepository.onDeviceModelId() == id) {
                settingsRepository.setOnDeviceModelId("")
            }
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            manager.setActive(id)
            settingsRepository.setOnDeviceModelId(id)
            engine.unload() // 下次 chat 时按新模型加载
            // 启用端侧后端 + 端侧×规则引擎，方便用户一键开用
            settingsRepository.setAccountingEngine(SettingsRepository.ENGINE_ONDEVICE_RULES)
            settingsRepository.setAiEnabled(true)
        }
    }

    fun runBenchmark() {
        viewModelScope.launch {
            _state.value = _state.value.copy(benchmarking = true, benchmark = null)
            val r = runCatching { engine.benchmark() }.getOrElse {
                BenchmarkResult(
                    backend = "—",
                    firstTokenMs = -1,
                    tokPerSec = 0f,
                    sampleTokens = 0,
                    deviceSummary = manager.deviceProfile().summary,
                    ok = false,
                    note = it.message ?: "基准失败",
                )
            }
            _state.value = _state.value.copy(benchmarking = false, benchmark = r)
        }
    }

    fun fmtSize(bytes: Long): String = manager.fmtMb(bytes)

    /**
     * 从本机文件路径导入模型包并注册（名称/简介/参数量/体积由文件与用户输入决定）。
     * @return 新模型 id；失败返回 null
     */
    fun importLocal(path: String, displayName: String? = null, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val id = manager.importLocalFile(path, displayName = displayName)
            if (id != null) {
                settingsRepository.setOnDeviceModelId(id)
                settingsRepository.setModelBackend(SettingsRepository.BACKEND_ONDEVICE)
                settingsRepository.setAccountingEngine(SettingsRepository.ENGINE_ONDEVICE_RULES)
                onDone(true, id)
            } else {
                onDone(false, "导入失败：文件无效或过小")
            }
        }
    }
}
