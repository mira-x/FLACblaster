package eu.mordorx.flacblaster.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.mordorx.flacblaster.fs.DatabaseSingleton
import eu.mordorx.flacblaster.fs.FileEntity
import eu.mordorx.flacblaster.fs.FileEntityDao
import eu.mordorx.flacblaster.fs.FileEntityDao_Impl
import eu.mordorx.flacblaster.fs.MediaScanMode
import eu.mordorx.flacblaster.fs.MediaScannerSingleton
import eu.mordorx.flacblaster.superutil.superViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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

    // load last played file selection and expand its parent folders
    val initialSelection by DatabaseSingleton.get(ctx).fileEntityDao().getSelectionFlow().timeout(Duration.parse("250ms")).take(1).collectAsState(null)
    if (initialSelection != null) {
        explorer.expandFile(File(initialSelection!!.path))
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

@Preview
@Composable
fun TreeItemRowPreview() {
    val f = FileEntity.emptyOfDummy("/Musik/podcast123.opus")
    f.isPodcast = true
    f.lastResumeMs = 750000
    f.durationMs = 1900000
    val dir = FileEntity.emptyOfDummy("/Musik/")
    dir.durationMs = 1234567890
    val itm1 = ExplorerViewModel.TreeItem(dir, 0, true)
    val itm2 = ExplorerViewModel.TreeItem(f, 1, false)
    Column {
        TreeItemRow(itm1, null, null)
        TreeItemRow(itm2, null, null)
    }
}

/**
 * A whole tree item row with all extras included.
 *
 * View Models are optional to allow for previews.
 */
@Composable
fun TreeItemRow(
    treeItem: ExplorerViewModel.TreeItem,
    explorer: ExplorerViewModel?,
    player: MusicPlayerViewModel?
) {
    val f = treeItem.file
    val colors = MaterialTheme.colorScheme
    val bg = if (f.isFolder) colors.surfaceBright else colors.surface
    val fg = if (f.isFolder) colors.onSurfaceVariant else colors.onSurface

    Box(Modifier
        .fillMaxWidth()
        .clickable {
            if (f.isFolder) {
                explorer?.toggleFolder(f.path)
            } else {
                player?.service?.play(f)
            }
        }
        .background(bg)
        .borderHorizontal(
            color = colors.outline,
            width = .25.dp
        )
    ) {
        // TODO: Re-add podcast check
        //if (f.isPodcast) {
            PlaybackIndicator(bg) { f.lastResumeMs.toFloat() / f.durationMs.toFloat() }
        //}

        AudioFileInfo(treeItem, fg)
    }
}

/**
 * Draw a thin red line at the box bottom that indicates how far the audio file has been played. Inspired by the thin line at the bottom of YouTube thumbnails that indicate how far you've watched a video.
 */
@Composable
private fun BoxScope.PlaybackIndicator(backgroundColor: Color, progress: () -> Float) {
    LinearProgressIndicator(
        color = Color.Red,
        trackColor = backgroundColor,
        strokeCap = StrokeCap.Square,
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(-10f).height(2.dp),
        progress = progress,
        drawStopIndicator = {}
    )
}

@Composable
private fun AudioFileInfo(itm: ExplorerViewModel.TreeItem, textColor: Color) {
    val f = itm.file
    val isExpanded = itm.isExpanded

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.zIndex(10f).fillMaxWidth()) {
        val (prefix, suffix) = when {
            f.isFolder && isExpanded -> Pair("\\", "/")
            f.isFolder -> Pair("|", "|")
            !f.isFolder && f.isSelected -> Pair(">", "")
            else -> Pair("", "")
        }
        Text(
            text = "  ".repeat(itm.level) + prefix + " " + f.getName(),
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textColor
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = f.durationString() + " " + suffix + " ".repeat(itm.level),
            modifier = Modifier.alignByBaseline(),
            color = textColor
        )
    }
}
