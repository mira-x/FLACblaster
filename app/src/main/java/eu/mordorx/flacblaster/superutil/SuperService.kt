package eu.mordorx.flacblaster.superutil

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * This is a helper class that you can inherit your services from. It provides helpful utils for
 * instantiating the service and saves you from boilerplate code. Use the instantiate() function to
 * receive a flow containing the service (or null if not ready yet or after it crashed)
 */
abstract class SuperService : Service() {
    companion object {
        /**
         * Create a binding to the service T and return its service object through a flow.
         */
        inline fun <reified T: SuperService>instantiate(ctx: Context): StateFlow<T?> {
            Log.d("SuperService", "instantiate called for ${T::class.java.simpleName}")
            /** Use applicationContext to prevent Activity context leaks.
             The ServiceConnection and its closures can outlive the calling Activity. */
            val appCtx = ctx.applicationContext
            val f = MutableStateFlow<T?>(null)

            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    Log.d("SuperService", "onServiceConnected: $name, binder: $binder")
                    @Suppress("UNCHECKED_CAST")
                    val service = (binder as SuperServiceBinder).service as T
                    Log.d("SuperService", "Emitting service: $service")
                    f.value = service
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.d("SuperService", "onServiceDisconnected: $name")
                    f.value = null
                    appCtx.unbindService(this)
                }
            }

            val intent = Intent(appCtx, T::class.java)
            Log.d("SuperService", "Binding service with intent: $intent")
            val bindResult = appCtx.bindService(intent, conn, BIND_AUTO_CREATE)
            Log.d("SuperService", "bindService result: $bindResult")

            return f
        }
    }

    inner class SuperServiceBinder : Binder() {
        val service: SuperService
            get() = this@SuperService
    }
    val binder = SuperServiceBinder()
    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}