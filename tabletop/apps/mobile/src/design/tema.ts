/**
 * Design system minimale (master prompt §4): token, non una libreria UI pesante.
 * Chiaro e scuro definiti insieme: una app che si usa la sera deve stare bene al buio.
 */
export const spazio = { xs: 4, s: 8, m: 16, l: 24, xl: 32 } as const;

export const tipografia = {
  titolo: { fontSize: 22, fontWeight: '600' },
  corpo: { fontSize: 16, fontWeight: '400' },
  piccolo: { fontSize: 13, fontWeight: '400' },
} as const;

export interface Palette {
  readonly sfondo: string; readonly superficie: string;
  readonly testo: string; readonly testoTenue: string; readonly bordo: string;
  readonly ok: string; readonly avviso: string; readonly errore: string;
}

export const colori: { readonly chiaro: Palette; readonly scuro: Palette } = {
  chiaro: {
    sfondo: '#faf9f7', superficie: '#ffffff', testo: '#1a1917',
    testoTenue: '#6b6862', bordo: '#e3e0d9',
    ok: '#2f7d4f', avviso: '#b06f1a', errore: '#a33227',
  },
  scuro: {
    sfondo: '#131211', superficie: '#1d1b19', testo: '#f2efe9',
    testoTenue: '#a09a91', bordo: '#33302c',
    ok: '#5fb37f', avviso: '#d79a4a', errore: '#d9695c',
  },
} as const;

/** Area minima toccabile (master prompt F10). Sotto i 44 pt non si scende. */
export const TOCCO_MINIMO = 44;
