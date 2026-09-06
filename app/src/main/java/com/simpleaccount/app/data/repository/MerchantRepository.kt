package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.entity.Merchant
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantRepository @Inject constructor(
    private val merchantDao: MerchantDao,
) {
    fun observeAll(): Flow<List<Merchant>> = merchantDao.observeAll()

    fun observeByStatus(status: String): Flow<List<Merchant>> = merchantDao.observeByStatus(status)

    fun observeSearch(q: String): Flow<List<Merchant>> = merchantDao.observeSearch(q)

    suspend fun getAll(): List<Merchant> = merchantDao.getAll()

    suspend fun getByStatus(status: String): List<Merchant> = merchantDao.getByStatus(status)

    suspend fun getByMerchant(name: String): Merchant? = merchantDao.getByMerchant(name)

    suspend fun insert(m: Merchant): Long = merchantDao.insert(m)

    suspend fun update(m: Merchant) = merchantDao.update(m)

    suspend fun deleteById(id: Long) = merchantDao.deleteById(id)

    suspend fun deleteAll() = merchantDao.deleteAll()
}
