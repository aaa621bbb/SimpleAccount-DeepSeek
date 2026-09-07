package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.SubCategoryDao
import com.simpleaccount.app.data.entity.SubCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubCategoryRepository @Inject constructor(
    private val subCategoryDao: SubCategoryDao,
) {
    fun observeAll(): Flow<List<SubCategory>> = subCategoryDao.observeAll()

    fun observeByParent(parent: String): Flow<List<SubCategory>> = subCategoryDao.observeByParent(parent)

    suspend fun getAll(): List<SubCategory> = subCategoryDao.getAll()

    suspend fun getByParent(parent: String): List<SubCategory> = subCategoryDao.getByParent(parent)

    suspend fun getByParentAndName(parent: String, name: String): SubCategory? =
        subCategoryDao.getByParentAndName(parent, name)

    suspend fun getById(id: Long): SubCategory? = subCategoryDao.getById(id)

    suspend fun add(s: SubCategory): Long = subCategoryDao.insert(s)

    suspend fun addAll(list: List<SubCategory>) = subCategoryDao.insertAll(list)

    suspend fun update(s: SubCategory) = subCategoryDao.update(s)

    suspend fun deleteById(id: Long) = subCategoryDao.deleteById(id)

    suspend fun deleteByParent(parent: String) = subCategoryDao.deleteByParent(parent)

    suspend fun deleteAll() = subCategoryDao.deleteAll()

    suspend fun renameParent(oldParent: String, newParent: String) =
        subCategoryDao.renameParent(oldParent, newParent)

    suspend fun count(): Int = subCategoryDao.count()
}
