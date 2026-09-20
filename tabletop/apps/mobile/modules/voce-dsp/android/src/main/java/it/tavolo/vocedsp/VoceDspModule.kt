package it.tavolo.vocedsp

import android.speech.tts.TextToSpeech
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.Locale
import kotlin.system.measureTimeMillis

/**
 * Catena DSP per le voci di personaggio (F4, ADR-004).
 *
 * SPIKE: scheletro dello Sprint 0. La trasformazione vera non e' ancora collegata;
 * quello che c'e' serve a verificare che il modulo si compili, si carichi su un
 * telefono reale e sappia dire cosa offre il device. Il corpo di [applicaCatena]
 * va sostituito in SPIKE-2.
 *
 * Nota sulla licenza, che qui non e' un dettaglio: la trasformazione usera'
 * Signalsmith Stretch (MIT) e NON Rubber Band, che e' GPL v2 e dentro un APK
 * distribuito obbligherebbe a fornire il sorgente dell'intera app. Vedi
 * docs/licenses.md e ADR-004: non sostituire la libreria senza rileggerli.
 */
class VoceDspModule : Module() {

  private val lavoro = SupervisorJob()
  private val ambito = CoroutineScope(Dispatchers.Default + lavoro)
  private var tts: TextToSpeech? = null

  class ParametriDsp : Record {
    @Field var pitchSemitoni: Double = 0.0
    @Field var formantiPct: Double = 0.0
    @Field var riverberoMix: Double = 0.0
    @Field var saturazione: Double = 0.0
  }

  class RichiestaRendering : Record {
    @Field var uriIngresso: String = ""
    @Field var uriUscita: String = ""
    @Field var parametri: ParametriDsp = ParametriDsp()
  }

  override fun definition() = ModuleDefinition {
    Name("VoceDsp")

    OnCreate {
      // Il motore di sintesi di sistema serve come ripiego a server spento
      // (F4 backend C). Va inizializzato presto: la prima chiamata e' lenta.
      val contesto = appContext.reactContext ?: return@OnCreate
      tts = TextToSpeech(contesto) { /* esito ignorato: le capacita' si leggono dopo */ }
    }

    OnDestroy {
      lavoro.cancel()
      tts?.shutdown()
      tts = null
    }

    AsyncFunction("capacita") {
      // Rilevamento a runtime, non assunzioni: voci e percorso a bassa latenza
      // cambiano fra produttori anche a parita' di versione di Android (rischio R4).
      val contesto = appContext.reactContext
      val gestore = contesto?.getSystemService(android.content.Context.AUDIO_SERVICE)
        as? android.media.AudioManager

      val frequenza = gestore
        ?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        ?.toIntOrNull() ?: 48_000
      val buffer = gestore
        ?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        ?.toIntOrNull() ?: 256

      val vociItaliane = tts?.voices
        ?.filter { it.locale.language == Locale.ITALIAN.language }
        ?.map { it.name }
        ?: emptyList()

      mapOf(
        "bassaLatenza" to (contesto?.packageManager?.hasSystemFeature(
          android.content.pm.PackageManager.FEATURE_AUDIO_LOW_LATENCY,
        ) ?: false),
        "frequenzaNativaHz" to frequenza,
        "dimensioneBufferConsigliata" to buffer,
        "vociSistemaItaliano" to vociItaliane,
      )
    }

    AsyncFunction("renderizza") { richiesta: RichiestaRendering ->
      var durataAudioMs = 0L
      val millisecondi = measureTimeMillis {
        durataAudioMs = applicaCatena(richiesta)
      }
      mapOf(
        "uriUscita" to richiesta.uriUscita,
        "millisecondiElaborazione" to millisecondi,
        "durataAudioMs" to durataAudioMs,
      )
    }

    Function("annulla") {
      // Un vocale lungo su un telefono lento deve restare interrompibile.
      lavoro.cancelChildren()
    }
  }

  /**
   * STUB: la catena vera (pitch shift, spostamento formanti, riverbero, saturazione)
   * arriva in SPIKE-2, con Signalsmith Stretch via JNI su Oboe.
   *
   * Oggi copia l'ingresso sull'uscita, cosi' il percorso completo
   * registrazione -> rendering -> riproduzione si puo' provare end-to-end prima che
   * il DSP esista. Restituisce la durata dell'audio in millisecondi.
   */
  private fun applicaCatena(richiesta: RichiestaRendering): Long {
    val ingresso = java.io.File(java.net.URI(richiesta.uriIngresso))
    val uscita = java.io.File(java.net.URI(richiesta.uriUscita))
    ingresso.copyTo(uscita, overwrite = true)

    val estrattore = android.media.MediaMetadataRetriever()
    return try {
      estrattore.setDataSource(ingresso.absolutePath)
      estrattore
        .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
      0L
    } finally {
      estrattore.release()
    }
  }
}
