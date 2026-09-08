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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddUiState(
    val type: String = Transaction.TYPE_EXPENSE,
    val amountText: String = "",
    val selectedCategory: Category? = null,
    /** 选中的二级分类名（空=不细分）。 */
    val subCategory: String = "",
    val date: String = DateUtil.today(),
    /** 时间 HH:mm（手动记默认当前时刻，可改） */
    val time: String = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) },
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
    private val subCategoryRepository: com.simpleaccount.app.data.repository.SubCategoryRepository,
    private val settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dateStyle = settingsRepository.datePickerFlow
    val timeStyle = settingsRepository.timePickerFlow

    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    private val _categoriesByType = MutableStateFlow<List<Category>>(emptyList())
    val categoriesByType = _categoriesByType.asStateFlow()

    /** 当前选中一级分类下的二级分类选项（按用户自定义顺序）。 */
    private val _subOptions = MutableStateFlow<List<com.simpleaccount.app.data.entity.SubCategory>>(emptyList())
    val subOptions = _subOptions.asStateFlow()

    private val knownMerchants = accountRepository.observeAll()
        .map { list -> list.map { it.merchant.trim() }.filter { it.isNotEmpty() }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 前缀优先，其次包含；不把相近店名合成一家。 */
    val merchantHints: StateFlow<List<String>> = combine(_state, knownMerchants) { s, all ->
        val q = s.merchant.trim()
        if (q.isEmpty()) return@combine emptyList()
        val prefix = all.filter { it.startsWith(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
        val contains = all.filter {
            it.contains(q, ignoreCase = true) &&
                !it.startsWith(q, ignoreCase = true) &&
                !it.equals(q, ignoreCase = true)
        }
        (prefix + contains).take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private fun refreshSubs() {
        viewModelScope.launch {
            val name = _state.value.selectedCategory?.name
            _subOptions.value = if (name.isNullOrBlank()) emptyList()
            else subCategoryRepository.getByParent(name)
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
                    subCategory = t.subCategory,
                    date = t.date,
                    time = t.time,
                    merchant = t.merchant,
                    product = t.product,
                    note = t.note,
                    editId = t.id,
                    loading = false
                )
                reloadCategories()
                refreshSubs()
            } else {
                _state.value = _state.value.copy(loading = false, error = "记录不存在")
            }
        }
    }

    fun onTypeChange(type: String) {
        _state.value = _state.value.copy(type = type, selectedCategory = null, subCategory = "", error = null)
        _subOptions.value = emptyList()
        reloadCategories()
    }

    fun onAmountChange(v: String) {
        _state.value = _state.value.copy(amountText = v.replace(",", ""), error = null)
    }

    fun onCategorySelect(c: Category?) {
        _state.value = _state.value.copy(selectedCategory = c, subCategory = "", error = null)
        refreshSubs()
    }

    /** 选择二级分类（再点一次取消细分）。 */
    fun onSubCategorySelect(name: String) {
        val cur = _state.value.subCategory
        _state.value = _state.value.copy(subCategory = if (cur == name) "" else name, error = null)
    }

    // ---------------- 就地分类 CRUD（记一笔页，不跳设置） ----------------

    /** 新增一级分类并选中。 */
    suspend fun addCategoryInPlace(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(error = "分类名不能为空")
            return false
        }
        if (categoryRepository.getByName(trimmed) != null) {
            _state.value = _state.value.copy(error = "分类「$trimmed」已存在")
            return false
        }
        val type = _state.value.type
        val list = categoryRepository.getByType(type)
        val maxOrder = (list.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val cat = Category(
            name = trimmed,
            type = type,
            sortOrder = maxOrder,
            isPreset = false,
            iconName = "more_horiz",
            colorHex = "#BDC3C7",
        )
        categoryRepository.add(cat)
        _categoriesByType.value = categoryRepository.getByType(type)
        // 重新拉取以拿到 id
        val created = categoryRepository.getByName(trimmed)
        if (created != null) onCategorySelect(created)
        return true
    }

    /** 重命名当前选中的自定义一级分类（预置只允许改不了名）。 */
    suspend fun renameCategoryInPlace(newName: String): Boolean {
        val cat = _state.value.selectedCategory ?: return false
        if (cat.isPreset) {
            _state.value = _state.value.copy(error = "预置分类不可改名")
            return false
        }
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == cat.name) return false
        if (categoryRepository.getByName(trimmed) != null) {
            _state.value = _state.value.copy(error = "分类「$trimmed」已存在")
            return false
        }
        accountRepository.getAll()
            .filter { it.category == cat.name }
            .forEach { t ->
                accountRepository.update(t.copy(category = trimmed, updatedAt = System.currentTimeMillis()))
            }
        subCategoryRepository.renameParent(cat.name, trimmed)
        categoryRepository.update(cat.copy(name = trimmed))
        _categoriesByType.value = categoryRepository.getByType(_state.value.type)
        val updated = categoryRepository.getByName(trimmed)
        if (updated != null) {
            _state.value = _state.value.copy(selectedCategory = updated, error = null)
            refreshSubs()
        }
        return true
    }

    /** 删除当前选中一级分类（账单迁到「其它」）。预置不可删。 */
    suspend fun deleteCategoryInPlace(): Boolean {
        val cat = _state.value.selectedCategory ?: return false
        if (cat.isPreset) {
            _state.value = _state.value.copy(error = "预置分类不可删除")
            return false
        }
        val fallback = if (cat.type == Category.TYPE_EXPENSE)
            com.simpleaccount.app.util.CategoryPresets.DEFAULT_EXPENSE_CATEGORY
        else com.simpleaccount.app.util.CategoryPresets.DEFAULT_INCOME_CATEGORY
        val fallbackEntity = categoryRepository.getByName(fallback) ?: return false
        accountRepository.getAll()
            .filter { it.category == cat.name }
            .forEach { t ->
                accountRepository.update(
                    t.copy(category = fallbackEntity.name, subCategory = "", updatedAt = System.currentTimeMillis())
                )
            }
        subCategoryRepository.deleteByParent(cat.name)
        categoryRepository.deleteById(cat.id)
        _categoriesByType.value = categoryRepository.getByType(_state.value.type)
        _state.value = _state.value.copy(selectedCategory = fallbackEntity, subCategory = "", error = null)
        refreshSubs()
        return true
    }

    /** 在当前一级下新增二级并选中。 */
    suspend fun addSubInPlace(name: String): Boolean {
        val parent = _state.value.selectedCategory?.name ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val existing = subCategoryRepository.getByParent(parent)
        if (existing.any { it.name == trimmed }) {
            _state.value = _state.value.copy(error = "二级「$trimmed」已存在")
            return false
        }
        val maxOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        subCategoryRepository.add(
            com.simpleaccount.app.data.entity.SubCategory(
                parent = parent,
                name = trimmed,
                sortOrder = maxOrder,
                iconName = com.simpleaccount.app.util.SubCategoryPresets.iconFor(trimmed),
            )
        )
        refreshSubs()
        onSubCategorySelect(trimmed)
        return true
    }

    /** 删除指定二级（账单二级清空）。 */
    suspend fun deleteSubInPlace(name: String): Boolean {
        val parent = _state.value.selectedCategory?.name ?: return false
        val sub = subCategoryRepository.getByParent(parent).firstOrNull { it.name == name } ?: return false
        accountRepository.getAll()
            .filter { it.category == parent && it.subCategory == name }
            .forEach { t ->
                accountRepository.update(t.copy(subCategory = "", updatedAt = System.currentTimeMillis()))
            }
        subCategoryRepository.deleteById(sub.id)
        if (_state.value.subCategory == name) {
            _state.value = _state.value.copy(subCategory = "")
        }
        refreshSubs()
        return true
    }

    /** 重命名二级分类。 */
    suspend fun renameSubInPlace(oldName: String, newName: String): Boolean {
        val parent = _state.value.selectedCategory?.name ?: return false
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return false
        val list = subCategoryRepository.getByParent(parent)
        if (list.any { it.name == trimmed }) {
            _state.value = _state.value.copy(error = "二级「$trimmed」已存在")
            return false
        }
        val sub = list.firstOrNull { it.name == oldName } ?: return false
        accountRepository.getAll()
            .filter { it.category == parent && it.subCategory == oldName }
            .forEach { t ->
                accountRepository.update(t.copy(subCategory = trimmed, updatedAt = System.currentTimeMillis()))
            }
        subCategoryRepository.update(sub.copy(name = trimmed))
        if (_state.value.subCategory == oldName) {
            _state.value = _state.value.copy(subCategory = trimmed)
        }
        refreshSubs()
        return true
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
                        subCategory = s.subCategory.trim(),
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
                    subCategory = s.subCategory.trim(),
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
