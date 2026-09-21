/**
 * Copia in dist/ i file che non sono TypeScript.
 *
 * Serve perche' tsc compila il codice e ignora tutto il resto: schema.sql restava
 * nei sorgenti e l'immagine Docker, che contiene solo dist/, si fermava all'avvio
 * con "file non trovato". I test non potevano accorgersene, perche' girano dai
 * sorgenti dove il file c'e'.
 */
import { cp, readdir } from 'node:fs/promises';
import path from 'node:path';

const QUI = path.dirname(new URL(import.meta.url).pathname);
const ESTENSIONI = ['.sql'];

async function copia(daDir, aDir) {
  for (const voce of await readdir(daDir, { withFileTypes: true })) {
    const da = path.join(daDir, voce.name);
    const a = path.join(aDir, voce.name);
    if (voce.isDirectory()) await copia(da, a);
    else if (ESTENSIONI.includes(path.extname(voce.name))) {
      await cp(da, a);
      console.log(`  copiato ${path.relative(QUI, a)}`);
    }
  }
}

await copia(path.join(QUI, 'src'), path.join(QUI, 'dist'));
