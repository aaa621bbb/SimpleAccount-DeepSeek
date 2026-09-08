package com.simpleaccount.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.data.dao.ConversationDao
import com.simpleaccount.app.data.dao.ImportFailureDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.dao.LedgerDao
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Conversation
import com.simpleaccount.app.data.entity.ImportFailure
import com.simpleaccount.app.data.entity.ImportLog
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.entity.Setting
import com.simpleaccount.app.data.entity.SubCategory
import com.simpleaccount.app.data.entity.Transaction

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Merchant::class,
        ImportLog::class,
        ImportFailure::class,
        Conversation::class,
        AiMessage::class,
        Setting::class,
        com.simpleaccount.app.data.entity.Ledger::class,
        SubCategory::class,
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** v1 → v2：新增 import_failures 表（保留既有账本数据） */
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `import_failures` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `batchId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        /** v2 → v3：transactions 表新增 paymentMethod/tradeOrderNo/merchantOrderNo 列（保留既有账本数据） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN `tradeOrderNo` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN `merchantOrderNo` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v3 → v4：一次性清洗历史重复导入（表结构不变，只清数据）。
         * 历史版本判重键未做归一化（商家/商品写法、空格差异），同一笔交易反复导入会积累脏变体。
         * 判定：导入记录中 date+amount+归一化(商家)+归一化(商品) 相同，且单号相同或任一方无单号 → 视为同一笔。
         * 保留策略：优先保留有交易单号的行；同级保留最早 id。
         * 注意两个 2 元奶茶那种同日同商家同额的真实多笔消费，各自单号不同 → 不会被误并。
         * 列名注意：Room 默认列名 = 字段名（驼峰），交易单号列是 `tradeOrderNo`，不是 trade_order_no。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """DELETE FROM transactions WHERE source = 'import' AND id IN (
                        SELECT t.id FROM transactions t WHERE EXISTS (
                            SELECT 1 FROM transactions t2
                            WHERE t2.source = 'import'
                              AND t2.date = t.date
                              AND t2.amount = t.amount
                              AND lower(replace(replace(trim(ifnull(t2.merchant, '')), char(12288), ''), ' ', ''))
                                  = lower(replace(replace(trim(ifnull(t.merchant, '')), char(12288), ''), ' ', ''))
                              AND lower(replace(replace(trim(ifnull(t2.product, '')), char(12288), ''), ' ', ''))
                                  = lower(replace(replace(trim(ifnull(t.product, '')), char(12288), ''), ' ', ''))
                              AND (
                                    ifnull(t2.tradeOrderNo, '') = ifnull(t.tradeOrderNo, '')
                                    OR ifnull(t2.tradeOrderNo, '') = ''
                                    OR ifnull(t.tradeOrderNo, '') = ''
                                  )
                              AND (
                                    (length(ifnull(t2.tradeOrderNo, '')) > 0) > (length(ifnull(t.tradeOrderNo, '')) > 0)
                                    OR (
                                        (length(ifnull(t2.tradeOrderNo, '')) > 0) = (length(ifnull(t.tradeOrderNo, '')) > 0)
                                        AND t2.id < t.id
                                    )
                                  )
                        )
                    )"""
                )
            }
        }
        /**
         * v4 → v5：AI 对话多会话化。
         * - 新增 conversations 表；
         * - ai_messages 重构：TEXT 主键（可溯源撤回）+ conversationId 归属 + 工具调用字段 + 状态位；
         * - 旧消息全部迁移进一个 "legacy" 会话，id 用 legacy-序号 生成，不丢数据。
         * 列名注意：Room 默认列名 = 字段名（驼峰）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `ai_messages_new` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `toolCallId` TEXT,
                        `toolName` TEXT,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO conversations (id, title, createdAt, updatedAt) " +
                        "VALUES ('legacy', '历史对话', $now, $now)"
                )
                db.execSQL(
                    """INSERT INTO ai_messages_new
                        (id, conversationId, role, content, timestamp, toolCallId, toolName, status)
                        SELECT printf('legacy-%05d', rowid), 'legacy', role, content, timestamp,
                               NULL, NULL, 'done'
                        FROM ai_messages ORDER BY timestamp ASC, rowid ASC"""
                )
                // 旧版多行欢迎语（用户反馈占屏）迁移时替换为短版
                db.execSQL(
                    "UPDATE ai_messages_new SET content = '你好，我是 AI 记账管家 🧾 会先查你的真实账本再回答～' " +
                        "WHERE content LIKE '你好！我是你的 AI 记账管家%'"
                )
                db.execSQL("DROP TABLE ai_messages")
                db.execSQL("ALTER TABLE ai_messages_new RENAME TO ai_messages")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_messages_conversationId` " +
                        "ON `ai_messages` (`conversationId`)"
                )
            }
        }

        /** v5 → v6：transactions 新增 time 列（HH:mm），账单/截图里的精确时间可入库 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN `time` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6 → v7：多账本。旧流水全部归入「主账本」。 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `ledgers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL
                    )"""
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO ledgers (id, name, createdAt, isDefault) VALUES (1, '主账本', $now, 1)"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN `ledgerId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` ON `transactions` (`ledgerId`)")
            }
        }

        /** v7 → v8：二级分类体系。新建 sub_categories 表 + transactions 新增 subCategory 列。 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sub_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parent` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isPreset` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sub_categories_parent` ON `sub_categories` (`parent`)")
                db.execSQL("ALTER TABLE transactions ADD COLUMN `subCategory` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v8 → v9：二级分类图标列 + 常见二级回填图标名。 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sub_categories ADD COLUMN `iconName` TEXT NOT NULL DEFAULT 'more_horiz'"
                )
                // 常见二级名回填（与 SubCategoryPresets.SUB_ICONS 对齐的子集）
                val pairs = listOf(
                    "早餐" to "free_breakfast", "午餐" to "lunch_dining", "晚餐" to "dinner_dining",
                    "夜宵" to "nightlife", "奶茶" to "local_cafe", "咖啡" to "coffee",
                    "零食" to "cookie", "水果" to "nutrition", "外卖" to "delivery_dining",
                    "公交" to "directions_bus", "地铁" to "directions_subway", "打车" to "local_taxi",
                    "火车" to "train", "飞机" to "flight", "加油" to "local_gas_station",
                    "停车" to "local_parking", "骑行" to "directions_bike", "高速" to "add_road",
                    "日用" to "shopping_basket", "服饰" to "checkroom", "数码" to "devices",
                    "电影" to "movie", "游戏" to "sports_esports", "会员" to "card_membership",
                    "房租" to "apartment", "水电" to "electrical_services", "话费" to "phone",
                    "红包" to "card_giftcard", "月薪" to "payments", "股票" to "show_chart",
                )
                pairs.forEach { (name, icon) ->
                    db.execSQL(
                        "UPDATE sub_categories SET iconName = ? WHERE name = ? AND (iconName = '' OR iconName = 'more_horiz')",
                        arrayOf(icon, name),
                    )
                }
            }
        }
    }

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun importFailureDao(): ImportFailureDao
    abstract fun conversationDao(): ConversationDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun settingDao(): SettingDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun subCategoryDao(): com.simpleaccount.app.data.dao.SubCategoryDao
}
