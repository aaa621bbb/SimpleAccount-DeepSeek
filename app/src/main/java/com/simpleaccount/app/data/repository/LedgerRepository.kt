package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.LedgerDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.entity.Ledger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前账本是全局作用域：首页 / 账本 / 统计 / AI / 导入 / 无感记账都只读写这一本。
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val ledgerDao: LedgerDao,
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
) {
    private val _currentId = MutableStateFlow(settingsRepository.currentLedgerId())
    val currentIdFlow: StateFlow<Long> = _currentId.asStateFlow()

    fun currentId(): Long = _currentId.value.coerceAtLeast(Ledger.DEFAULT_ID)

    fun observeAll(): Flow<List<Ledger>> = ledgerDao.observeAll()

    suspend fun getAll(): List<Ledger> = ledgerDao.getAll()

    suspend fun getCurrent(): Ledger? = ledgerDao.getById(currentId())

    suspend fun ensureDefault() {
        if (ledgerDao.count() == 0) {
            val id = ledgerDao.insert(
                Ledger(name = Ledger.DEFAULT_NAME, isDefault = true)
            )
            switchTo(id)
        } else if (ledgerDao.getById(currentId()) == null) {
            val first = ledgerDao.getAll().firstOrNull() ?: return
            switchTo(first.id)
        }
    }

    fun switchTo(id: Long) {
        _currentId.value = id
        settingsRepository.setCurrentLedgerId(id)
    }

    suspend fun create(name: String): Long {
        val trimmed = name.trim().ifBlank { "未命名账本" }.take(16)
        val id = ledgerDao.insert(Ledger(name = trimmed, isDefault = false))
        switchTo(id)
        return id
    }

    suspend fun rename(id: Long, name: String) {
        val existing = ledgerDao.getById(id) ?: return
        val trimmed = name.trim().ifBlank { existing.name }.take(16)
        ledgerDao.update(existing.copy(name = trimmed))
    }

    /**
     * 删除非主账本。会一并删掉该账本下全部流水。当前正在用的被删则切回主账本。
     */
    suspend fun delete(id: Long): String? {
        val existing = ledgerDao.getById(id) ?: return "账本不存在"
        if (existing.isDefault) return "主账本不能删除"
        transactionDao.deleteByLedger(id)
        ledgerDao.deleteById(id)
        if (currentId() == id) {
            val fallback = ledgerDao.getAll().firstOrNull { it.isDefault }
                ?: ledgerDao.getAll().firstOrNull()
            if (fallback != null) switchTo(fallback.id)
        }
        return null
    }
}
