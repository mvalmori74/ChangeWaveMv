package com.changewave.scorch.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Collegamento Bluetooth RFCOMM fra due dispositivi: uno ospita (server) e uno si
 * collega (client). Legge i pacchetti su un thread dedicato e li consegna al listener.
 */
class BluetoothLink(private val adapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "ScorchBT"
        private const val SERVICE_NAME = "ScorchWave"

        /** UUID dedicato al gioco: deve coincidere sui due dispositivi. */
        val SERVICE_UUID: UUID = UUID.fromString("7b0f2a54-9f0e-4a6f-8d1e-5c3a7e2b1d90")
    }

    interface Listener {
        fun onConnected(peerName: String)
        fun onPacket(packet: Protocol.Packet)
        fun onDisconnected(reason: String)
    }

    private var attached: Listener? = null
    private val backlog = ArrayDeque<Protocol.Packet>()

    /**
     * Ascoltatore corrente. Nel passaggio dalla schermata di attesa alla partita resta
     * scollegato per un istante: i pacchetti arrivati nel frattempo vengono conservati
     * e consegnati appena qualcuno si ricollega.
     */
    var listener: Listener?
        get() = synchronized(backlog) { attached }
        set(value) {
            synchronized(backlog) {
                attached = value
                if (value != null) {
                    while (backlog.isNotEmpty()) value.onPacket(backlog.removeFirst())
                }
            }
        }

    @Volatile
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var output: DataOutputStream? = null
    private var worker: Thread? = null

    @Volatile
    var connected = false
        private set

    @Volatile
    private var closing = false

    // -------------------------------------------------------------- connessione

    /** Resta in ascolto di un avversario che si collega. */
    @SuppressLint("MissingPermission")
    fun host() {
        stop()
        closing = false
        worker = Thread({
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = server
                val s = server.accept()
                try {
                    server.close()
                } catch (e: IOException) {
                    Log.w(TAG, "chiusura server socket", e)
                }
                serverSocket = null
                onSocketReady(s, safeName(s.remoteDevice))
            } catch (e: IOException) {
                fail(if (closing) "" else "Nessun avversario collegato")
            } catch (e: SecurityException) {
                fail("Permesso Bluetooth negato")
            }
        }, "scorch-bt-host").also { it.start() }
    }

    /** Si collega al dispositivo scelto (se non e' accoppiato, Android chiede l'accoppiamento). */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stop()
        closing = false
        worker = Thread({
            try {
                try {
                    adapter.cancelDiscovery()
                } catch (e: SecurityException) {
                    Log.w(TAG, "cancelDiscovery", e)
                }
                val s = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                s.connect()
                onSocketReady(s, safeName(device))
            } catch (e: IOException) {
                fail(if (closing) "" else "Collegamento non riuscito")
            } catch (e: SecurityException) {
                fail("Permesso Bluetooth negato")
            }
        }, "scorch-bt-client").also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String = try {
        device.name ?: device.address ?: "Avversario"
    } catch (e: SecurityException) {
        "Avversario"
    }

    private fun onSocketReady(s: BluetoothSocket, peerName: String) {
        socket = s
        output = DataOutputStream(s.outputStream.buffered())
        connected = true
        listener?.onConnected(peerName)
        readLoop(DataInputStream(s.inputStream.buffered()))
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (connected && !closing) {
                val packet = Protocol.read(input)
                if (packet.type == Protocol.BYE) {
                    fail("L'avversario ha lasciato la partita")
                    return
                }
                deliver(packet)
            }
        } catch (e: IOException) {
            fail(if (closing) "" else "Collegamento interrotto")
        } catch (e: IllegalArgumentException) {
            fail("Dati non validi dall'avversario")
        }
    }

    private fun deliver(packet: Protocol.Packet) {
        synchronized(backlog) {
            val l = attached
            if (l == null) backlog.addLast(packet) else l.onPacket(packet)
        }
    }

    private fun fail(reason: String) {
        val wasConnected = connected
        connected = false
        closeQuietly()
        if (!closing && (wasConnected || reason.isNotEmpty())) listener?.onDisconnected(reason)
    }

    // -------------------------------------------------------------- invio

    /** @return false se il pacchetto non e' partito (collegamento caduto). */
    fun send(type: Int, payload: ByteArray): Boolean {
        val out = output ?: return false
        return try {
            Protocol.write(out, type, payload)
            true
        } catch (e: IOException) {
            fail("Collegamento interrotto")
            false
        }
    }

    fun sendBye() {
        try {
            output?.let { Protocol.write(it, Protocol.BYE, ByteArray(0)) }
        } catch (e: IOException) {
            Log.w(TAG, "invio BYE", e)
        }
    }

    // -------------------------------------------------------------- chiusura

    fun stop() {
        closing = true
        connected = false
        closeQuietly()
        worker = null
    }

    private fun closeQuietly() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "chiusura server", e)
        }
        serverSocket = null
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "chiusura socket", e)
        }
        socket = null
        output = null
    }
}
