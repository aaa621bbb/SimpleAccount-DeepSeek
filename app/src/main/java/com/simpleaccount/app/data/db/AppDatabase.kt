package com.simpleaccount.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Category
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
        AiMessage::class,
        Setting::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun settingDao(): SettingDao
}
