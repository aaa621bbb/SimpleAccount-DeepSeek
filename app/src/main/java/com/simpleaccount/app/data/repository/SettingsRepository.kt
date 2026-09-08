package com.simpleaccount.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.entity.Setting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 相关设置 + 外观设置。
 * 普通值（开关/BaseUrl/模型名/主题模式）存 Room Setting 表；
 * API Key 存 EncryptedSharedPreferences（Keystore + AES-GCM）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingDao: SettingDao,
) {
    companion object {
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_KEY = "ai_api_key"
        const val KEY_AI_PROVIDER_ID = "ai_provider_id"

        /** 识图 API Key（独立于主 Key，存加密 prefs） */
        const val KEY_VISION_KEY = "vision_api_key"

        /** 外观模式：system 跟随系统 / light 浅色 / dark 深色 */
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** 高级配色 id（ColorPalettes） */
        const val KEY_COLOR_PALETTE = "color_palette"

        /** 统计页图表顺序（逗号分隔模块 id） */
        const val KEY_STATS_ORDER = "stats_order"
        /** 统计页隐藏的图表（逗号分隔） */
        const val KEY_STATS_HIDDEN = "stats_hidden"
        /** 饼图百分比预览条数：0=点击后直接看全部；3=先占 3 条位置 */
        const val KEY_PIE_LEGEND_COUNT = "pie_legend_count"

        /** 每月预算（单位：分，0 = 未设置） */
        const val KEY_MONTHLY_BUDGET = "monthly_budget_fen"

        /** 无感记账总开关 */
        const val KEY_AUTO_RECORD = "auto_record_enabled"

        /** 数据冲突优先级：import=以导入账单为准 / manual=以手动·截图·AI记录为准 */
        const val KEY_IMPORT_PRIORITY = "import_priority"
        const val PRIORITY_IMPORT = "import"
        const val PRIORITY_MANUAL = "manual"

        /** 首页最近记录条数 */
        const val KEY_RECENT_COUNT = "recent_count"

        /** 截图记账用的视觉模型（需支持 image_url 输入，如 glm-4v-flash / qwen-vl 系列） */
        const val KEY_VISION_MODEL = "vision_model"
        const val DEFAULT_VISION_MODEL = "glm-4v-flash"

        /** 识图专用接口地址（与主接口独立，默认智谱） */
        const val KEY_VISION_BASE_URL = "vision_base_url"
        const val DEFAULT_VISION_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"

        /** 主模型是多模态时优先直接用它识图（失败自动回退独立识图配置） */
        const val KEY_VISION_USE_MAIN = "vision_use_main_model"

        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"

        const val KEY_CURRENT_LEDGER = "current_ledger_id"

        /** 顶级 UI 视觉方案：default / glass(液态玻璃) / depth(3D景深) */
        const val KEY_UI_SKIN = "ui_skin"
        const val SKIN_DEFAULT = "default"
        const val SKIN_GLASS = "glass"
        const val SKIN_DEPTH = "depth"

        /** 交易条目标题字段：merchant 商家名 / product 商品名（另一字段降为副标题）。 */
        const val KEY_TITLE_FIELD = "title_field"
        const val TITLE_MERCHANT = "merchant"
        const val TITLE_PRODUCT = "product"

        /** 首页版式：simple 简约风 / dense 信息密集风。 */
        const val KEY_HOME_LAYOUT = "home_layout"
        const val HOME_SIMPLE = "simple"
        const val HOME_DENSE = "dense"

/** 模型后端：cloud 云端大模型 / ondevice 端侧小模型。 */
        const val KEY_MODEL_BACKEND = "model_backend"
        const val BACKEND_CLOUD = "cloud"
        const val BACKEND_ONDEVICE = "ondevice"

        /**
         * 记账识别引擎模式（互斥/级联，由用户明确选择）：
         * - api：仅云端 API Agent
         * - ondevice：仅端侧模型
         * - rules：仅本地规则（LocalAccountant / UtteranceParser）
         * - api_rules：规则优先，吃不准再 API
         * - ondevice_rules：规则优先，吃不准再端侧
         */
        const val KEY_ACCOUNTING_ENGINE = "accounting_engine"
        const val ENGINE_API = "api"
        const val ENGINE_ONDEVICE = "ondevice"
        const val ENGINE_RULES = "rules"
        const val ENGINE_API_RULES = "api_rules"
        const val ENGINE_ONDEVICE_RULES = "ondevice_rules"

        /**
         * 智能体执行授权：
         * - confirm：每次写操作前需用户确认（默认）
         * - auto：授权后自动执行，写工具直接落库
         */
        const val KEY_AGENT_EXEC_AUTH = "agent_exec_auth"
        const val EXEC_AUTH_CONFIRM = "confirm"
        const val EXEC_AUTH_AUTO = "auto"

        /** 最近活跃的 AI 会话 id（退出后自动续接） */
        const val KEY_LAST_CONVERSATION_ID = "last_conversation_id"

        /** 端侧模型：当前启用的本地模型 id（目录名） */
        const val KEY_ONDEVICE_MODEL_ID = "ondevice_model_id"

        /**
         * 收支口径：退款/投资分红/投资支出是否计入流水统计与预算。
         * 默认均计入（true）；用户可逐项关闭。
         */
        const val KEY_INCLUDE_REFUND = "include_refund_in_stats"
        const val KEY_INCLUDE_INVEST_DIVIDEND = "include_invest_dividend_in_stats"
        const val KEY_INCLUDE_INVEST_EXPENSE = "include_invest_expense_in_stats"

        /** 统计页是否展开「高级分析」（频次/结构演进/帕累托等默认折叠） */
        const val KEY_STATS_ADVANCED_OPEN = "stats_advanced_open"

        /** 日期 / 时间选择器样式 */
        const val KEY_DATE_PICKER = "date_picker_style"
        const val KEY_TIME_PICKER = "time_picker_style"

        /** 思考深度：off / low / medium / high */
        const val KEY_THINKING_LEVEL = "thinking_level"
        const val THINKING_OFF = "off"
        const val THINKING_LOW = "low"
        const val THINKING_MEDIUM = "medium"
        const val THINKING_HIGH = "high"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_ai_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun setAiEnabled(enabled: Boolean) = setSetting(KEY_AI_ENABLED, enabled.toString())
    fun isAiEnabled(): Boolean = readSetting(KEY_AI_ENABLED) == "true"

    suspend fun setBaseUrl(url: String) = setSetting(KEY_AI_BASE_URL, url)
    fun baseUrl(): String = readSetting(KEY_AI_BASE_URL) ?: DEFAULT_BASE_URL

    suspend fun setModel(model: String) = setSetting(KEY_AI_MODEL, model)
    fun model(): String = readSetting(KEY_AI_MODEL) ?: DEFAULT_MODEL

    suspend fun setProviderId(id: String) = setSetting(KEY_AI_PROVIDER_ID, id)
    fun providerId(): String = readSetting(KEY_AI_PROVIDER_ID).orEmpty()

    fun apiKey(): String = prefs.getString(KEY_AI_KEY, "") ?: ""
    fun setApiKey(key: String) {
        prefs.edit { putString(KEY_AI_KEY, key) }
    }

    // ---------------- 外观（主题模式） ----------------

    /** 主题模式的响应式流：切换后 MainActivity 收集并即时重建主题 */
    private val _themeMode = MutableStateFlow(themeMode())
    val themeModeFlow: StateFlow<String> = _themeMode.asStateFlow()

    fun themeMode(): String {
        val v = readSetting(KEY_THEME_MODE)
        return if (v == THEME_LIGHT || v == THEME_DARK || v == THEME_SYSTEM) v else THEME_SYSTEM
    }

    suspend fun setThemeMode(mode: String) {
        setSetting(KEY_THEME_MODE, mode)
        _themeMode.value = mode
    }

    // ---------------- 高级配色 ----------------

    private val _colorPalette = MutableStateFlow(colorPalette())
    val colorPaletteFlow: StateFlow<String> = _colorPalette.asStateFlow()

    fun colorPalette(): String = readSetting(KEY_COLOR_PALETTE) ?: "pine"

    suspend fun setColorPalette(id: String) {
        setSetting(KEY_COLOR_PALETTE, id)
        _colorPalette.value = id
    }

    // ---------------- 统计页图表布局 ----------------

    private val _statsOrder = MutableStateFlow(statsOrder())
    val statsOrderFlow: StateFlow<String> = _statsOrder.asStateFlow()

    fun statsOrder(): String = readSetting(KEY_STATS_ORDER).orEmpty()

    suspend fun setStatsOrder(order: String) {
        setSetting(KEY_STATS_ORDER, order)
        _statsOrder.value = order
    }

    private val _statsHidden = MutableStateFlow(statsHidden())
    val statsHiddenFlow: StateFlow<String> = _statsHidden.asStateFlow()

    fun statsHidden(): String = readSetting(KEY_STATS_HIDDEN).orEmpty()

    suspend fun setStatsHidden(hidden: String) {
        setSetting(KEY_STATS_HIDDEN, hidden)
        _statsHidden.value = hidden
    }

    private val _pieLegendCount = MutableStateFlow(pieLegendCount())
    val pieLegendCountFlow: StateFlow<Int> = _pieLegendCount.asStateFlow()

    fun pieLegendCount(): Int = readSetting(KEY_PIE_LEGEND_COUNT)?.toIntOrNull()?.coerceIn(0, 12) ?: 3

    suspend fun setPieLegendCount(n: Int) {
        val v = n.coerceIn(0, 12)
        setSetting(KEY_PIE_LEGEND_COUNT, v.toString())
        _pieLegendCount.value = v
    }

    // ---------------- 数据冲突优先级 ----------------

    private val _importPriority = MutableStateFlow(importPriority())
    val importPriorityFlow: StateFlow<String> = _importPriority.asStateFlow()

    fun importPriority(): String {
        val v = readSetting(KEY_IMPORT_PRIORITY)
        return if (v == PRIORITY_MANUAL) PRIORITY_MANUAL else PRIORITY_IMPORT
    }

    suspend fun setImportPriority(p: String) {
        setSetting(KEY_IMPORT_PRIORITY, p)
        _importPriority.value = p
    }

    // ---------------- 首页最近记录条数 ----------------

    private val _recentCount = MutableStateFlow(recentCount())
    val recentCountFlow: StateFlow<Int> = _recentCount.asStateFlow()

    fun recentCount(): Int = readSetting(KEY_RECENT_COUNT)?.toIntOrNull()?.coerceIn(5, 100) ?: 30

    suspend fun setRecentCount(n: Int) {
        setSetting(KEY_RECENT_COUNT, n.toString())
        _recentCount.value = n.coerceIn(5, 100)
    }

    // ---------------- 条目标题字段（商家名/商品名互换，全局生效） ----------------

    private val _titleField = MutableStateFlow(titleField())
    val titleFieldFlow: StateFlow<String> = _titleField.asStateFlow()

    fun titleField(): String {
        val v = readSetting(KEY_TITLE_FIELD)
        return if (v == TITLE_PRODUCT) TITLE_PRODUCT else TITLE_MERCHANT
    }

    suspend fun setTitleField(v: String) {
        val norm = if (v == TITLE_PRODUCT) TITLE_PRODUCT else TITLE_MERCHANT
        setSetting(KEY_TITLE_FIELD, norm)
        _titleField.value = norm
    }

    // ---------------- 首页版式（简约风/信息密集风） ----------------

    private val _homeLayout = MutableStateFlow(homeLayout())
    val homeLayoutFlow: StateFlow<String> = _homeLayout.asStateFlow()

    fun homeLayout(): String {
        val v = readSetting(KEY_HOME_LAYOUT)
        return if (v == HOME_DENSE) HOME_DENSE else HOME_SIMPLE
    }

    suspend fun setHomeLayout(v: String) {
        val norm = if (v == HOME_DENSE) HOME_DENSE else HOME_SIMPLE
        setSetting(KEY_HOME_LAYOUT, norm)
        _homeLayout.value = norm
    }

    // ---------------- 智能体模型后端（云端/端侧） ----------------

    private val _modelBackend = MutableStateFlow(modelBackend())
    val modelBackendFlow: StateFlow<String> = _modelBackend.asStateFlow()

    fun modelBackend(): String {
        val v = readSetting(KEY_MODEL_BACKEND)
        return if (v == BACKEND_ONDEVICE) BACKEND_ONDEVICE else BACKEND_CLOUD
    }

    suspend fun setModelBackend(v: String) {
        val norm = if (v == BACKEND_ONDEVICE) BACKEND_ONDEVICE else BACKEND_CLOUD
        setSetting(KEY_MODEL_BACKEND, norm)
        _modelBackend.value = norm
    }

    // ---------------- 记账识别引擎模式 ----------------

    private val _accountingEngine = MutableStateFlow(accountingEngine())
    val accountingEngineFlow: StateFlow<String> = _accountingEngine.asStateFlow()

    fun accountingEngine(): String {
        val v = readSetting(KEY_ACCOUNTING_ENGINE)
        return when (v) {
            ENGINE_API, ENGINE_ONDEVICE, ENGINE_RULES, ENGINE_API_RULES, ENGINE_ONDEVICE_RULES -> v
            else -> {
                // 兼容旧 model_backend：端侧 → ondevice_rules；云端 → api_rules
                if (modelBackend() == BACKEND_ONDEVICE) ENGINE_ONDEVICE_RULES else ENGINE_API_RULES
            }
        }
    }

    suspend fun setAccountingEngine(v: String) {
        val norm = when (v) {
            ENGINE_API, ENGINE_ONDEVICE, ENGINE_RULES, ENGINE_API_RULES, ENGINE_ONDEVICE_RULES -> v
            else -> ENGINE_API_RULES
        }
        setSetting(KEY_ACCOUNTING_ENGINE, norm)
        _accountingEngine.value = norm
        // 同步 model_backend，供既有 AgentLoop / OnDevice 路径读取
        val backend = when (norm) {
            ENGINE_ONDEVICE, ENGINE_ONDEVICE_RULES -> BACKEND_ONDEVICE
            else -> BACKEND_CLOUD
        }
        setModelBackend(backend)
    }

    /** 当前模式是否允许规则引擎先吃。 */
    fun engineAllowsRules(): Boolean = when (accountingEngine()) {
        ENGINE_RULES, ENGINE_API_RULES, ENGINE_ONDEVICE_RULES -> true
        else -> false
    }

    /** 当前模式是否允许模型（API 或端侧）兜底。 */
    fun engineAllowsModel(): Boolean = accountingEngine() != ENGINE_RULES

    /** 当前模式是否走端侧（纯端侧或端侧×规则）。 */
    fun engineUsesOnDevice(): Boolean = when (accountingEngine()) {
        ENGINE_ONDEVICE, ENGINE_ONDEVICE_RULES -> true
        else -> false
    }

    // ---------------- 智能体执行授权（每次确认 / 授权后自动执行） ----------------

    private val _agentExecAuth = MutableStateFlow(agentExecAuth())
    val agentExecAuthFlow: StateFlow<String> = _agentExecAuth.asStateFlow()

    fun agentExecAuth(): String {
        val v = readSetting(KEY_AGENT_EXEC_AUTH)
        return if (v == EXEC_AUTH_AUTO) EXEC_AUTH_AUTO else EXEC_AUTH_CONFIRM
    }

    /** 是否授权后自动执行写操作（跳过确认卡片）。默认 false=每次确认。 */
    fun isAgentAutoExecute(): Boolean = agentExecAuth() == EXEC_AUTH_AUTO

    suspend fun setAgentExecAuth(v: String) {
        val norm = if (v == EXEC_AUTH_AUTO) EXEC_AUTH_AUTO else EXEC_AUTH_CONFIRM
        setSetting(KEY_AGENT_EXEC_AUTH, norm)
        _agentExecAuth.value = norm
    }

    // ---------------- 会话续接：记住最近活跃会话 ----------------

    fun lastConversationId(): String = readSetting(KEY_LAST_CONVERSATION_ID).orEmpty()

    suspend fun setLastConversationId(id: String) {
        setSetting(KEY_LAST_CONVERSATION_ID, id)
    }

    // ---------------- 端侧模型选择 ----------------

    fun onDeviceModelId(): String = readSetting(KEY_ONDEVICE_MODEL_ID).orEmpty()

    suspend fun setOnDeviceModelId(id: String) {
        setSetting(KEY_ONDEVICE_MODEL_ID, id)
    }

    // ---------------- 收支口径（退款 / 投资是否计入流水） ----------------

    /** 退款是否计入统计与预算（默认 true）。 */
    fun includeRefund(): Boolean = readSetting(KEY_INCLUDE_REFUND) != "false"
    suspend fun setIncludeRefund(v: Boolean) {
        setSetting(KEY_INCLUDE_REFUND, v.toString())
        bumpLedgerScope()
    }

    /** 投资分红是否计入收入统计（默认 true）。 */
    fun includeInvestDividend(): Boolean = readSetting(KEY_INCLUDE_INVEST_DIVIDEND) != "false"
    suspend fun setIncludeInvestDividend(v: Boolean) {
        setSetting(KEY_INCLUDE_INVEST_DIVIDEND, v.toString())
        bumpLedgerScope()
    }

    /** 投资支出是否计入支出统计（默认 true）。 */
    fun includeInvestExpense(): Boolean = readSetting(KEY_INCLUDE_INVEST_EXPENSE) != "false"
    suspend fun setIncludeInvestExpense(v: Boolean) {
        setSetting(KEY_INCLUDE_INVEST_EXPENSE, v.toString())
        bumpLedgerScope()
    }

    /** 口径变更版本号，供首页/统计 combine 触发重算。 */
    private val _ledgerScopeVersion = MutableStateFlow(0)
    val ledgerScopeVersionFlow: StateFlow<Int> = _ledgerScopeVersion.asStateFlow()
    private fun bumpLedgerScope() {
        _ledgerScopeVersion.value = _ledgerScopeVersion.value + 1
    }

    /**
     * 统计口径过滤器：
     * 1) 草稿（SOURCE_DRAFT）永不计入统计/预算；
     * 2) 按用户开关剔除退款/投资类流水。
     */
    fun filterByLedgerScope(txs: List<com.simpleaccount.app.data.entity.Transaction>): List<com.simpleaccount.app.data.entity.Transaction> {
        val keepRefund = includeRefund()
        val keepDiv = includeInvestDividend()
        val keepInvExp = includeInvestExpense()
        return txs.filter { t ->
            if (t.source == com.simpleaccount.app.data.entity.Transaction.SOURCE_DRAFT) return@filter false
            if (keepRefund && keepDiv && keepInvExp) return@filter true
            val cat = t.category
            val product = t.product
            val note = t.note
            val isRefund = cat == "退款" || product.contains("退款") || note.contains("退款") ||
                t.merchant.contains("退款")
            val isDiv = cat == "投资" && t.type == com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME
            val isInvExp = cat == "投资" && t.type == com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE
            when {
                isRefund && !keepRefund -> false
                isDiv && !keepDiv -> false
                isInvExp && !keepInvExp -> false
                else -> true
            }
        }
    }

    // ---------------- 统计高级分析展开 ----------------

    private val _statsAdvancedOpen = MutableStateFlow(statsAdvancedOpen())
    val statsAdvancedOpenFlow: StateFlow<Boolean> = _statsAdvancedOpen.asStateFlow()

    fun statsAdvancedOpen(): Boolean = readSetting(KEY_STATS_ADVANCED_OPEN) == "true"

    suspend fun setStatsAdvancedOpen(open: Boolean) {
        setSetting(KEY_STATS_ADVANCED_OPEN, open.toString())
        _statsAdvancedOpen.value = open
    }

    // ---------------- 每月预算 ----------------

    /** 每月预算（分）；未设置为 0 */
    fun monthlyBudget(): Long = readSetting(KEY_MONTHLY_BUDGET)?.toLongOrNull() ?: 0L

    suspend fun setMonthlyBudget(fen: Long) {
        setSetting(KEY_MONTHLY_BUDGET, fen.toString())
    }

    // ---------------- 无感记账（通知监听自动记账） ----------------

    fun isAutoRecordEnabled(): Boolean = readSetting(KEY_AUTO_RECORD) == "true"

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        setSetting(KEY_AUTO_RECORD, enabled.toString())
    }

    // ---------------- 截图记账（视觉模型，独立接口+独立Key） ----------------

    /** 识图接口地址（默认智谱） */
    fun visionBaseUrl(): String = readSetting(KEY_VISION_BASE_URL)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_VISION_BASE_URL

    suspend fun setVisionBaseUrl(url: String) {
        setSetting(KEY_VISION_BASE_URL, url.trim())
    }

    /** 识图模型 */
    fun visionModel(): String = readSetting(KEY_VISION_MODEL)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_VISION_MODEL

    suspend fun setVisionModel(model: String) {
        setSetting(KEY_VISION_MODEL, model.trim())
    }

    /** 识图 API Key（与主 Key 独立，存加密 prefs） */
    fun visionApiKey(): String = prefs.getString(KEY_VISION_KEY, "") ?: ""

    fun setVisionApiKey(key: String) {
        prefs.edit { putString(KEY_VISION_KEY, key) }
    }

    /** 优先用主模型识图（主模型是多模态时开；失败自动回退独立识图配置）。默认开 */
    fun useMainModelForVision(): Boolean = readSetting(KEY_VISION_USE_MAIN) != "false"

    suspend fun setUseMainModelForVision(use: Boolean) {
        setSetting(KEY_VISION_USE_MAIN, use.toString())
    }

    fun currentLedgerId(): Long = readSetting(KEY_CURRENT_LEDGER)?.toLongOrNull() ?: 1L

    fun setCurrentLedgerId(id: Long) {
        runBlocking { setSetting(KEY_CURRENT_LEDGER, id.toString()) }
    }

    private val _datePicker = MutableStateFlow(datePickerStyle())
    val datePickerFlow: StateFlow<String> = _datePicker.asStateFlow()
    fun datePickerStyle(): String = readSetting(KEY_DATE_PICKER) ?: "date_wheel"
    suspend fun setDatePickerStyle(id: String) {
        setSetting(KEY_DATE_PICKER, id)
        _datePicker.value = id
    }

    private val _timePicker = MutableStateFlow(timePickerStyle())
    val timePickerFlow: StateFlow<String> = _timePicker.asStateFlow()
    fun timePickerStyle(): String = readSetting(KEY_TIME_PICKER) ?: "time_dial"
    suspend fun setTimePickerStyle(id: String) {
        setSetting(KEY_TIME_PICKER, id)
        _timePicker.value = id
    }

    fun thinkingLevel(): String {
        val v = readSetting(KEY_THINKING_LEVEL)
        return if (v == THINKING_LOW || v == THINKING_MEDIUM || v == THINKING_HIGH || v == THINKING_OFF) v
        else THINKING_OFF
    }

    suspend fun setThinkingLevel(level: String) = setSetting(KEY_THINKING_LEVEL, level)

    // ---------------- 顶级 UI 视觉方案 ----------------

    private val _uiSkin = MutableStateFlow(uiSkin())
    val uiSkinFlow: StateFlow<String> = _uiSkin.asStateFlow()

    fun uiSkin(): String {
        val v = readSetting(KEY_UI_SKIN)
        return when (v) {
            SKIN_GLASS -> SKIN_GLASS
            SKIN_DEPTH -> SKIN_DEPTH
            else -> SKIN_DEFAULT
        }
    }

    suspend fun setUiSkin(skin: String) {
        val norm = when (skin) {
            SKIN_GLASS, SKIN_DEPTH -> skin
            else -> SKIN_DEFAULT
        }
        setSetting(KEY_UI_SKIN, norm)
        _uiSkin.value = norm
    }

    private suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.insert(Setting(key, value))
    }

    private fun readSetting(key: String): String? = runBlocking { settingDao.get(key)?.value }

    /** 清空所有 AI 设置（含加密 key） */
    suspend fun clearAll() {
        settingDao.deleteByKey(KEY_AI_ENABLED)
        settingDao.deleteByKey(KEY_AI_BASE_URL)
        settingDao.deleteByKey(KEY_AI_MODEL)
        withContext(Dispatchers.IO) {
            prefs.edit { clear() }
        }
    }
}
