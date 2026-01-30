package eu.mordorx.flacblaster.ui

import android.content.Context
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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

/** @author https://stackoverflow.com/a/54828055 */
fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
    delay(initialDelay)
    while (true) {
        emit(1)
        delay(period)
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
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Bottom app bar",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen() {
    val t = flow {
        delay(3000)
        emit(1)
    }.collectAsState(initial = null)

    val ctx = LocalContext.current
    val rootDirPath = ctx.getSharedPreferences(ctx.packageName, Context.MODE_PRIVATE).getString("RootDirectory", "")!!

    val explorer = superViewModel {
        ExplorerViewModel(
            dao = DatabaseSingleton.get(ctx).fileEntityDao(),
            rootPath = rootDirPath
        )
    }

    val scanner = remember { MediaScannerSingleton.get(ctx) }
    val isScanning by scanner.scanState.collectAsState()
    val progress by scanner.scanStateProgress.collectAsState()
    val label by scanner.scanStateLabel.collectAsState()

    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    val player = superViewModel { MusicPlayerViewModel(ctx) }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            snackbarHostState.showSnackbar(
                message = "Scanning...",
                duration = SnackbarDuration.Indefinite
            )
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                Snackbar {
                    Column {
                        Text(label)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = { TopBar(player) },
        bottomBar = { BottomBar(player) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { scanner.scanAsync(MediaScanMode.CORRECT) },
            state = pullRefreshState,
            modifier = Modifier.padding(padding)
        ) {
            val treeItems by explorer.flattenedTree.collectAsState()
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    val svc by player.svcFlow.collectAsState(initial = null)
                    Text("Player state: ${svc != null}")
                }
                items(treeItems, key = { it.file.path }) { treeItem ->
                    TreeItemRow(treeItem = treeItem, explorer = explorer, player = player)
                }
                if(treeItems.isEmpty() && t.value != null) {
                    item {
                        Text("Loading the music library seems to take longer than normal. Please try to restart the app.")
                    }
                }
            }
        }
    }
}

/** This draws a border but only at the top and the bottom */
fun Modifier.borderHorizontal(color: Color, width: Dp): Modifier {
    return this then Modifier.drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = width.toPx()
        )
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = width.toPx()
        )
    }
}

@Composable
fun TreeItemRow(
    treeItem: ExplorerViewModel.TreeItem,
    explorer: ExplorerViewModel,
    player: MusicPlayerViewModel
) {
    val file = treeItem.file
    val isExpanded = treeItem.isExpanded
    val colors = MaterialTheme.colorScheme
    val bg = if (file.isFolder) colors.surfaceBright else colors.surface
    val fg = if (file.isFolder) colors.onSurfaceVariant else colors.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (file.isFolder) {
                    explorer.toggleFolder(file.path)
                } else {
                    player.service?.player?.apply {
                        this.setMediaItem(MediaItem.fromUri(treeItem.file.getUri()))
                        this.prepare()
                        this.play()
                    }
                }
            }
            .background(bg)
            .borderHorizontal(
                color = colors.outline,
                width = .25.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val (prefix, suffix) = when {
            file.isFolder && isExpanded -> Pair("\\", "/")
            file.isFolder -> Pair("|", "|")
            else -> Pair("", "")
        }
        Text(
            text = "  ".repeat(treeItem.level) + prefix + " " + file.getName(),
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = fg
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = file.durationString() + " " + suffix + " ".repeat(treeItem.level),
            modifier = Modifier.alignByBaseline(),
            color = fg
        )
    }
}
