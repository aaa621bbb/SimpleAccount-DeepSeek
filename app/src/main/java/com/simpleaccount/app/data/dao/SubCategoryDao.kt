package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simpleaccount.app.data.entity.SubCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface SubCategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(s: SubCategory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<SubCategory>)

    @Update
    suspend fun update(s: SubCategory)

    @Query("DELETE FROM sub_categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sub_categories WHERE `parent` = :parent")
    suspend fun deleteByParent(parent: String)

    @Query("DELETE FROM sub_categories")
    suspend fun deleteAll()

    @Query("UPDATE sub_categories SET `parent` = :newParent WHERE `parent` = :oldParent")
    suspend fun renameParent(oldParent: String, newParent: String)

    @Query("SELECT * FROM sub_categories ORDER BY `parent` ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SubCategory>>

    @Query("SELECT * FROM sub_categories WHERE `parent` = :parent ORDER BY sortOrder ASC, id ASC")
    fun observeByParent(parent: String): Flow<List<SubCategory>>

    @Query("SELECT * FROM sub_categories WHERE `parent` = :parent ORDER BY sortOrder ASC, id ASC")
    suspend fun getByParent(parent: String): List<SubCategory>

    @Query("SELECT * FROM sub_categories WHERE `parent` = :parent AND name = :name LIMIT 1")
    suspend fun getByParentAndName(parent: String, name: String): SubCategory?

    @Query("SELECT * FROM sub_categories WHERE id = :id")
    suspend fun getById(id: Long): SubCategory?

    @Query("SELECT * FROM sub_categories")
    suspend fun getAll(): List<SubCategory>

    @Query("SELECT COUNT(*) FROM sub_categories")
    suspend fun count(): Int
}
