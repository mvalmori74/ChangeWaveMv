package com.changewave.dungeon.cli

import com.changewave.dungeon.audio.AmbientMusicEngine
import com.changewave.dungeon.audio.MusicDirector
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Esporta in file WAV la musica generata dal motore, usando esattamente lo stesso
 * codice che gira sull'app Android. Serve per ascoltare il risultato senza
 * installare l'APK e per confrontare le versioni fra una modifica e l'altra.
 *
 *   ./gradlew :core:runMusicExport --args="--out /tmp/musica --seconds 45"
 *   ./gradlew :core:runMusicExport --args="--out /tmp/musica --depths 1,5,10 --descent"
 */
object MusicExport {

    @JvmStatic
    fun main(args: Array<String>) {
        var outDir = "musica"
        var seconds = 40.0
        var depths = listOf(1, 4, 7, 10)
        var descent = false
        var seed = 0x0D06F00DL

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--out" -> args.getOrNull(i + 1)?.let { outDir = it; i++ }
                "--seconds" -> args.getOrNull(i + 1)?.toDoubleOrNull()?.let { seconds = it; i++ }
                "--depths" -> args.getOrNull(i + 1)?.let { spec ->
                    depths = spec.split(",").mapNotNull { it.trim().toIntOrNull() }
                    i++
                }
                "--seed" -> args.getOrNull(i + 1)?.toLongOrNull()?.let { seed = it; i++ }
                "--descent" -> descent = true
                "--help" -> { printHelp(); return }
            }
            i++
        }

        val directory = File(outDir).apply { mkdirs() }

        for (depth in depths) {
            val engine = AmbientMusicEngine(seed = seed, depth = depth)
            engine.masterVolume = 0.85
            val file = File(directory, "dungeon-livello-%02d.wav".format(depth))
            writeWav(file, engine, seconds)
            println("${file.name}  ${MusicDirector.describe(depth)}")
        }

        if (descent) {
            // Traccia dimostrativa: scende di un livello ogni `seconds / 10` secondi,
            // cosi' si sente la transizione continua e non solo i punti estremi.
            val engine = AmbientMusicEngine(seed = seed, depth = 1)
            engine.masterVolume = 0.85
            val file = File(directory, "dungeon-discesa-completa.wav")
            val perLevel = seconds / MusicDirector.MAX_DEPTH
            writeWav(file, engine, seconds) { elapsed ->
                engine.setDepth((elapsed / perLevel).toInt() + 1)
            }
            println("${file.name}  discesa dal livello 1 al ${MusicDirector.MAX_DEPTH}")
        }
    }

    private fun printHelp() {
        println(
            """
            Esportazione della colonna sonora in WAV
              --out <cartella>     destinazione (default: ./musica)
              --seconds <n>        durata di ogni brano (default: 40)
              --depths 1,4,7,10    profondita' da esportare
              --descent            aggiunge una traccia che scende per tutti i livelli
              --seed <n>           seme del generatore
            """.trimIndent(),
        )
    }

    /** Scrive un WAV PCM 16 bit mono. [onProgress] riceve i secondi trascorsi. */
    fun writeWav(
        file: File,
        engine: AmbientMusicEngine,
        seconds: Double,
        onProgress: ((Double) -> Unit)? = null,
    ) {
        val totalFrames = (seconds * engine.sampleRate).toInt()
        val blockFrames = 1024
        val block = ShortArray(blockFrames)
        val dataBytes = totalFrames * 2

        BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
            writeHeader(out, engine.sampleRate, dataBytes)
            var written = 0
            while (written < totalFrames) {
                val frames = minOf(blockFrames, totalFrames - written)
                onProgress?.invoke(written.toDouble() / engine.sampleRate)
                engine.renderBlock(block, frames)
                for (n in 0 until frames) {
                    val sample = block[n].toInt()
                    out.write(sample and 0xFF)
                    out.write((sample shr 8) and 0xFF)
                }
                written += frames
            }
        }
    }

    private fun writeHeader(out: java.io.OutputStream, sampleRate: Int, dataBytes: Int) {
        fun int32(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        fun int16(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray())
        int32(36 + dataBytes)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        int32(16)          // dimensione del blocco fmt
        int16(1)           // PCM non compresso
        int16(1)           // mono
        int32(sampleRate)
        int32(sampleRate * 2) // byte al secondo
        int16(2)           // byte per frame
        int16(16)          // bit per campione
        out.write("data".toByteArray())
        int32(dataBytes)
    }
}
