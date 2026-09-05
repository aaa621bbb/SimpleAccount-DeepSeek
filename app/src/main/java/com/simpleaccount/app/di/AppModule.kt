package com.simpleaccount.app.di

import android.content.Context
import androidx.room.Room
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.data.dao.ConversationDao
import com.simpleaccount.app.data.dao.ImportFailureDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.db.AppDatabase
import com.simpleaccount.app.util.CategoryPresets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "simple_account.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            // 硬约束：禁止 fallback 清数据。迁移链 1→5 完整，任何真实升级路径都有迁移；
            // 移除 fallback 后若出现未知路径会直接报错（可排查），而不是悄悄清空用户数据
            .build()
    }
    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMerchantDao(db: AppDatabase): MerchantDao = db.merchantDao()

    @Provides
    fun provideImportLogDao(db: AppDatabase): ImportLogDao = db.importLogDao()

    @Provides
    fun provideImportFailureDao(db: AppDatabase): ImportFailureDao = db.importFailureDao()

    @Provides
    fun provideAiMessageDao(db: AppDatabase): AiMessageDao = db.aiMessageDao()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideSettingDao(db: AppDatabase): SettingDao = db.settingDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): com.simpleaccount.app.data.dao.LedgerDao = db.ledgerDao()

    @Provides
    @Named("ioDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Named("expenseDefaultCategory")
    fun provideExpenseDefaultCategory(): String = CategoryPresets.DEFAULT_EXPENSE_CATEGORY
}
