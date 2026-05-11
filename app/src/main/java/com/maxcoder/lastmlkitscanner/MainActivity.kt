package com.maxcoder.lastmlkitscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxcoder.lastmlkitscanner.presentation.NavGraph
import com.maxcoder.lastmlkitscanner.ui.theme.LastMlKitScannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LastMlKitScannerTheme {
                NavGraph()
            }
        }
    }
}