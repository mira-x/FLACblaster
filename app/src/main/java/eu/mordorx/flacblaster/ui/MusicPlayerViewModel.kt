package eu.mordorx.flacblaster.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.mordorx.flacblaster.superutil.SuperService
import eu.mordorx.flacblaster.playback.MusicPlayerService

class MusicPlayerViewModel(val ctx: Context) : ViewModel() {
    public var service: MusicPlayerService? = null
    val svcFlow: StateFlow<MusicPlayerService?>

    init {
        Log.d("MusicPlayerViewModel", "INIT - Creating flow")
        svcFlow = SuperService.instantiate<MusicPlayerService>(ctx)

        viewModelScope.launch {
            Log.d("MusicPlayerViewModel", "Starting to collect flow")
            svcFlow.collect { value ->
                Log.d("MusicPlayerViewModel", "Got svc data: $value")
                service = value
            }
        }
    }
}
