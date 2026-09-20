#!/bin/sh
# Ripristino (master prompt F11).
#
# ATTENZIONE: un backup mai ripristinato non e' un backup. Questa procedura va
# ESEGUITA ALMENO UNA VOLTA in Sprint 0, su un'installazione vuota, annotando nel
# runbook il tempo impiegato. Non e' burocrazia: e' l'unica prova che i dati
# tornino indietro.
#
# Uso:  ./ripristina.sh /percorso/backup/20260920-030000
set -eu

DA=${1:?indica la cartella del backup da ripristinare}
[ -f "$DA/db.dump" ] || { echo "manca $DA/db.dump" >&2; exit 1; }

echo "--- verifica integrita' ---"
if [ -f "$DA/db.dump.md5" ]; then
  (cd "$DA" && md5sum -c db.dump.md5) || { echo "IMPRONTA NON CORRISPONDE: dump corrotto" >&2; exit 2; }
else
  echo "nessuna impronta di verifica presente: procedo comunque" >&2
fi

echo "--- ripristino database ---"
docker compose up -d db
until docker compose exec -T db pg_isready -U "${POSTGRES_USER:-tavolo}" >/dev/null 2>&1; do
  echo "attendo il database..."; sleep 2
done
docker compose exec -T db psql -U "${POSTGRES_USER:-tavolo}" -c \
  "DROP DATABASE IF EXISTS ${POSTGRES_DB:-tavolo}; CREATE DATABASE ${POSTGRES_DB:-tavolo};"
docker compose exec -T db pg_restore -U "${POSTGRES_USER:-tavolo}" \
  -d "${POSTGRES_DB:-tavolo}" --no-owner < "$DA/db.dump"

echo "--- ripristino media ---"
MEDIA=$(dirname "$DA")/media
[ -d "$MEDIA" ] && cp -au "$MEDIA/." ./data/media/ || echo "nessuna cartella media nel backup"

echo "--- fatto. Annota nel runbook il tempo impiegato. ---"
