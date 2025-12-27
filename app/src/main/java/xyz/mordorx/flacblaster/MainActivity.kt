package xyz.mordorx.flacblaster

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.mordorx.flacblaster.fs.MediaScannerSingleton
import xyz.mordorx.flacblaster.ui.theme.ActiveColorScheme
import xyz.mordorx.flacblaster.ui.theme.FLACblasterTheme
import java.io.File
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import xyz.mordorx.flacblaster.fs.DatabaseSingleton
import xyz.mordorx.flacblaster.fs.FileEntity
import xyz.mordorx.flacblaster.fs.MediaScanMode


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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen() {
    val ctx = LocalContext.current
    val scanner = remember { MediaScannerSingleton.get(ctx) }
    val isScanning by scanner.scanState.collectAsState()
    val progress by scanner.scanStateProgress.collectAsState()
    val label by scanner.scanStateLabel.collectAsState()

    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    val rootDirPath = ctx.getSharedPreferences(ctx.packageName, Context.MODE_PRIVATE).getString("RootDirectory", "")!!

    val allFiles by DatabaseSingleton.get(ctx)
        .fileEntityDao()
        .getAllFilesFlow()
        .collectAsState(initial = emptyList())

    val rootDir = allFiles.find { it.path == rootDirPath }


    //val rootDir by DatabaseSingleton.get(ctx).fileEntityDao().getFlowByPath(rootDirPath).collectAsState(initial = null)
    Log.i("MainActivity", "rootDirPath is $rootDirPath, rootDir is $rootDir")

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
            LazyColumn(Modifier.fillMaxSize()) {
                // Tree items
                item {
                    rootDir?.let { FolderViewTree(it) }
                }
            }
        }
    }
}

@Composable
fun TextPadding(level: Int) {
    Text("    ".repeat(level))
}

@Composable
fun FolderViewTree(folder: FileEntity, level: Int = 0) {
    val db = DatabaseSingleton.get(LocalContext.current).fileEntityDao()
    val children by db.getDirectChildren(folder.path).collectAsState(initial = null)

    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(4.dp)
    ) {
        TextPadding(level)
        Text(if (isExpanded) "📂" else "📁")
        Spacer(Modifier.width(8.dp))
        Text(folder.getName())
    }

    // Kinder wenn expanded
    if (isExpanded) {
        children?.forEach { child ->
            if (child.isFolder) {
                FolderViewTree(child, level + 1)
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                ) {
                    TextPadding(level + 1)
                    Text("🎵")
                    Spacer(Modifier.width(8.dp))
                    Text(child.getName())
                }
            }
        }
    }
}
