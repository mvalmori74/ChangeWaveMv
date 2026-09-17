package com.changewave.scorch.net

/**
 * Tiene il collegamento Bluetooth vivo fra la schermata di attesa e la partita:
 * un socket non si puo' passare dentro a un Intent.
 */
object NetSession {

    @Volatile
    var link: BluetoothLink? = null

    @Volatile
    var peerName: String = ""

    /** Motivo dell'ultima disconnessione, mostrato quando si torna al menu. */
    @Volatile
    var lastError: String? = null

    fun close() {
        link?.let {
            it.sendBye()
            it.stop()
        }
        link = null
    }

    fun isActive(): Boolean = link?.connected == true
}
