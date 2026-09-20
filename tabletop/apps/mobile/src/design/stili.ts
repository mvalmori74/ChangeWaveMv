import { StyleSheet } from 'react-native';
import { colori, spazio, tipografia, type Palette } from './tema';

/**
 * Fogli di stile per tema, costruiti una volta sola.
 *
 * Perche' non stili inline: con React Native 0.87 i tipi generati rifiutano gli
 * oggetti di stile scritti direttamente dentro una composizione ad array
 * (`style={[base, { color: x }]}`). Costruire il foglio per tema risolve il
 * problema alla radice ed e' anche la forma piu' efficiente, perche' evita di
 * ricreare un oggetto a ogni render. Convenzione del progetto: nessuno stile
 * inline, mai.
 */
function crea(c: Palette) {
  return StyleSheet.create({
    pagina: { padding: spazio.m, gap: spazio.m, flexGrow: 1, backgroundColor: c.sfondo },
    scheda: {
      padding: spazio.m, borderRadius: 12, borderWidth: 1, gap: spazio.xs,
      backgroundColor: c.superficie, borderColor: c.bordo,
    },
    titolo: { ...tipografia.titolo, color: c.testo },
    corpo: { ...tipografia.corpo, color: c.testo },
    piccolo: { ...tipografia.piccolo, color: c.testoTenue },
    errore: { ...tipografia.corpo, color: c.errore },
    sfondoSchermata: { backgroundColor: c.sfondo },
    sfondoBarra: { backgroundColor: c.superficie },
  });
}

export const stiliChiaro = crea(colori.chiaro);
export const stiliScuro = crea(colori.scuro);

export function stiliPerTema(scuro: boolean) {
  return scuro ? stiliScuro : stiliChiaro;
}
