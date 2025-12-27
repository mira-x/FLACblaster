package xyz.mordorx.flacblaster.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import xyz.mordorx.flacblaster.fs.FileEntity
import xyz.mordorx.flacblaster.fs.FileEntityDao

class ExplorerViewModel(private val dao: FileEntityDao) : ViewModel() {
    // This is our internal variable for writing
    private val expandedFoldersMut = MutableStateFlow<Set<String>>(emptySet())
    // This is the exported read-only variable
    val expandedFolders: StateFlow<Set<String>> = expandedFoldersMut.asStateFlow()

    val allFiles: StateFlow<List<FileEntity>> = dao
        .getAllFilesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleFolder(path: String) {
        expandedFoldersMut.update { current ->
            if (path in current) current - path
            else current + path
        }
    }


    private val childrenCache = mutableMapOf<String, StateFlow<List<FileEntity>>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getChildrenCached(folderPath: String): StateFlow<List<FileEntity>> {
        // If the folder row changes (e.g. last modified date) we re-query this folder's children
        return childrenCache.getOrPut(folderPath) {
            dao.getFlowByPath(folderPath)
                .flatMapLatest { folder ->
                    if (folder == null) {
                        flowOf(emptyList())
                    } else {
                        dao.getDirectChildren(folderPath)
                    }
                }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Lazily,
                    emptyList()
                )
        }
    }

}
