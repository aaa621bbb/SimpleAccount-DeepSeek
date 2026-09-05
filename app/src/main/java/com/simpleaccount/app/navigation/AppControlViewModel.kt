package com.simpleaccount.app.navigation

import androidx.lifecycle.ViewModel
import com.simpleaccount.app.data.agent.AppControlCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 给 Compose 层提供 AppControlCenter 单例（hiltViewModel 便捷入口） */
@HiltViewModel
class AppControlViewModel @Inject constructor(
    val appControl: AppControlCenter,
) : ViewModel()
