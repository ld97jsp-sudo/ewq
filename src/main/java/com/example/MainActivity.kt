package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.IPTVDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.XtreamViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: XtreamViewModel = viewModel()
                
                // Collect preferred screen orientation and dynamically apply to Activity
                val appOri by viewModel.appOrientation.collectAsState()
                LaunchedEffect(appOri) {
                    viewModel.applyOrientation(this@MainActivity, appOri)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1016)
                ) {
                    IPTVDashboard(viewModel = viewModel)
                }
            }
        }
    }
}
