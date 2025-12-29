package xyz.mordorx.flacblaster.superutil

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * This allows you to create a ViewModel Factory without having to write a custom class definition.
 * See also superViewModel(), a wrapper for this class.
 *
 * If you want to use/create a ViewModel, Android requires you to create a Factory so the Android System can instantiate the viewModel itself. This is bothersome and can be circumvented using this class.
 */
class AutoViewModelFactory<T : ViewModel>(val constructor: () -> T) : ViewModelProvider.Factory {
    override fun <T2 : ViewModel> create(modelClass: Class<T2>): T2 {
        @Suppress("UNCHECKED_CAST")
        return constructor() as T2
    }
}
