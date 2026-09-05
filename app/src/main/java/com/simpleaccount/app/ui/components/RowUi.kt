package com.simpleaccount.app.ui.components

import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction

/** 带分类信息的流水展示行（供首页/账本等共用） */
data class RowUi(val transaction: Transaction, val category: Category?)
