package ch.alpmann.flacblaster.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import ch.alpmann.flacblaster.R
import ch.alpmann.flacblaster.fs.DatabaseSingleton
import ch.alpmann.flacblaster.fs.MediaScanMode
import ch.alpmann.flacblaster.fs.MediaScannerSingleton
import ch.alpmann.flacblaster.ui.theme.FLACblasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = System.currentTimeMillis()
        DatabaseSingleton.get(this)
        Log.d("MainActivity", "DB init: ${System.currentTimeMillis() - start}ms")

        AppSetup(this).promptIfNeeded()


        enableEdgeToEdge()
        setContent {
            FLACblasterTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true
                ) {
                    Log.d("MainActivity", "Recomposing")
                    FileListScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaScannerSingleton.get(this).scanAsync(MediaScanMode.FAST)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(player: MusicPlayerViewModel) {
    TopAppBar(
        colors = topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text("Top app bar")
        }
    )
}

@Composable
fun BottomBar(player: MusicPlayerViewModel) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(painter = painterResource(R.drawable.subdirectory_arrow_right), contentDescription = "scroll song into view", modifier = Modifier.fillMaxSize(.75f).weight(1f).clickable(onClick = {

        }))
        Icon(painter = painterResource(R.drawable.fast_backward), contentDescription = "backward", modifier = Modifier.fillMaxSize(.75f).weight(1f).clickable(onClick = {

        }))
        Icon(painter = painterResource(R.drawable.play_pause), contentDescription = "play", modifier = Modifier.fillMaxSize(.75f).weight(1f).clickable(onClick = {
            player.service?.apply {
                if(this.isPlaying()) {
                    this.pause()
                } else {
                    this.play()
                }
            }
        }))
        Icon(painter = painterResource(R.drawable.fast_forward), contentDescription = "forward", modifier = Modifier.fillMaxSize(.75f).weight(1f).clickable(onClick = {

        }))
        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = "more", modifier = Modifier.fillMaxSize(.75f).weight(1f).clickable(onClick = {

        }))
    }
}
