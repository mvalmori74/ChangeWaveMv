/**
 * Interfaccia del modulo audio nativo (ADR-004).
 *
 * Deliberatamente SENZA riferimenti ad Android, benche' esista solo
 * l'implementazione Kotlin: costa poco ora e tiene aperta la porta a un domani
 * (§13-D5). Nessuna implementazione iOS va scritta finche' quella decisione non cambia.
 */
import type { ParametriDsp } from '@tabletop/shared';

export interface CapacitaAudio {
  /** Percorso a bassa latenza disponibile sul device. */
  readonly bassaLatenza: boolean;
  /** Frequenza di campionamento nativa: usarla evita un ricampionamento inutile. */
  readonly frequenzaNativaHz: number;
  readonly dimensioneBufferConsigliata: number;
  /** Motori di sintesi di sistema disponibili, per il ripiego a server spento (F4 backend C). */
  readonly vociSistemaItaliano: readonly string[];
}

export interface RichiestaRendering {
  readonly uriIngresso: string;
  readonly uriUscita: string;
  readonly parametri: ParametriDsp;
}

export interface EsitoRendering {
  readonly uriUscita: string;
  readonly millisecondiElaborazione: number;
  readonly durataAudioMs: number;
}

export interface VoceDspModule {
  /**
   * Rileva cosa offre QUESTO telefono. Il rilevamento a runtime non e' un lusso:
   * voci di sistema e percorso a bassa latenza cambiano fra produttori anche a
   * parita' di versione di Android (rischio R4).
   */
  capacita(): Promise<CapacitaAudio>;

  /**
   * Applica la catena DSP a un file audio gia' registrato.
   *
   * Rendering differito, non in tempo reale (ADR-004, decisione 3): il prodotto e'
   * una chat con messaggi vocali, non una conversazione dal vivo. Questo toglie ogni
   * vincolo di latenza stretta e permette di rifare il rendering con un preset
   * diverso senza registrare di nuovo.
   */
  renderizza(richiesta: RichiestaRendering): Promise<EsitoRendering>;

  /** Annulla un rendering in corso. Un vocale lungo su un telefono lento va interrompibile. */
  annulla(): void;
}
