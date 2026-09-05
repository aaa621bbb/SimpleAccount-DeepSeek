package com.simpleaccount.app.data.dao

/** Room 聚合查询返回行 */
data class TotalRow(val total: Long?, val type: String? = null)

/** 分类聚合 */
data class CategoryTotal(val category: String, val total: Long?)

/** 趋势聚合（按日期+类型） */
data class RangeRow(val date: String, val type: String?, val total: Long?)
