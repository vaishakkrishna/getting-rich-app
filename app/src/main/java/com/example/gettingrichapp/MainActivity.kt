package com.example.gettingrichapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.gettingrichapp.ui.navigation.AppNavigation
import com.example.gettingrichapp.ui.theme.GettingRichAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GettingRichAppTheme {
                AppNavigation()
            }
        }
    }
}
