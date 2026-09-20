import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme } from 'react-native';
import { colori } from '@/design/tema';
import { stiliPerTema } from '@/design/stili';

export default function Disposizione() {
  const scuro = useColorScheme() === 'dark';
  const c = scuro ? colori.scuro : colori.chiaro;
  const s = stiliPerTema(scuro);

  return (
    <>
      <StatusBar style={scuro ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: s.sfondoBarra,
          headerTintColor: c.testo,
          contentStyle: s.sfondoSchermata,
        }}
      >
        <Stack.Screen name="index" options={{ title: 'Tavolo' }} />
      </Stack>
    </>
  );
}
