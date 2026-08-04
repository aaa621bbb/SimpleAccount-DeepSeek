package com.simpleaccount.app.ui.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MerchantManageUiState(
    val statusFilter: String = Merchant.STATUS_PENDING,
    val query: String = "",
    val merchants: List<Merchant> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantManageViewModel @Inject constructor(
    private val merchantRepository: MerchantRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val allStatusOptions = listOf(
        "全部" to "",
        "待归类" to Merchant.STATUS_PENDING,
        "已归类" to Merchant.STATUS_CLASSIFIED,
        "手动" to Merchant.STATUS_USER_SET,
    )

    private val _status = MutableStateFlow(Merchant.STATUS_PENDING)
    private val _query = MutableStateFlow("")

    private val merchantFlow = _status.flatMapLatest { status ->
        if (status.isBlank()) merchantRepository.observeAll()
        else merchantRepository.observeByStatus(status)
    }

    val uiState: StateFlow<MerchantManageUiState> =
        combine(merchantFlow, _query, categoryRepository.observeAll()) { merchants, query, cats ->
            val filtered = if (query.isBlank()) merchants
            else merchants.filter { it.merchant.contains(query, ignoreCase = true) }
            MerchantManageUiState(
                statusFilter = _status.value,
                query = query,
                merchants = filtered,
                categories = cats
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MerchantManageUiState())

    fun setStatus(status: String) { _status.value = status }
    fun setQuery(q: String) { _query.value = q }

    /** 保存分类：状态置 user_set + 历史追改（只改 source='import'） */
    suspend fun assignCategory(m: Merchant, categoryName: String) {
        val valid = categoryRepository.getByName(categoryName) ?: return
        // 更新 merchant
        val newMerchant = m.copy(
            category = valid.name,
            status = Merchant.STATUS_USER_SET,
            updatedAt = System.currentTimeMillis()
        )
        merchantRepository.update(newMerchant)
        // 历史追改：只改 import 记录
        accountRepository.getAllImport()
            .filter { it.merchant.trim() == m.merchant }
            .forEach { t ->
                accountRepository.update(
                    t.copy(category = valid.name, updatedAt = System.currentTimeMillis())
                )
            }
    }
}
