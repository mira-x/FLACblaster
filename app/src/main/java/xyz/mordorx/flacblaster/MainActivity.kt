package xyz.mordorx.flacblaster

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.PermissionChecker
import xyz.mordorx.flacblaster.fs.DatabaseSingleton
import xyz.mordorx.flacblaster.fs.ScannerService
import xyz.mordorx.flacblaster.ui.theme.ActiveColorScheme
import xyz.mordorx.flacblaster.ui.theme.FLACblasterTheme
import java.io.File


class MainActivity : ComponentActivity() {
    private var scannerService: ScannerService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            scannerService = (binder as ScannerService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            scannerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSetup()

        if(!bindService(
            Intent(this, ScannerService::class.java),
            connection,
            BIND_AUTO_CREATE
        )) {
            Log.e("MainActivity", "Could not bind ScannerService")
        }

        enableEdgeToEdge()
        setContent {
            FLACblasterTheme {
                Scaffold(modifier = Modifier
                    .fillMaxSize()
                    //.systemBarsPadding()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                ) { innerPadding ->
                    //FileTree(f)
                    Row {
                        Button(onClick = {
                            scannerService?.helloWorld();
                        }) {
                            Text("TRIGGER SCAN")
                        }
                        Button(onClick = {
                            Thread {
                                val dao = DatabaseSingleton.get(applicationContext).fileEntityDao()
                                dao.delete(*dao.getAllFiles().toTypedArray())
                                Log.d("MainActivity", "Deleted all files!")
                            }.start()
                        }) {
                            Text("Clear DB!")
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }

    /**
     * This prompts the user to allow MANAGE_EXTERNAL_STORAGE and to pick a music directory
     */
    private fun appSetup() {
        // Checking for MANAGE_EXTERNAL_STORAGE via selfCheckPermission() is unreliable
        if(!Environment.isExternalStorageManager()) {
            val t = Toast.makeText(this, "This app won't work without full storage access.", Toast.LENGTH_LONG);
            t.show();
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
        if(prefs.getString("RootDirectory", "")!!.isEmpty()) {
            Toast.makeText(this, "You must select a root directory where your music is stored", Toast.LENGTH_LONG).show();

            val folderPicker = registerForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    // Format: "primary:Music" oder "XXXX-XXXX:Music" (SD-Karte)
                    val split = docId.split(":")

                    val path = when (split[0]) {
                        "primary" -> "/storage/emulated/0/${split.getOrElse(1) { "" }}"
                        else -> "/storage/${split[0]}/${split.getOrElse(1) { "" }}"
                    }
                    Log.d("MainActivity", "User picked URI:" + uri.toString() + " which is " + path)
                    prefs.edit().putString("RootDirectory", path).apply()
                }
            }
            folderPicker.launch(null)
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
    val m = Modifier.fillMaxWidth()
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