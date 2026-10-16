package io.github.bijojp.thingspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.ThingSpotApp
import io.github.bijojp.thingspot.ui.theme.ThingSpotTheme

class MainActivity : ComponentActivity() {
    private val viewModel: InventoryViewModel by viewModels {
        viewModelFactory {
            initializer { InventoryViewModel((application as ThingSpotApplication).container.inventory) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThingSpotTheme {
                ThingSpotApp(viewModel)
            }
        }
    }
}
