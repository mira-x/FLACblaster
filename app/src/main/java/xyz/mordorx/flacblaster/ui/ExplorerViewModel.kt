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

class ExplorerViewModel(private val dao: FileEntityDao, rootPath: String) : ViewModel() {
    // This is our internal variable for writing
    private val expandedFoldersMut = MutableStateFlow<Set<String>>(mutableSetOf(rootPath))
    // This is the exported read-only variable
    val expandedFolders: StateFlow<Set<String>> = expandedFoldersMut.asStateFlow()

    private val rootPathFlow = MutableStateFlow(rootPath)

    val allFiles: StateFlow<List<FileEntity>> = dao
        .getAllFilesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleFolder(path: String) {
        expandedFoldersMut.update { current ->
            if (path in current) current - path
            else current + path
        }
    }

    data class TreeItem(
        val file: FileEntity,
        val level: Int,
        val isExpanded: Boolean
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flattenedTree: StateFlow<List<TreeItem>> = rootPathFlow
        .flatMapLatest { rootPath ->
            expandedFolders.flatMapLatest { expanded ->
                allFiles.transform { files ->
                    // Build a map for quick parent-child lookup
                    val childrenMap = files.groupBy { file ->
                        file.path.substringBeforeLast('/', "")
                    }

                    val result = mutableListOf<TreeItem>()

                    fun addItemsRecursively(parentPath: String, level: Int) {
                        val children = childrenMap[parentPath]?.sortedWith(
                            compareBy<FileEntity> { !it.isFolder }
                                .thenBy { it.path }
                        ) ?: emptyList()

                        for (child in children) {
                            val isExpanded = child.path in expanded
                            result.add(TreeItem(child, level, isExpanded))

                            if (child.isFolder && isExpanded) {
                                addItemsRecursively(child.path, level + 1)
                            }
                        }
                    }

                    // Start from root
                    val actualRoot = files.find { it.path == rootPath }
                    if (actualRoot != null) {
                        val isExpanded = actualRoot.path in expanded
                        result.add(TreeItem(actualRoot, 0, isExpanded))
                        if (isExpanded) {
                            addItemsRecursively(actualRoot.path, 1)
                        }
                    }

                    emit(result)
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

}
