package com.simpleaccount.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.data.dao.ImportFailureDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.ImportFailure
import com.simpleaccount.app.data.entity.ImportLog
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.entity.Setting
import com.simpleaccount.app.data.entity.Transaction

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Merchant::class,
        ImportLog::class,
        ImportFailure::class,
        AiMessage::class,
        Setting::class,
    ],
    version = 2,
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
    }

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun importFailureDao(): ImportFailureDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun settingDao(): SettingDao
}
