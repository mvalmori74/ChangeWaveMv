import { Text } from 'react-native';
import type { Messaggio } from '@tabletop/shared';

/** Segnaposto: la chat vera arriva in S1. Qui serve solo come feature di esempio. */
export function ListaMessaggi({ messaggi }: { messaggi: readonly Messaggio[] }) {
  return <Text>{messaggi.length} messaggi</Text>;
}
