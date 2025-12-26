package xyz.mordorx.flacblaster

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import xyz.mordorx.flacblaster.fs.DatabaseSingleton


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSetup()

        enableEdgeToEdge()
        setContent {
            FLACblasterTheme {
                FileListScreen()
            }
        }
    }

    /**
     * This prompts the user to allow MANAGE_EXTERNAL_STORAGE and to pick a music directory
     */
    private fun appSetup() {
        if(!Environment.isExternalStorageManager()) {
            val t = Toast.makeText(this, "This app won't work without full storage access.", Toast.LENGTH_LONG)
            t.show()
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                ("package:$packageName").toUri()
            )
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
        if(prefs.getString("RootDirectory", "")!!.isEmpty()) {
            Toast.makeText(this, "You must select a root directory where your music is stored", Toast.LENGTH_LONG).show()

            val folderPicker = registerForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    // Format: "primary:Music" or "XXXX-XXXX:Music" (SD card)
                    val split = docId.split(":")

                    val path = when (split[0]) {
                        "primary" -> "/storage/emulated/0/${split.getOrElse(1) { "" }}"
                        else -> "/storage/${split[0]}/${split.getOrElse(1) { "" }}"
                    }
                    Log.d("MainActivity", "User picked URI: $uri which is $path")
                    prefs.edit {putString("RootDirectory", path)}
                }
            }
            folderPicker.launch(null)
        }
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
            onRefresh = { scanner.scanAsync() },
            state = pullRefreshState,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("Welcome to FLACblaster!")
                }
                item {
                    Text("Swipe down to re-scan your music library!")
                }
            }
        }
    }
}

@Composable
fun FileTree(f: File) {
    if(f.isFile) {
        Leaf(f.name)
    } else if (f.isDirectory) {
        Branch(f.name) {
            f.listFiles()?.forEach { sub ->
                FileTree(sub)
            }
        }
    }
}

@Composable
fun TreeColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier
        .fillMaxWidth()
        .drawBehind {
            val strokeWidth = 1f
            val y = size.height - strokeWidth / 2

            drawLine(
                ActiveColorScheme.onBackground,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth
            )
        }) {
        content()
    }
}

@Composable
fun Branch(label: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(true) }
    TreeColumn {
        TreeColumn (Modifier
            .fillMaxWidth()
            .clickable(true, onClick = { open = !open })
            .background(ActiveColorScheme.primary)) {
            Row {
                Text(label, color = ActiveColorScheme.onPrimary)
                Spacer(Modifier.weight(1f))
                Text(if (open) "v" else "<", color = ActiveColorScheme.onPrimary)
            }
        }
        var m = Modifier
            .fillMaxWidth()
            .width(1000.dp)
            .padding(start = 10.dp)
        if (!open) { m = m
            .size(0.dp)
            .alpha(1f) }
        TreeColumn(m) {
            content()
        }
    }
}

@Composable
fun Leaf(label: String) {
    Column(Modifier
        .fillMaxWidth()
        .background(ActiveColorScheme.secondary)) {
        Text(label, color = ActiveColorScheme.onSecondary)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Modifier.fillMaxWidth()
    Branch("OSes") {
        Branch("Unix") {
            Branch("Linux") {
                Leaf("Android")
                Leaf("Linux")
                Leaf("WSL")
            }
            Branch("BSD") {
                Leaf("NetBSD")
                Leaf("MacOS X")
                Leaf("FreeBSD")
            }
        }
        Branch("DOS") {
            Branch("Windows") {
                Branch("Windows NT") {
                    Leaf("Win11")
                    Leaf("Win10")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FLACblasterTheme {
        Greeting("Android")
    }
}