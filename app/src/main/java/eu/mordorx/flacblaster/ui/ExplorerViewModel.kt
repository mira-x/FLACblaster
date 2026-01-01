package eu.mordorx.flacblaster.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import eu.mordorx.flacblaster.fs.FileEntity
import eu.mordorx.flacblaster.fs.FileEntityDao
import kotlinx.coroutines.flow.first

class ExplorerViewModel(private val dao: FileEntityDao, private val rootPath: String) : ViewModel() {
    // This is our internal variable for writing
    private val expandedFoldersMut = MutableStateFlow<Set<String>>(mutableSetOf(rootPath))
    // This is the exported read-only variable
    val expandedFolders: StateFlow<Set<String>> = expandedFoldersMut.asStateFlow()

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

    /**
     * Lazy-loaded tree structure that only loads visible items from the database.
     *
     * This lambda loads the data, and passes it to buildTreeLazy() to build the tree objects.
     *
     * The flow automatically updates when:
     * - Folders are expanded/collapsed (via expandedFolders)
     * - Database content changes (via Room's Flow support)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val flattenedTree: StateFlow<List<TreeItem>> = expandedFolders
        .flatMapLatest { expanded ->
            // Step 1: Fetch the lastest list of expanded folders, then load the root directory entity
            val rootEntity = dao.getFlowByPath(rootPath).first()

            if (rootEntity == null) {
                Log.w("ExplorerViewModel", "Root entity not found: $rootPath")
                return@flatMapLatest flowOf(emptyList<TreeItem>())
            }

            if (rootPath !in expanded) {
                return@flatMapLatest flowOf(listOf(TreeItem(rootEntity, 0, false)))
            }

            val foldersToLoad = expanded.toList()
            val start = System.currentTimeMillis()

            // Step 2: For each expanded folder, load only its sorted direct children from DB.
            // The lambda below is not a loop, but a terminal operation that is executed once all flows are loaded. Correct ordering is guaranteed.
            return@flatMapLatest combine(foldersToLoad.map(dao::getDirectChildren)) { childrenArrays ->
                // Step 3: Build a map of folder path -> children for quick lookup
                val childrenMap = foldersToLoad.indices.associate { i -> foldersToLoad[i] to childrenArrays[i] }

                // Step 4: Build the flattened tree structure recursively
                // Only includes items that are actually visible (root + expanded children)
                val result = buildTreeLazy(rootEntity, expanded, childrenMap)
                val end = System.currentTimeMillis()
                Log.d("ExplorerViewModel", "flattenedTree built (${result.size} items) in ${end-start}ms")
                return@combine result
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    /**
     * Builds a flattened tree structure from the given entity and its children.
     *
     * This function is used by the flattenedTree lambda. That lambda fetches the data, and this
     * function is used to create a tree out of it.
     *
     * @param rootEntity The root entity to start building from
     * @param expanded Set of folder paths that are currently expanded
     * @param childrenMap Pre-loaded map of folder path -> list of direct children (already sorted by DB)
     * @return Flattened list of TreeItem ready for LazyColumn rendering
     * @author Claude
     */
    private fun buildTreeLazy(
        rootEntity: FileEntity,
        expanded: Set<String>,
        childrenMap: Map<String, List<FileEntity>>
    ): List<TreeItem> {
        // This function is NOT recursive. However, it uses the recursive, depth-first function addRecursively.
        val result = mutableListOf<TreeItem>()

        fun addRecursively(item: FileEntity, lvl: Int) {
            val isExpanded = item.path in expanded
            result.add(TreeItem(item, lvl, isExpanded))
            Log.d("ExplorerViewModel", "buildTreeLazy: Added ${item.getName()} at level $lvl (expanded=$isExpanded)")

            // Only recurse into folders that are actually expanded.
            // Collapsed folders are shown but their children are skipped.
            if (isExpanded && item.isFolder) {
                val children = childrenMap[item.path] ?: emptyList()
                Log.d("ExplorerViewModel", "buildTreeLazy: Processing ${children.size} children of ${item.getName()}")
                children.forEach { child ->
                    addRecursively(child, lvl + 1)
                }
            }
        }

        addRecursively(rootEntity, 0)
        Log.d("ExplorerViewModel", "buildTreeLazy: Built tree with ${result.size} total items")
        return result
    }

}
