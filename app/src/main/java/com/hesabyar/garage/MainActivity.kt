package com.hesabyar.garage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hesabyar.garage.ui.AppViewModel
import com.hesabyar.garage.ui.HesabYarApp
import com.hesabyar.garage.ui.theme.HesabYarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme {
                    val application = applicationContext as HesabYarApplication
                    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(application.container))
                    HesabYarApp(appViewModel)
                }
            }
        }
    }
}
