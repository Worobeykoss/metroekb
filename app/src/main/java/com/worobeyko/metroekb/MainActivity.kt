package com.worobeyko.metroekb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.worobeyko.metroekb.ui.App
import com.worobeyko.metroekb.ui.theme.MetroEkbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetroEkbTheme {
                App()
            }
        }
    }
}
