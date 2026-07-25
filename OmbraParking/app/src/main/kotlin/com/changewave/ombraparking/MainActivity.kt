package com.changewave.ombraparking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.changewave.ombraparking.ui.OmbraParkingApp
import com.changewave.ombraparking.ui.theme.OmbraParkingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmbraParkingTheme {
                OmbraParkingApp()
            }
        }
    }
}
