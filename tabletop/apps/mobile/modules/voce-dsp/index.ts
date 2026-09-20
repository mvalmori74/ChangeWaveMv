import { requireNativeModule } from 'expo-modules-core';
import type { VoceDspModule } from './src/VoceDsp.types';

export * from './src/VoceDsp.types';

/**
 * Il modulo esiste solo in una build nativa personalizzata: con il client Expo
 * generico non c'e'. Chi lo usa deve gestire l'assenza, non darla per impossibile.
 */
export function moduloVoceDsp(): VoceDspModule | null {
  try {
    return requireNativeModule<VoceDspModule>('VoceDsp');
  } catch {
    return null;
  }
}
