package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
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
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _type = MutableStateFlow(Category.TYPE_EXPENSE)
    val type: StateFlow<String> = _type.asStateFlow()

    val uiState: StateFlow<CategoryManageUiState> =
        combine(_type, categoryRepository.observeAll()) { type, all ->
            CategoryManageUiState(type = type, categories = all.filter { it.type == type })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryManageUiState())

    fun setType(t: String) {
        _type.value = t
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
        // 删除前先迁移其下记录到"其它"
        val fallback = if (category.type == Category.TYPE_EXPENSE)
            CategoryPresets.DEFAULT_EXPENSE_CATEGORY else CategoryPresets.DEFAULT_INCOME_CATEGORY
        val fallbackEntity = categoryRepository.getByName(fallback) ?: return
        accountRepository.getAll()
            .filter { it.category == category.name }
            .forEach { t ->
                accountRepository.update(
                    t.copy(category = fallbackEntity.name, updatedAt = System.currentTimeMillis())
                )
            }
        categoryRepository.deleteById(category.id)
    }

    /** 判断分类是否非空（删除前需要二次确认并提示迁移） */
    suspend fun isCategoryEmpty(name: String): Boolean {
        return accountRepository.getAll().none { it.category == name }
    }
}
