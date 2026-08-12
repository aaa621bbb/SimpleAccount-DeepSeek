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

    fun edit(id: Long) = "edit/$id"
}
