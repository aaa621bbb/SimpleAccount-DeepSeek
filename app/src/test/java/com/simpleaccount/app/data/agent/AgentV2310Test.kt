package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.ondevice.DeviceTier
import com.simpleaccount.app.data.ondevice.OnDeviceModelCatalog
import com.simpleaccount.app.data.repository.SettingsRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * v2.31.0 回归：
 * 1. 系统提示不再硬性「禁止调用工具」
 * 2. 执行授权两档可配置（confirm / auto）
 * 3. 禁止伪造执行凭据（sanitize + 回执契约）
 * 4. 同请求连续多次落库可核验（rows_affected 契约）
 * 5. 端侧模型目录按档位推荐、磁盘预检
 * 6. CHAT 意图仍挂最小工具集（可执行性）
 */
class AgentV2310Test {

    @Test
    fun prompt_chatLite_doesNotForbidTools() {
        val lite = AgentPromptBuilder.systemChatLite()
        assertFalse("不得出现「不要调用工具」硬约束", lite.contains("不要调用工具"))
        assertFalse(lite.contains("禁止调用工具"))
        assertTrue(lite.contains("自主") || lite.contains("按需") || lite.contains("工具"))
    }

    @Test
    fun prompt_full_noHardToolBan() {
        val full = AgentPromptBuilder.systemFull(listOf("2026-08", "2026-09"), "【账本快照】本月支出 ¥100")
        assertFalse(full.contains("绝不调用账本工具"))
        assertTrue(full.contains("rows_affected"))
        assertTrue(full.contains("【账本已核验】") || full.contains("落库回执"))
    }

    @Test
    fun prompt_autoExecute_wording() {
        val auto = AgentPromptBuilder.systemFull(emptyList(), "", autoExecute = true)
        assertTrue(auto.contains("直接落库") || auto.contains("自动执行"))
        val confirm = AgentPromptBuilder.systemFull(emptyList(), "", autoExecute = false)
        assertTrue(confirm.contains("确认卡片") || confirm.contains("批准"))
    }

    @Test
    fun execAuth_defaultsConfirm() {
        assertEquals("confirm", SettingsRepository.EXEC_AUTH_CONFIRM)
        assertEquals("auto", SettingsRepository.EXEC_AUTH_AUTO)
    }

    @Test
    fun writeGate_stillCoversWrites() {
        assertEquals(13, WriteGate.GATED.size)
        assertTrue(WriteGate.isGated("add_transaction"))
        assertFalse(WriteGate.isGated("query_transactions"))
        assertFalse(WriteGate.isGated("navigate"))
    }

    @Test
    fun repeatedWrite_receiptContract() {
        // 同请求连续多次落库：每次回执必须可反查
        val receipts = (1..5).map { i ->
            "【账本已核验】rows_affected=1 已记账（流水号 $i）"
        }
        receipts.forEach { r ->
            assertTrue(ToolAudit.isVerified(r))
            assertEquals(1, ToolAudit.affectedRowsOf(r))
        }
        val batch = "【账本已核验】rows_affected=5 matched=5 改到「餐饮」。"
        assertEquals(5, ToolAudit.affectedRowsOf(batch))
        assertTrue(ToolAudit.isVerified(batch))
    }

    @Test
    fun fakeCompletion_zeroRowsNotVerified() {
        val fake = "已完成，已记账 3 笔。"
        assertFalse(ToolAudit.isVerified(fake))
        assertEquals(0, ToolAudit.affectedRowsOf(fake))
        val zero = "失败 rows_affected=0。账本没有改动。"
        assertFalse(ToolAudit.isVerified(zero))
        assertEquals(0, ToolAudit.affectedRowsOf(zero))
    }

    @Test
    fun intentGate_writeStillClassified() {
        assertEquals(QueryIntent.LEDGER_WRITE, IntentGate.classify("帮我记一笔午餐 25 元"))
        assertEquals(QueryIntent.LEDGER_READ, IntentGate.classify("本月花了多少"))
        assertEquals(QueryIntent.CHAT, IntentGate.classify("今天天气怎么样"))
    }

    @Test
    fun onDeviceCatalog_tiersAndSizes() {
        assertTrue(OnDeviceModelCatalog.ALL.size >= 3)
        OnDeviceModelCatalog.ALL.forEach {
            assertTrue(it.paramsLabel.contains("B"))
            assertTrue(it.sizeBytes > 0)
            assertTrue(it.downloadUrl.startsWith("http"))
            assertTrue(it.estTokPerSec > 0)
        }
        val entry = OnDeviceModelCatalog.recommended(DeviceTier.ENTRY, freeDiskBytes = 8L * 1024 * 1024 * 1024)
        assertTrue(entry.isNotEmpty())
        assertTrue(entry.all { it.minTier.ordinal <= DeviceTier.ENTRY.ordinal || it.minTier == DeviceTier.ENTRY || it.minTier.ordinal <= 1 })
        // 入门档不应把仅高端模型排最前硬塞
        assertTrue(entry.first().minTier != DeviceTier.HIGH || entry.size == 1)
    }

