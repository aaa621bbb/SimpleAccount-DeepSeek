package com.simpleaccount.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.simpleaccount.app.ui.navigation.SimpleAccountNavHost
import com.simpleaccount.app.ui.theme.SimpleAccountTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleAccountTheme {
                SimpleAccountNavHost()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SimpleAccountTheme {
        SimpleAccountNavHost()
    }
}
