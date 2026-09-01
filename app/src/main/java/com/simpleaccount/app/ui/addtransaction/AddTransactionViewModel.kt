package com.simpleaccount.app.ui.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddUiState(
    val type: String = Transaction.TYPE_EXPENSE,
    val amountText: String = "",
    val selectedCategory: Category? = null,
    val date: String = DateUtil.today(),
    /** 时间 HH:mm（手动记默认当前时刻，可改） */
    val time: String = "",
    val merchant: String = "",
    val product: String = "",
    val note: String = "",
    val error: String? = null,
    val editId: Long? = null,
    val loading: Boolean = false,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    private val _categoriesByType = MutableStateFlow<List<Category>>(emptyList())
    val categoriesByType = _categoriesByType.asStateFlow()

    private var editId: Long? = null

    init {
        val id = savedStateHandle.get<Long>("id")
        editId = id
        _state.value = _state.value.copy(editId = id, loading = id != null)
        if (id != null) {
            loadForEdit(id)
        }
        reloadCategories()
    }

    private fun reloadCategories() {
        viewModelScope.launch {
            _categoriesByType.value = categoryRepository.getByType(_state.value.type)
        }
    }

    private fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val t = accountRepository.getById(id)
            if (t != null) {
                val cat = categoryRepository.getByName(t.category)
                _state.value = AddUiState(
                    type = t.type,
                    amountText = MoneyUtil.fenToYuan(t.amount),
                    selectedCategory = cat,
                    date = t.date,
                    time = t.time,
                    merchant = t.merchant,
                    product = t.product,
                    note = t.note,
                    editId = t.id,
                    loading = false
                )
                reloadCategories()
            } else {
                _state.value = _state.value.copy(loading = false, error = "记录不存在")
            }
        }
    }

    fun onTypeChange(type: String) {
        _state.value = _state.value.copy(type = type, selectedCategory = null, error = null)
        reloadCategories()
    }

    fun onAmountChange(v: String) {
        _state.value = _state.value.copy(amountText = v.replace(",", ""), error = null)
    }

    fun onCategorySelect(c: Category?) {
        _state.value = _state.value.copy(selectedCategory = c, error = null)
    }

    fun onDateChange(date: String) {
        _state.value = _state.value.copy(date = date, error = null)
    }

    fun onDateSet(y: Int, m: Int, d: Int) {
        val date = "%04d-%02d-%02d".format(y, m, d)
        _state.value = _state.value.copy(date = date, error = null)
    }

    fun onTimeChange(v: String) {
        // 只接受 HH:mm（可空）
        if (v.isBlank() || v.matches(Regex("\\d{1,2}:\\d{2}"))) {
            _state.value = _state.value.copy(time = v, error = null)
        }
    }

    fun onMerchantChange(v: String) = _state.value.let { _state.value = it.copy(merchant = v) }
    fun onProductChange(v: String) = _state.value.let { _state.value = it.copy(product = v) }
    fun onNoteChange(v: String) = _state.value.let { _state.value = it.copy(note = v) }

    /** 现有交易里出现过的商家名（去重排序，供选择） */
    suspend fun knownMerchants(): List<String> =
        accountRepository.getAll()
            .map { it.merchant.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    /** 现有交易里出现过的商品名（去重排序，供选择） */
    suspend fun knownProducts(): List<String> =
        accountRepository.getAll()
            .map { it.product.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    /** 校验并保存，返回 true 表示成功 */
    suspend fun save(): Boolean {
        val s = _state.value
        val amountFen = MoneyUtil.parseToFen(s.amountText)
        if (amountFen == null || amountFen <= 0) {
            _state.value = s.copy(error = "请输入有效金额")
            return false
        }
        val cat = s.selectedCategory
        if (cat == null) {
            _state.value = s.copy(error = "请选择分类")
            return false
        }
        if (!DateUtil.isValidYmd(s.date)) {
            _state.value = s.copy(error = "日期格式不正确")
            return false
        }
        val now = System.currentTimeMillis()
        if (s.editId != null) {
            val existing = accountRepository.getById(s.editId)
            if (existing != null) {
                accountRepository.update(
                    existing.copy(
                        amount = amountFen,
                        type = s.type,
                        category = cat.name,
                        date = s.date,
                        time = s.time,
                        merchant = s.merchant.trim(),
                        product = s.product.trim(),
                        note = s.note.trim(),
                        updatedAt = now
                    )
                )
            }
        } else {
            accountRepository.insert(
                Transaction(
                    amount = amountFen,
                    type = s.type,
                    category = cat.name,
                    date = s.date,
                    time = s.time,
                    merchant = s.merchant.trim(),
                    product = s.product.trim(),
                    note = s.note.trim(),
                    source = Transaction.SOURCE_MANUAL,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return true
    }
}
