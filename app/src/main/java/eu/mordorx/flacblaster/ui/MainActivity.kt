package eu.mordorx.flacblaster.ui

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import eu.mordorx.flacblaster.R
import eu.mordorx.flacblaster.fs.DatabaseSingleton
import eu.mordorx.flacblaster.fs.MediaScanMode
import eu.mordorx.flacblaster.fs.MediaScannerSingleton
import eu.mordorx.flacblaster.superutil.superViewModel
import eu.mordorx.flacblaster.ui.theme.FLACblasterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

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
