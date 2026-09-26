import type { ExpoConfig } from 'expo/config';

/**
 * Configurazione dell'app, letta al momento della build.
 *
 * È un file TypeScript e non un JSON perché due valori devono cambiare fra la build
 * di prova in casa e quella definitiva, e non vogliamo due file da tenere allineati
 * a mano.
 */

/**
 * Indirizzo del server di casa. Va impostato al momento della build:
 *
 *   URL_SERVER=http://192.168.1.50:8080  (prova in rete locale)
 *   URL_SERVER=https://tavolo.tuodominio (attraverso il tunnel)
 *
 * Il valore predefinito punta all'indirizzo con cui l'emulatore Android raggiunge il
 * computer che lo ospita: utile per lo sviluppo, inutile su un telefono vero. Se
 * dimentichi di impostarlo, l'app dirà che il server non risponde — ed è meglio di
 * un errore oscuro.
 */
const urlServer = process.env['URL_SERVER'] ?? 'http://10.0.2.2:8080';

/**
 * Android blocca il traffico non cifrato dalla versione 9.
 *
 * In rete locale, verso un indirizzo tipo 192.168.x.x, non esiste un certificato
 * valido, quindi per le prove in casa serve un'eccezione. **Solo per le prove**: la
 * build definitiva passa dal tunnel, che fornisce HTTPS, e questa eccezione deve
 * restare spenta. Da qui il legame con l'indirizzo: se non è cifrato, siamo in prova.
 */
const inChiaro = urlServer.startsWith('http://');

const configurazione: ExpoConfig = {
  name: 'Tavolo',
  slug: 'tavolo',
  version: '0.1.0',
  orientation: 'portrait',
  scheme: 'tavolo',
  userInterfaceStyle: 'automatic',
  newArchEnabled: true,
  platforms: ['android'],
  android: {
    package: 'it.tavolo.app',
    versionCode: 1,
    permissions: [
      'android.permission.RECORD_AUDIO',
      'android.permission.POST_NOTIFICATIONS',
      'android.permission.INTERNET',
    ],
    // Con la baseline API 33 si usa il selettore di sistema per le foto: questi
    // permessi non servono, e chiederli sarebbe attrito inutile per i giocatori.
    blockedPermissions: [
      'android.permission.READ_MEDIA_IMAGES',
      'android.permission.READ_MEDIA_VIDEO',
      'android.permission.READ_EXTERNAL_STORAGE',
    ],
  },
  plugins: [
    'expo-router',
    [
      'expo-build-properties',
      {
        android: {
          minSdkVersion: 33,
          compileSdkVersion: 36,
          targetSdkVersion: 36,
          enableProguardInReleaseBuilds: true,
          usesCleartextTraffic: inChiaro,
        },
      },
    ],
  ],
  experiments: { typedRoutes: true },
  extra: { urlServer },
};

export default configurazione;
