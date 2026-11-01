package com.signa.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SignaRoot() } }
    }
}

private enum class Tab(val label: String) {
    DRIVE("Lái xe"),
    MAP("Bản đồ"),
    SETTINGS("Cài đặt"),
}

@Composable
private fun SignaRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.DRIVE) }
    var camera by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        camera = result[Manifest.permission.CAMERA] == true
        // Tốc độ và vị trí biển cần GPS, vị trí gần đúng không đủ
        location = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        // Android 12+ bỏ qua yêu cầu FINE nếu không xin kèm COARSE
        permissions.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.DRIVE -> if (camera) DriveScreen(location) else Text("Cần quyền camera để nhận diện biển báo.", Modifier.padding(24.dp))
                Tab.MAP -> MapScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
