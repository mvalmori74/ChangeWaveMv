/**
 * Sorveglianza dello spazio su disco (master prompt §4-bis punto 3).
 *
 * Con la retention infinita il vincolo da presidiare non e' piu' il tempo ma lo
 * spazio: qui si decide quando avvisare il GM e quando smettere di accettare
 * allegati. Il servizio NON deve morire a disco pieno: chat e dadi continuano.
 */
export type LivelloDisco = 'ok' | 'avviso' | 'critico' | 'esaurito';

export const SOGLIE_DISCO = { avviso: 0.8, critico: 0.9, esaurito: 0.98 } as const;

export function livelloDisco(usatoByte: number, totaleByte: number): LivelloDisco {
  if (totaleByte <= 0) throw new RangeError('Dimensione totale del disco non valida');
  const frazione = usatoByte / totaleByte;
  if (frazione >= SOGLIE_DISCO.esaurito) return 'esaurito';
  if (frazione >= SOGLIE_DISCO.critico) return 'critico';
  if (frazione >= SOGLIE_DISCO.avviso) return 'avviso';
  return 'ok';
}

/** A disco esaurito si rifiutano i caricamenti, non si spegne il tavolo. */
export function accettaCaricamenti(livello: LivelloDisco): boolean {
  return livello !== 'esaurito';
}

export function messaggioPerIlGm(livello: LivelloDisco): string | null {
  switch (livello) {
    case 'ok': return null;
    case 'avviso': return 'Spazio su disco oltre l’80%. Valuta di archiviare una campagna conclusa.';
    case 'critico': return 'Spazio su disco oltre il 90%. Archivia o libera spazio a breve.';
    case 'esaurito': return 'Disco pieno: i nuovi allegati sono rifiutati. Chat e dadi continuano a funzionare.';
  }
}
