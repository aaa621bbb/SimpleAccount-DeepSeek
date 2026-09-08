package com.simpleaccount.app.ui.navigation

/** 路由定义 */
object Routes {
    const val HOME = "home"
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val AI = "ai"
    const val SETTINGS = "settings"

    const val ADD = "add"
    const val EDIT = "edit/{id}"
    const val IMPORT = "import"
    const val CATEGORY_MANAGE = "category_manage"
    const val MERCHANT_MANAGE = "merchant_manage"
    const val DATA_MANAGE = "data_manage"
    const val LOGS = "logs"
    const val ABOUT = "about"
const val AI_SETTINGS = "ai_settings"
    const val ONDEVICE_MODELS = "ondevice_models"
    const val AUTO_RECORD = "auto_record"
    /** 收支口径：退款/投资是否计入统计与预算 */
    const val LEDGER_SCOPE = "ledger_scope"
    const val LEDGER_MANAGE = "ledger_manage"
    const val APPEARANCE = "appearance"
    const val STATS_LAYOUT = "stats_layout"
    const val PICKER_STYLE = "picker_style"
    const val MEMORY = "memory"
    const val AI_SHOT = "ai_shot"
    /** 月底测算详情：口径 / 纳入豁免 / 日摊与一次性 */
    const val PROJECTION_DETAIL = "projection_detail"

    fun edit(id: Long) = "edit/$id"
}
