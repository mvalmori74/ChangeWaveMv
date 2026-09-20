import { useCallback, useEffect, useState } from 'react';
import { ScrollView, Text, useColorScheme, View } from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { classificaFascia, type EsitoFascia } from '@tabletop/shared';
import { stiliPerTema } from '@/design/stili';
import {
  funzioniDisponibili, spiegaAllUtente, valutaRaggiungibilita, type Raggiungibilita,
} from '@/features/stato/statoServer';

const URL_SERVER = String(Constants.expoConfig?.extra?.['urlServer'] ?? 'http://10.0.2.2:8080');

/**
 * Schermata unica dello Sprint 0 (S0-02): non e' il prodotto, e' la prova che la
 * catena regge. Mostra le due cose che in Sprint 0 vogliamo vedere su un telefono
 * vero: la fascia di prestazioni assegnata e se il server di casa risponde.
 */
export default function Schermata() {
  const s = stiliPerTema(useColorScheme() === 'dark');

  const [fascia, setFascia] = useState<EsitoFascia | null>(null);
  const [erroreFascia, setErroreFascia] = useState<string | null>(null);
  const [raggiungibilita, setRaggiungibilita] = useState<Raggiungibilita>('ignota');
  const [millisecondi, setMillisecondi] = useState<number | null>(null);

  useEffect(() => {
    try {
      const anno = Device.deviceYearClass;
      setFascia(
        classificaFascia({
          ramGb: Math.round((Device.totalMemory ?? 0) / 1024 ** 3),
          coreCpu: Device.supportedCpuArchitectures?.length ?? 4,
          livelloApi: Number(Device.platformApiLevel ?? 33),
          ...(anno != null ? { annoModello: anno } : {}),
        }),
      );
    } catch (e) {
      setErroreFascia(e instanceof Error ? e.message : 'errore sconosciuto');
    }
  }, []);

  const verificaServer = useCallback(async () => {
    const inizio = Date.now();
    try {
      const risposta = await fetch(`${URL_SERVER}/salute`, { signal: AbortSignal.timeout(6000) });
      const ms = Date.now() - inizio;
      setMillisecondi(ms);
      setRaggiungibilita(valutaRaggiungibilita(risposta.ok ? 'ok' : 'errore', ms));
    } catch {
      setMillisecondi(Date.now() - inizio);
      setRaggiungibilita('irraggiungibile');
    }
  }, []);

  useEffect(() => { void verificaServer(); }, [verificaServer]);

  const disponibili = funzioniDisponibili(raggiungibilita);

  return (
    <ScrollView contentContainerStyle={s.pagina}>
      <View style={s.scheda}>
        <Text style={s.titolo}>Server del tavolo</Text>
        <Text style={s.corpo}>{spiegaAllUtente(raggiungibilita)}</Text>
        {millisecondi !== null && (
          <Text style={s.piccolo}>Risposta in {millisecondi} ms — {URL_SERVER}</Text>
        )}
        <Text style={s.piccolo}>
          Scrivere: {disponibili.scrivere ? 'sì' : 'no'} · Tirare dadi:{' '}
          {disponibili.tirareDadi ? 'sì' : 'no'} · Allegare:{' '}
          {disponibili.allegare ? 'sì' : 'no'}
        </Text>
      </View>

      <View style={s.scheda}>
        <Text style={s.titolo}>Questo telefono</Text>
        {erroreFascia ? (
          <Text style={s.errore}>{erroreFascia}</Text>
        ) : fascia ? (
          <>
            <Text style={s.corpo}>Fascia: {fascia.fascia}</Text>
            <Text style={s.piccolo}>{fascia.motivo}</Text>
            <Text style={s.piccolo}>
              {fascia.daMisura ? 'da misura reale' : 'da indizi hardware, non ancora misurata'}
            </Text>
          </>
        ) : (
          <Text style={s.piccolo}>Rilevazione in corso…</Text>
        )}
        <Text style={s.piccolo}>
          {Device.manufacturer} {Device.modelName} · Android API {Device.platformApiLevel}
        </Text>
      </View>
    </ScrollView>
  );
}
