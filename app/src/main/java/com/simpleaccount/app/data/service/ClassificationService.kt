package com.simpleaccount.app.data.service

import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.CategoryPresets
import com.simpleaccount.app.util.KeywordRules
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 导入账单时的自动分类服务（分层，不调 AI）：
 * 1. 查 Merchant 记忆表（商家名最长匹配优先），命中用其分类；user_set 永远优先。
 * 2. 未命中 → 文件自带"交易分类/交易类型"列（能归一化到已有分类名时采用）。
 * 3. 再未命中 → 内置关键词规则表。
 * 4. 都未命中 → 收入落"其它收入"、支出落"其它"，写 Merchant status=pending。
 */
@Singleton
class ClassificationService @Inject constructor(
    private val merchantDao: MerchantDao,
    @Named("expenseDefaultCategory") private val defaultCategory: String,
) {

    /**
     * 对一笔记录做自动分类。
     * @param merchant 商家名（可能空）
     * @param product 商品名（可能空）
     * @return 分类名（永不为空，落"其它"兜底），同时会更新 Merchant 记忆表
     */
    suspend fun classify(merchant: String, product: String): String {
        return classifyForImport(merchant, product, "", emptySet())
    }

    /**
     * 导入账单时的自动分类（用户要求的分层优先级）：
     * 1. 商家映射表（merchant 记忆，user_set 用户手动最高；classified 次之）——「我映射表」
     * 2. 文件自带的"交易分类/交易类型"列值（仅在可归一化到已有分类名时采用）——「他表里的交易分类列」
     * 3. 内置关键词规则表
     * 4. 收入兜底"其它收入"、支出兜底"其它" + 商家置 pending
     *
     * @param sourceCategory 文件"交易分类/交易类型"列的原始值（可能为空、也可能是"商户消费/转账"等，
     *                       只有能归一化到已有分类名时才采用，否则忽略，避免拿"交易类型"当分类名）
     * @param type 该行的收支类型（income 时兜底分类用"其它收入"，避免收入挂到支出分类上）
     */
    suspend fun classifyForImport(
        merchant: String,
        product: String,
        sourceCategory: String,
        validCategoryNames: Set<String>,
        type: String = Transaction.TYPE_EXPENSE,
    ): String {
        val mName = merchant.trim()

        // --- 层级1：商家映射表（用户映射表最高优先）---
        if (mName.isNotEmpty()) {
            val known = merchantDao.getByMerchant(mName)
            if (known != null) {
                when (known.status) {
                    Merchant.STATUS_USER_SET -> return known.category
                    Merchant.STATUS_CLASSIFIED -> if (known.category.isNotEmpty()) return known.category
                    else -> { /* pending：继续走下级 */ }
                }
            }
        }

        // --- 层级2：文件自带的"交易分类/交易类型"列；归一化后须为有效分类名才采用。
        // 仅支出行走此层级：账单的分类列是支出口径，收入行（红包/群收款等）套用会挂错类型 ---
        if (type == Transaction.TYPE_EXPENSE && sourceCategory.isNotBlank()) {
            val mapped = KeywordRules.mapSourceCategory(sourceCategory)
            if (mapped in validCategoryNames) {
                upsertMerchant(mName, mapped, Merchant.STATUS_CLASSIFIED)
                return mapped
            }
        }

        // --- 层级3：内置关键词规则表 ---
        val haystack = "$merchant $product"
        val kw = KeywordRules.classify(haystack)
        if (kw != null) {
            upsertMerchant(mName, kw, Merchant.STATUS_CLASSIFIED)
            return kw
        }

        // --- 层级4：收入落"其它收入"、支出落"其它" + pending ---
        val fallback = if (type == Transaction.TYPE_INCOME) CategoryPresets.DEFAULT_INCOME_CATEGORY
        else defaultCategory
        upsertMerchant(mName, fallback, Merchant.STATUS_PENDING)
        return fallback
    }

    private suspend fun upsertMerchant(name: String, category: String, status: String) {
        if (name.isEmpty()) return
        val existing = merchantDao.getByMerchant(name)
        if (existing != null) {
            // user_set 不覆盖；classified 不重复写相同分类
            if (existing.status == Merchant.STATUS_USER_SET) return
            if (existing.status == Merchant.STATUS_CLASSIFIED && existing.category == category) return
            merchantDao.update(existing.copy(category = category, status = status, updatedAt = System.currentTimeMillis()))
        } else {
            merchantDao.insert(
                Merchant(
                    merchant = name, category = category, status = status,
                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
