package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 普通设置项。
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)
