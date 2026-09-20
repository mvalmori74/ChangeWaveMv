import { Text } from 'react-native';

/** Segnaposto: il motore dei dadi arriva in S2. */
export function Dado({ facce }: { facce: number }) {
  return <Text>d{facce}</Text>;
}
