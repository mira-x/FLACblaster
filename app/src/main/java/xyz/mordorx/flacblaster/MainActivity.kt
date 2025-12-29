package xyz.mordorx.flacblaster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import xyz.mordorx.flacblaster.SuperService.Companion.instantiate
import xyz.mordorx.flacblaster.fs.DatabaseSingleton
import xyz.mordorx.flacblaster.fs.MediaScanMode
import xyz.mordorx.flacblaster.fs.MediaScannerSingleton
import xyz.mordorx.flacblaster.ui.ExplorerViewModel
import xyz.mordorx.flacblaster.ui.AutoViewModelFactory
import xyz.mordorx.flacblaster.ui.MusicPlayerViewModel
import xyz.mordorx.flacblaster.ui.superViewModel
import xyz.mordorx.flacblaster.ui.theme.FLACblasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSetup(this).promptIfNeeded()

        enableEdgeToEdge()
        setContent {
            FLACblasterTheme {
                FileListScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaScannerSingleton.get(this).scanAsync(MediaScanMode.FAST)
    }
}

/**
 * @author https://stackoverflow.com/a/70020301
 */
private fun countDownFlow(
    start: Long,
    delayInSeconds: Long = 1_000L,
) = flow {
    var count = start
    while (count >= 0L) {
        emit(count--)
        delay(delayInSeconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen() {
    val ctx = LocalContext.current
    val rootDirPath = ctx.getSharedPreferences(ctx.packageName, Context.MODE_PRIVATE).getString("RootDirectory", "")!!

    val model = superViewModel {
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
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { scanner.scanAsync(MediaScanMode.CORRECT) },
            state = pullRefreshState,
            modifier = Modifier.padding(padding)
        ) {
            val treeItems by model.flattenedTree.collectAsState()
            LazyColumn(Modifier.fillMaxSize()) {
                item() {
                    val svc by player.svcFlow.collectAsState(initial = null)
                    Text("Player state: ${svc != null}")
                }
                items(treeItems, key = { it.file.path }) { treeItem ->
                    TreeItemRow(treeItem = treeItem, model = model)
                }
            }
        }
    }
}

@Composable
fun TreeItemRow(
    treeItem: ExplorerViewModel.TreeItem,
    model: ExplorerViewModel
) {
    val file = treeItem.file
    val level = treeItem.level
    val isExpanded = treeItem.isExpanded

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (file.isFolder) {
                    model.toggleFolder(file.path)
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val emoji = if (file.isFolder) (if (isExpanded) "📂" else "📁") else ""
        val padding = "  ".repeat(level)
        Text(
            text = padding + emoji + " " + file.getName(),
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = file.durationString() + " ",
            modifier = Modifier.alignByBaseline()
        )
    }
}
