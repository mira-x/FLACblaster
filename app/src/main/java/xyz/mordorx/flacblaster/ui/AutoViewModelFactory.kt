package xyz.mordorx.flacblaster.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/** This allows you to create a ViewModel Factory without having to write boilerplate, i.e. an actual class definition! This is because Android requires a Factory class to instantiate a ViewModel, because it wants to do it for you. */
class AutoViewModelFactory<T : ViewModel>(val constructor: () -> T) : ViewModelProvider.Factory {
    override fun <T2 : ViewModel> create(modelClass: Class<T2>): T2 {
        @Suppress("UNCHECKED_CAST")
        return constructor() as T2
    }
}

/** This wrapper to viewModel(...) allows you to instantiate a ViewModel without a factory */
@Composable
inline fun <reified VM : ViewModel> superViewModel(noinline constructor: () -> VM): VM {
    return viewModel(
        factory = AutoViewModelFactory(constructor)
    )
}
