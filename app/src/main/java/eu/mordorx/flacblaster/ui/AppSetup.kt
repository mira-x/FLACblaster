package eu.mordorx.flacblaster.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import eu.mordorx.flacblaster.fs.MediaScanMode
import eu.mordorx.flacblaster.fs.MediaScannerSingleton

/**
 * This prompts the user to allow MANAGE_EXTERNAL_STORAGE and to pick a music directory.
 * Call this before setContent{}
 */
class AppSetup(val caller: ComponentActivity) {

    private val folderPicker: ActivityResultLauncher<Uri?> = caller.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val path = when (split[0]) {
                "primary" -> "/storage/emulated/0/${split.getOrElse(1) { "" }}"
                else -> "/storage/${split[0]}/${split.getOrElse(1) { "" }}"
            }
            Log.d("AppSetup", "User picked: $path")
            val prefs = caller.getSharedPreferences(caller.packageName, Context.MODE_PRIVATE)
            prefs.edit { putString("RootDirectory", path) }
            MediaScannerSingleton.Companion.get(caller).scanAsync(MediaScanMode.CORRECT)
        }
    }

    private val permissionLauncher: ActivityResultLauncher<Intent> = caller.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            requestRootDirectoryIfNeeded()
        } else {
            Toast.makeText(caller, "Storage permission is required!", Toast.LENGTH_LONG).show()
            caller.finish()
        }
    }

    fun promptIfNeeded() {
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(caller, "Storage permission required", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                ("package:${caller.packageName}").toUri()
            )
            permissionLauncher.launch(intent)
        } else {
            requestRootDirectoryIfNeeded()
        }
    }

    private fun requestRootDirectoryIfNeeded() {
        val prefs = caller.getSharedPreferences(caller.packageName, Context.MODE_PRIVATE)
        if (prefs.getString("RootDirectory", "")!!.isNotEmpty()) {
            return
        }

        Toast.makeText(caller, "You must select a music root directory", Toast.LENGTH_LONG).show()
        folderPicker.launch(null)
    }
}