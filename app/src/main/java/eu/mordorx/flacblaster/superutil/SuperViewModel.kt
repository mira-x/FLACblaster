package eu.mordorx.flacblaster.superutil

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel


/** This wrapper to viewModel(...) allows you to instantiate a ViewModel using an internally-created factory using the given constructor */
@Composable
inline fun <reified VM : ViewModel> superViewModel(noinline constructor: () -> VM): VM {
    return viewModel(
        factory = AutoViewModelFactory(constructor)
    )
}
