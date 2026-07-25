package com.changewave.ombraparking

import android.app.Application
import android.content.Context
import com.changewave.ombraparking.data.ObstacleRepository
import com.changewave.ombraparking.data.ParkedCarStore
import java.io.File
import org.osmdroid.config.Configuration

/**
 * Punto di partenza dell'app: configura osmdroid e tiene le poche dipendenze condivise.
 * Il progetto è piccolo, un contenitore manuale basta e avanza rispetto a un framework di DI.
 */
class OmbraParkingApplication : Application() {

    /** Cache degli ostacoli condivisa fra mappa e realtà aumentata. */
    val obstacleRepository: ObstacleRepository by lazy { ObstacleRepository(userAgent = userAgent()) }

    /** Posto auto salvato, condiviso dalle due viste. */
    val parkedCarStore: ParkedCarStore by lazy { ParkedCarStore(this) }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
    }

    /**
     * osmdroid va configurato prima di creare la prima MapView. Le tile finiscono nella
     * cache interna dell'app, così non serve alcun permesso sullo storage.
     */
    private fun configureOsmdroid() {
        val configuration = Configuration.getInstance()
        configuration.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // Le regole d'uso dei tile server OSM chiedono uno user agent identificabile.
        configuration.userAgentValue = userAgent()
        configuration.osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        configuration.osmdroidTileCache = File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
    }

    private fun userAgent(): String = "OmbraParking/${BuildConfig.VERSION_NAME} ($packageName)"
}
