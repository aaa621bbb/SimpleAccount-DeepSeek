package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.KeywordRules
import com.simpleaccount.app.util.SpokenTimeParser
import com.simpleaccount.app.util.SubCategoryPresets
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * v2.31 智能体重建回归用例。
 *
 * 封锁线：
 *  - 口语时间有依据才解析，无依据追问，永不默认 12:00；
 *  - 语义解析金额/商家/商品可靠切分，商家商品互斥；
 *  - 写闸门不可绕过（13 个写工具全覆盖，只读/导航/速记不限）；
 *  - 执行审计轨迹以 rows 为唯一回执依据；
 *  - 二级分类预置与规则映射有把握才给、无把握 null。
 */
class AgentV231Test {

    private val now = LocalDate.of(2026, 9, 7)
    private val cats = setOf("餐饮", "交通", "购物", "其它", "工资", "退款")

    // ---------------- 口语时间 ----------------

    @Test
    fun spokenTime_lastNight() {
        val t = SpokenTimeParser.parse("昨晚吃了20块钱晚饭", now)
        assertEquals("2026-09-06", t.date)
        assertTrue(t.dateExplicit)
        assertEquals("20:00", t.time)
        assertTrue(t.timeExplicit)
        assertTrue(t.timeIsPeriodDefault)
        assertFalse(t.timeAmbiguous)
    }

    @Test
    fun spokenTime_afternoonThree() {
        val t = SpokenTimeParser.parse("下午三点买了杯咖啡", now)
        assertEquals("15:00", t.time)
        assertTrue(t.timeExplicit)
        assertFalse(t.timeIsPeriodDefault)
        assertFalse(t.timeAmbiguous)
    }

    @Test
    fun spokenTime_dayBeforeNoon() {
        val t = SpokenTimeParser.parse("前天中午吃饭花了50", now)
        assertEquals("2026-09-05", t.date)
        assertTrue(t.dateExplicit)
        assertEquals("12:00", t.time)
        assertTrue(t.timeIsPeriodDefault)
    }

    @Test
    fun spokenTime_bareHourIsAmbiguous() {
        val t = SpokenTimeParser.parse("三点开了个会", now)
        assertTrue(t.timeAmbiguous)
        assertNull(t.time)
    }

    @Test
    fun spokenTime_noEvidenceAsks() {
        val t = SpokenTimeParser.parse("吃了顿饭", now)
        assertNull(t.date)
        assertNull(t.time)
        assertTrue(t.needsDateAsk())
        assertTrue(t.needsTimeAsk())
    }

    @Test
    fun spokenTime_neverDefaultsToNoon() {
        // 无任何时间依据：不得出现 12:00 默认
        val t = SpokenTimeParser.parse("买了杯咖啡25元", now)
        assertNull(t.time)
        assertFalse(t.timeExplicit)
    }

    // ---------------- 语义解析 ----------------

    @Test
    fun utterance_canteenLunch() {
        val u = UtteranceParser.parse("在食堂吃了午饭25元", cats, now = now)
        assertEquals(2500L, u.amountFen)
        assertEquals("食堂", u.merchant)
        assertEquals("午饭", u.product)
        assertEquals(Transaction.TYPE_EXPENSE, u.type)
        assertEquals("餐饮", u.category)
        // 无时间依据 → 必须追问日期/时刻
        assertTrue(u.needAsk.contains("date"))
        assertTrue(u.needAsk.contains("time"))
        assertFalse(u.needAsk.contains("amount"))
    }

    @Test
    fun utterance_merchantProductMutuallyExclusive() {
        val u = UtteranceParser.parse("在食堂吃了午饭25元", cats, now = now)
        assertTrue(u.product.isBlank() || u.product != u.merchant)
    }

    @Test
    fun utterance_incomeSalary() {
        val u = UtteranceParser.parse("发工资8000元", cats, now = now)
        assertEquals(800000L, u.amountFen)
        assertEquals(Transaction.TYPE_INCOME, u.type)
    }

    @Test
    fun utterance_missingAmountAsks() {
        val u = UtteranceParser.parse("在食堂吃了午饭", cats, now = now)
        assertNull(u.amountFen)
        assertTrue(u.needAsk.contains("amount"))
    }

    // ---------------- 写闸门 ----------------

    @Test
    fun writeGate_coversAllWrites() {
        assertEquals(13, WriteGate.GATED.size)
        listOf(
            "add_transaction", "withdraw_transaction", "delete_transaction",
            "edit_transaction", "update_transaction_category", "reclassify_transactions",
            "create_category", "delete_category", "set_merchant_category",
            "classify_merchants", "set_monthly_budget", "set_theme", "set_auto_record",
        ).forEach { assertTrue("$it 应在闸门内", WriteGate.isGated(it)) }
    }

    @Test
    fun writeGate_readsNotGated() {
        listOf("query_transactions", "get_month_summary", "navigate", "memory_write")
            .forEach { assertFalse("$it 不应被闸门拦截", WriteGate.isGated(it)) }
    }

    // ---------------- 执行审计 ----------------

    @Test
    fun audit_verdictLineShowsRows() {
        val r = ToolExecutionRecord(
            tool = "add_transaction", ok = true, affectedRows = 1,
            elapsedMs = 23, permission = ToolPermission.NOT_REQUIRED, permissionNote = null,
        )
        val line = r.verdictLine()
        assertTrue(line.contains("add_transaction"))
        assertTrue(line.contains("rows=1"))
        assertTrue(line.contains("成功"))
    }

    @Test
    fun audit_zeroRowsNeverClaimsRows() {
        val r = ToolExecutionRecord(
            tool = "delete_transaction", ok = false, affectedRows = 0,
            elapsedMs = 5, permission = ToolPermission.NOT_REQUIRED, permissionNote = "用户未确认",
        )
        val line = r.verdictLine()
        assertTrue(line.contains("失败"))
        assertFalse(line.contains("rows="))
        assertTrue(line.contains("用户未确认"))
    }

    // ---------------- 二级分类 ----------------

    @Test
    fun subPresets_coverSixteenParents() {
        val all = SubCategoryPresets.presetSubCategories()
        assertTrue(all.size >= 16)
        val parents = all.map { it.parent }.toSet()
        assertTrue(parents.contains("餐饮"))
        assertTrue(parents.contains("交通"))
        // 同 parent 内 sortOrder 不重复
        all.groupBy { it.parent }.forEach { (p, list) ->
            val orders = list.map { it.sortOrder }
            assertEquals("$p 内排序号重复", orders.size, orders.toSet().size)
        }
    }

    @Test
    fun classifySub_confidentOnly() {
        // 有把握 → 给出；无把握 → null（不硬猜）
        assertNotNull(KeywordRules.classifySub("早餐包子", "餐饮"))
        assertNull(KeywordRules.classifySub("一笔说不清的开销xyz", "其它"))
        assertNull(KeywordRules.classifySub("早餐", null))
    }
}
