package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.SubCategory
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.SubCategoryRepository
import com.simpleaccount.app.util.CategoryPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManageUiState(
    val type: String = Category.TYPE_EXPENSE,
    val categories: List<Category> = emptyList(),
    /** 一级分类名 → 其二级分类（按用户自定义顺序）。 */
    val subs: Map<String, List<SubCategory>> = emptyMap(),
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _type = MutableStateFlow(Category.TYPE_EXPENSE)
    val type: StateFlow<String> = _type.asStateFlow()

    val uiState: StateFlow<CategoryManageUiState> =
        combine(_type, categoryRepository.observeAll(), subCategoryRepository.observeAll()) { type, all, subs ->
            CategoryManageUiState(
                type = type,
                categories = all.filter { it.type == type },
                subs = subs.groupBy { it.parent }.mapValues { (_, v) -> v.sortedWith(compareBy({ it.sortOrder }, { it.id })) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryManageUiState())

    fun setType(t: String) {
        _type.value = t
    }

    /** 二次修改图标/颜色；自定义分类还可改名（预置只改外观）。改名同步迁移二级归属与账单。 */
    suspend fun updateStyle(cat: Category, iconName: String, colorHex: String, name: String? = null): Boolean {
        val newName = if (cat.isPreset) cat.name else (name?.trim()?.ifBlank { cat.name } ?: cat.name)
        if (newName != cat.name) {
            if (categoryRepository.getByName(newName) != null) return false
            accountRepository.getAll()
                .filter { it.category == cat.name }
                .forEach { t ->
                    accountRepository.update(t.copy(category = newName, updatedAt = System.currentTimeMillis()))
                }
            subCategoryRepository.renameParent(cat.name, newName)
        }
        categoryRepository.update(
            cat.copy(iconName = iconName, colorHex = colorHex, name = newName)
        )
        return true
    }

    suspend fun add(name: String, iconName: String, colorHex: String): Boolean {
        val type = _type.value
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (categoryRepository.getByName(trimmed) != null) return false
        val list = categoryRepository.getByType(type)
        val maxOrder = (list.maxOfOrNull { it.sortOrder } ?: -1) + 1
        categoryRepository.add(
            Category(
                name = trimmed, type = type, sortOrder = maxOrder,
                isPreset = false, iconName = iconName, colorHex = colorHex
            )
        )
        return true
    }

    suspend fun delete(category: Category) {
        // 删除前先迁移其下记录到"其它"（二级清空），再删其二级分类
        val fallback = if (category.type == Category.TYPE_EXPENSE)
            CategoryPresets.DEFAULT_EXPENSE_CATEGORY else CategoryPresets.DEFAULT_INCOME_CATEGORY
        val fallbackEntity = categoryRepository.getByName(fallback) ?: return
        accountRepository.getAll()
            .filter { it.category == category.name }
            .forEach { t ->
                accountRepository.update(
                    t.copy(category = fallbackEntity.name, subCategory = "", updatedAt = System.currentTimeMillis())
                )
            }
        subCategoryRepository.deleteByParent(category.name)
        categoryRepository.deleteById(category.id)
    }

    /** 判断分类是否非空（删除前需要二次确认并提示迁移） */
    suspend fun isCategoryEmpty(name: String): Boolean {
        return accountRepository.getAll().none { it.category == name }
    }

    // ---------------- 二级分类 ----------------

    /** 新增二级分类（同 parent 下重名拒绝）。 */
    suspend fun addSub(parent: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || parent.isBlank()) return false
        val existing = subCategoryRepository.getByParent(parent)
        if (existing.any { it.name == trimmed }) return false
        val maxOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
subCategoryRepository.add(
            SubCategory(
                parent = parent,
                name = trimmed,
                sortOrder = maxOrder,
                iconName = com.simpleaccount.app.util.SubCategoryPresets.iconFor(trimmed),
            )
        )
        return true
    }

    /** 删除二级分类：使用它的账单二级清空（一级保留）。 */
    suspend fun deleteSub(sub: SubCategory) {
        accountRepository.getAll()
            .filter { it.category == sub.parent && it.subCategory == sub.name }
            .forEach { t ->
                accountRepository.update(t.copy(subCategory = "", updatedAt = System.currentTimeMillis()))
            }
        subCategoryRepository.deleteById(sub.id)
    }

    /** 二级分类是否被账单使用（删除前提示）。 */
    suspend fun isSubInUse(sub: SubCategory): Boolean =
        accountRepository.getAll().any { it.category == sub.parent && it.subCategory == sub.name }

    // ---------------- 自定义排序（用户顺序，不被内置锁死） ----------------

    /** 一级分类上移/下移（同 Tab 内交换 sortOrder）。 */
    fun moveCategory(cat: Category, up: Boolean) {
        viewModelScope.launch {
            val list = categoryRepository.getByType(cat.type)
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            val i = list.indexOfFirst { it.id == cat.id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in list.indices) return@launch
            val a = list[i]
            val b = list[j]
            categoryRepository.update(a.copy(sortOrder = b.sortOrder))
            categoryRepository.update(b.copy(sortOrder = a.sortOrder))
        }
    }

    /** 二级分类上移/下移（同 parent 内交换 sortOrder）。 */
    fun moveSub(sub: SubCategory, up: Boolean) {
        viewModelScope.launch {
            val list = subCategoryRepository.getByParent(sub.parent)
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            val i = list.indexOfFirst { it.id == sub.id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in list.indices) return@launch
            val a = list[i]
            val b = list[j]
            subCategoryRepository.update(a.copy(sortOrder = b.sortOrder))
            subCategoryRepository.update(b.copy(sortOrder = a.sortOrder))
        }
    }
}
