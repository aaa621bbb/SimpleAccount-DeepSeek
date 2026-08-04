package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.util.CategoryPresets
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    fun observeAll(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeByType(type: String): Flow<List<Category>> = categoryDao.observeByType(type)

    suspend fun getAll(): List<Category> = categoryDao.getAll()

    suspend fun getByType(type: String): List<Category> = categoryDao.getByType(type)

    suspend fun getById(id: Long): Category? = categoryDao.getById(id)

    suspend fun getByName(name: String): Category? = categoryDao.getByName(name)

    suspend fun add(c: Category) = categoryDao.insert(c)

    suspend fun addAll(list: List<Category>) = categoryDao.insertAll(list)

    suspend fun deleteAll() = categoryDao.deleteAll()

    suspend fun deleteById(id: Long) = categoryDao.deleteById(id)

    suspend fun update(c: Category) = categoryDao.update(c)

    /** 按类型取第一个分类（用于记录行兜底显示） */
    suspend fun defaultForType(type: String): Category =
        getByType(type).firstOrNull()
            ?: Category(name = if (type == Category.TYPE_EXPENSE) "其它" else "其它收入", type = type)
}