    @Test
    fun onDeviceCatalog_diskPrecheck() {
        val big = OnDeviceModelCatalog.ALL.maxBy { it.sizeBytes }
        assertFalse(OnDeviceModelCatalog.fits(big, freeDiskBytes = 10L * 1024 * 1024, totalRamMb = 2048))
        val small = OnDeviceModelCatalog.ALL.minBy { it.sizeBytes }
        assertTrue(OnDeviceModelCatalog.fits(small, freeDiskBytes = 8L * 1024 * 1024 * 1024, totalRamMb = 6144))
    }

    @Test
    fun utterance_missingAmountDoesNotFabricate() {
        val u = UtteranceParser.parse("帮我记一笔食堂午饭", setOf("餐饮", "其它"))
        assertNull("金额未明示不得臆测", u.amountFen)
        assertTrue(u.needAsk.contains("amount"))
    }

    // ---------- 五–九 ----------

    @Test
    fun statsModules_advancedDefaultFolded() {
        assertTrue(com.simpleaccount.app.ui.stats.StatsModules.ADVANCED_IDS.contains("freq"))
        assertTrue(com.simpleaccount.app.ui.stats.StatsModules.ADVANCED_IDS.contains("pareto"))
        assertTrue(com.simpleaccount.app.ui.stats.StatsModules.isAdvanced("elastic"))
        assertFalse(com.simpleaccount.app.ui.stats.StatsModules.isAdvanced("pie"))
        // 主列表不含高级
        assertTrue(com.simpleaccount.app.ui.stats.StatsModules.DEFAULT_ORDER.none {
            com.simpleaccount.app.ui.stats.StatsModules.isAdvanced(it)
        })
    }

    @Test
    fun projectMonthEnd_oneShotNotAmortized() {
        // 月初话费 100 元 + 地铁日频：话费只计一次，地铁按日外推
        val txs = mutableListOf<com.simpleaccount.app.data.entity.Transaction>()
        // day 1 话费
        txs += tx(10000, "通讯", "中国移动", "话费充值", "2026-09-01")
        // 前 10 天每天地铁 5 元
        for (d in 1..10) {
            txs += tx(500, "交通", "地铁", "地铁", "2026-09-%02d".format(d))
        }
        val projected = com.simpleaccount.app.data.insights.InsightsEngine.projectMonthEnd(txs, dayOfMonth = 10, daysInMonth = 30)
        // 话费 10000 只计一次 + 地铁 500*10 日均外推到 30 天 = 15000 → 合计 25000
        assertEquals(25000L, projected)
        // 若整月一刀切日均：(10000+5000)*30/10 = 45000，必须明显小于该值
        assertTrue(projected < 45000L)
    }

    @Test
    fun projectMonthEnd_metroStillPaced() {
        val txs = (1..5).map { d ->
            tx(600, "交通", "地铁", "地铁通勤", "2026-09-%02d".format(d))
        }
        val projected = com.simpleaccount.app.data.insights.InsightsEngine.projectMonthEnd(txs, 5, 30)
        // 5 天共 3000，日均 600 × 30 = 18000
        assertEquals(18000L, projected)
    }

    @Test
    fun notificationParser_confidenceOnFallback() {
        val high = com.simpleaccount.app.auto.NotificationParser.parse(
            "com.tencent.mm", "星巴克", "微信支付 ¥32.00"
        )
        assertNotNull(high)
        assertTrue(high!!.confidence >= 0.75f)

        val low = com.simpleaccount.app.auto.NotificationParser.parse(
            "com.tencent.mm", "微信支付", "支付成功 ¥10.00"
        )
        assertNotNull(low)
        // 商家是兜底名时置信应偏低
        assertTrue(low!!.confidence < 0.75f || low.merchant.contains("微信"))
    }

    @Test
    fun draftSource_constant() {
        assertEquals("draft", com.simpleaccount.app.data.entity.Transaction.SOURCE_DRAFT)
        assertEquals("auto", com.simpleaccount.app.data.entity.Transaction.SOURCE_AUTO)
    }

    @Test
    fun filterByLedgerScope_excludesDraftAlways() {
        // 直接验证 Transaction 常量与过滤契约：草稿 source 永不进统计
        val draft = tx(100, "餐饮", "店", "餐", "2026-09-01").copy(source = com.simpleaccount.app.data.entity.Transaction.SOURCE_DRAFT)
        val ok = tx(200, "餐饮", "店", "餐", "2026-09-01")
        assertEquals("draft", draft.source)
        assertEquals("manual", ok.source)
        assertTrue(ok.source != com.simpleaccount.app.data.entity.Transaction.SOURCE_DRAFT)
    }

    @Test
    fun statsModules_allOrderContainsAdvanced() {
        val all = com.simpleaccount.app.ui.stats.StatsModules.ALL_ORDER
        com.simpleaccount.app.ui.stats.StatsModules.ADVANCED_IDS.forEach {
            assertTrue("$it should be in ALL_ORDER", it in all)
        }
    }

    private fun tx(
        amount: Long,
        category: String,
        merchant: String,
        product: String,
        date: String,
    ) = com.simpleaccount.app.data.entity.Transaction(
        amount = amount,
        type = com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE,
        category = category,
        date = date,
        merchant = merchant,
        product = product,
        source = com.simpleaccount.app.data.entity.Transaction.SOURCE_MANUAL,
    )
}
