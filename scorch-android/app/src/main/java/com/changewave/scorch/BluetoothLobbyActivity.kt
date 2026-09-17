package com.changewave.scorch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.changewave.scorch.game.GameSettings
import com.changewave.scorch.net.BluetoothLink
import com.changewave.scorch.net.NetSession
import com.changewave.scorch.net.Protocol
import kotlin.random.Random

/**
 * Schermata di collegamento per la partita a due via Bluetooth: un dispositivo ospita,
 * l'altro sceglie l'avversario dall'elenco. Al termine dell'handshake parte la partita.
 */
class BluetoothLobbyActivity : AppCompatActivity(), BluetoothLink.Listener {

    companion object {
        const val EXTRA_SETTINGS = "settings"
        private const val DISCOVERABLE_SECONDS = 180
    }

    private var adapter: BluetoothAdapter? = null
    private var link: BluetoothLink? = null
    private lateinit var baseSettings: GameSettings

    private lateinit var status: TextView
    private lateinit var hostButton: Button
    private lateinit var scanButton: Button
    private lateinit var deviceList: ListView
    private lateinit var listAdapter: ArrayAdapter<String>

    private val devices = ArrayList<BluetoothDevice>()
    private var isHost = false
    private var helloSent = false
    private var remoteName = ""
    private var launched = false
    private var discovering = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { !it }) {
            toast("Senza permesso Bluetooth non si puo' giocare in due")
        }
        refreshPaired()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshPaired() }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { startHosting() }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addDevice(it, discovered = true) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    discovering = false
                    scanButton.text = "Cerca avversari"
                    if (!isHost) status.text = "Scegli l'avversario dall'elenco"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        baseSettings = intentSettings()
        status = findViewById(R.id.statusText)
        hostButton = findViewById(R.id.hostButton)
        scanButton = findViewById(R.id.scanButton)
        deviceList = findViewById(R.id.deviceList)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        deviceList.adapter = listAdapter
        deviceList.setOnItemClickListener { _, _, position, _ ->
            devices.getOrNull(position)?.let { joinDevice(it) }
        }

        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            status.text = "Questo dispositivo non ha il Bluetooth"
            hostButton.isEnabled = false
            scanButton.isEnabled = false
            return
        }

        hostButton.setOnClickListener { hostGame() }
        scanButton.setOnClickListener { startDiscovery() }

        registerReceiver(
            discoveryReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        )

        requestPermissionsIfNeeded()
    }

    private fun intentSettings(): GameSettings {
        val fromIntent: GameSettings? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_SETTINGS, GameSettings::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SETTINGS)
        }
        return fromIntent ?: GameSettings()
    }

    // ---------------------------------------------------------------- permessi

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNeeded() {
        if (hasPermissions()) refreshPaired() else permissionLauncher.launch(requiredPermissions())
    }

    @SuppressLint("MissingPermission")
    private fun ensureBluetoothOn(): Boolean {
        val a = adapter ?: return false
        if (!a.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return false
        }
        return true
    }

    // ---------------------------------------------------------------- elenco dispositivi

    @SuppressLint("MissingPermission")
    private fun refreshPaired() {
        if (!hasPermissions()) return
        val a = adapter ?: return
        if (!a.isEnabled) {
            status.text = "Accendi il Bluetooth per giocare in due"
            return
        }
        try {
            for (device in a.bondedDevices.orEmpty()) addDevice(device, discovered = false)
        } catch (e: SecurityException) {
            toast("Permesso Bluetooth mancante")
        }
        if (!isHost) {
            status.text = if (devices.isEmpty()) {
                "Nessun dispositivo accoppiato: premi «Cerca avversari»"
            } else {
                "Scegli l'avversario, oppure ospita tu la partita"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice, discovered: Boolean) {
        if (devices.any { it.address == device.address }) return
        devices.add(device)
        val name = try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        }
        listAdapter.add(if (discovered) "$name  ·  nuovo" else "$name  ·  accoppiato")
        listAdapter.notifyDataSetChanged()
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (!hasPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (!ensureBluetoothOn()) return
        val a = adapter ?: return
        try {
            if (discovering) {
                a.cancelDiscovery()
                discovering = false
                scanButton.text = "Cerca avversari"
                return
            }
            refreshPaired()
            discovering = a.startDiscovery()
            if (discovering) {
                scanButton.text = "Interrompi ricerca"
                status.text = "Ricerca in corso… l'altro telefono deve essere in «Ospita»"
            }
        } catch (e: SecurityException) {
            toast("Permesso di ricerca mancante")
        }
    }

    // ---------------------------------------------------------------- collegamento

    private fun newLink(): BluetoothLink {
        NetSession.close()
        val l = BluetoothLink(adapter!!)
        l.listener = this
        link = l
        NetSession.link = l
        return l
    }

    @SuppressLint("MissingPermission")
    private fun hostGame() {
        if (!hasPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (!ensureBluetoothOn()) return
        isHost = true
        val request = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        }
        discoverableLauncher.launch(request)
    }

    private fun startHosting() {
        isHost = true
        hostButton.isEnabled = false
        scanButton.isEnabled = false
        status.text = "In attesa dell'avversario…"
        newLink().host()
    }

    @SuppressLint("MissingPermission")
    private fun joinDevice(device: BluetoothDevice) {
        if (!ensureBluetoothOn()) return
        isHost = false
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            // ignorato: la ricerca si ferma comunque alla connessione
        }
        discovering = false
        hostButton.isEnabled = false
        scanButton.isEnabled = false
        status.text = "Collegamento in corso…"
        newLink().connect(device)
    }

    @SuppressLint("MissingPermission")
    private fun localName(): String = try {
        adapter?.name ?: Build.MODEL
    } catch (e: SecurityException) {
        Build.MODEL
    }

    // ---------------------------------------------------------------- listener

    override fun onConnected(peerName: String) {
        runOnUiThread {
            remoteName = peerName
            NetSession.peerName = peerName
            status.text = "Collegato a $peerName"
        }
        if (!helloSent) {
            helloSent = true
            link?.send(Protocol.HELLO, Protocol.encodeHello(localName()))
        }
    }

    override fun onPacket(packet: Protocol.Packet) {
        when (packet.type) {
            Protocol.HELLO -> {
                val (version, name) = Protocol.decodeHello(packet.payload)
                remoteName = name
                NetSession.peerName = name
                runOnUiThread { status.text = "Avversario: $name" }
                if (version != Protocol.VERSION) {
                    runOnUiThread {
                        status.text = "Versioni del gioco diverse: aggiornate entrambi i telefoni"
                    }
                    NetSession.close()
                    return
                }
                if (isHost) {
                    val settings = baseSettings.copy(
                        playerCount = 2,
                        humanCount = 2,
                        seed = Random.nextLong(),
                        localPlayerIndex = 0,
                        isHost = true,
                        remoteName = name
                    )
                    link?.send(Protocol.START, Protocol.encodeStart(settings))
                    launchGame(settings)
                }
            }

            Protocol.START -> {
                if (isHost) return
                val settings = Protocol.decodeStart(packet.payload, localPlayerIndex = 1, remoteName = remoteName)
                launchGame(settings)
            }
        }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread {
            if (launched) return@runOnUiThread
            hostButton.isEnabled = true
            scanButton.isEnabled = true
            isHost = false
            helloSent = false
            status.text = if (reason.isEmpty()) "Collegamento chiuso" else reason
        }
    }

    private fun launchGame(settings: GameSettings) {
        if (launched) return
        launched = true
        runOnUiThread {
            // il listener passa alla partita: i pacchetti nel frattempo restano in coda
            link?.listener = null
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra(GameActivity.EXTRA_SETTINGS, settings)
            startActivity(intent)
            finish()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // gia' deregistrato
        }
        try {
            if (discovering) adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            // niente da fare
        }
        if (!launched) NetSession.close()
    }
}
