package com.reals.app.foreground

class ForegroundDestinationLifecyclePublisher(
    private val registration: ForegroundDestinationRegistration,
) {
    private var resumed = false
    private var destination: ForegroundDestination? = null

    fun onResume() {
        resumed = true
        registration.publish(destination)
    }

    fun onDestinationChanged(destination: ForegroundDestination?) {
        this.destination = destination
        if (resumed) {
            registration.publish(destination)
        }
    }

    fun onPause() {
        resumed = false
        registration.clear()
    }

    fun onStop() {
        resumed = false
        registration.clear()
    }

    fun onDispose() {
        resumed = false
        registration.dispose()
    }
}
