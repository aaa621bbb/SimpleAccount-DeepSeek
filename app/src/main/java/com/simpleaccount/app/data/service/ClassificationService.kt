package com.simpleaccount.app.data.service

import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.util.KeywordRules
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 导入账单时的自动分类服务（分层，不调 AI）：
 * 1. 查 Merchant 记忆表（商家名最长匹配优先），命中用其分类；user_set 永远优先。
 * 2. 未命中 → 内置关键词规则表。
 * 3. 都未命中 → 归"其它"，写 Merchant status=pending。
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
        // --- 层级1：Merchant 记忆表 ---
        val mName = merchant.trim()
        if (mName.isNotEmpty()) {
            val known = merchantDao.getByMerchant(mName)
            if (known != null) {
                when (known.status) {
                    Merchant.STATUS_USER_SET -> return known.category  // 用户手动最高优先
                    Merchant.STATUS_CLASSIFIED -> if (known.category.isNotEmpty()) return known.category
                    else -> { /* pending：跳过，继续走关键词 */ }
                }
            }
        }

        // --- 层级2：关键词规则表 ---
        val haystack = "$merchant $product"
        val kw = KeywordRules.classify(haystack)
        if (kw != null) {
            upsertMerchant(mName, kw, Merchant.STATUS_CLASSIFIED)
            return kw
        }

        // --- 层级3：其它 + pending ---
        upsertMerchant(mName, defaultCategory, Merchant.STATUS_PENDING)
        return defaultCategory
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
