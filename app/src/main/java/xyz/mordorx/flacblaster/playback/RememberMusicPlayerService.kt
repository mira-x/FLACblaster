package xyz.mordorx.flacblaster.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
/*
@Composable
fun rememberMusicPlayerService(): MusicPlayerService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<MusicPlayerService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val musicBinder = binder as MusicPlayerService.MusicPlayerServiceBinder
                service = musicBinder.service
                isBound = true
                Log.d("Compose", "Service connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                isBound = false
                Log.d("Compose", "Service disconnected")
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, MusicPlayerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            if (isBound) {
                context.unbindService(serviceConnection)
            }
        }
    }

    return service
}*/