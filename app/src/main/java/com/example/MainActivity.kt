package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.firebase.configureAppCheck
import com.example.ui.StreetLingoApp
import com.example.ui.theme.StreetLingoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase operations.
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      StreetLingoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          StreetLingoApp()
        }
      }
    }
  }
}
