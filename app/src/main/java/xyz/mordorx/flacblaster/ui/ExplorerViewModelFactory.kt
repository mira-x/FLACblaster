package xyz.mordorx.flacblaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import xyz.mordorx.flacblaster.fs.FileEntityDao

/** This class is necessary because we do not instantiate our ExplorerViewModel ourselves, but Android has to do it for us */
class ExplorerViewModelFactory(
    private val dao: FileEntityDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExplorerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExplorerViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}