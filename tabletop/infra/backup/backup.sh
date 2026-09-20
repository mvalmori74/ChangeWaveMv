#!/bin/sh
# Backup periodico (master prompt F11). Gira in un container, scrive su un SECONDO disco.
# Salva: dump del database + copia dei media + un file di esito leggibile dalla pagina di stato.
set -eu

INTERVALLO=$(( ${ORE_FRA_BACKUP:-12} * 3600 ))
CONSERVA=${GIORNI_CONSERVAZIONE_BACKUP:-30}

esegui() {
  STAMP=$(date '+%Y%m%d-%H%M%S')
  DEST="/backup/$STAMP"
  mkdir -p "$DEST"

  # 1) Database. Formato custom: comprimibile e ripristinabile selettivamente.
  pg_dump -h db -U "${POSTGRES_USER:-tavolo}" -d "${POSTGRES_DB:-tavolo}" \
          -Fc -f "$DEST/db.dump"

  # 2) Media. Copia incrementale: solo cio' che e' cambiato.
  mkdir -p /backup/media
  cp -au /dati/. /backup/media/ 2>/dev/null || true

  # 3) Impronta di verifica: serve a sapere se il dump e' integro senza ripristinarlo.
  (cd "$DEST" && md5sum db.dump > db.dump.md5)

  BYTE=$(wc -c < "$DEST/db.dump")
  printf '{"quando":"%s","esito":"ok","byteDb":%s,"percorso":"%s"}\n' \
    "$(date -Iseconds)" "$BYTE" "$DEST" > /backup/ultimo-backup.json

  # 4) Rotazione.
  find /backup -maxdepth 1 -type d -name '20*' -mtime "+$CONSERVA" -exec rm -rf {} + 2>/dev/null || true
  echo "[backup] completato: $DEST ($BYTE byte)"
}

fallito() {
  printf '{"quando":"%s","esito":"fallito","errore":"%s"}\n' \
    "$(date -Iseconds)" "${1:-sconosciuto}" > /backup/ultimo-backup.json
  echo "[backup] FALLITO: ${1:-sconosciuto}" >&2
}

echo "[backup] avviato, intervallo ${ORE_FRA_BACKUP:-12}h, conservazione ${CONSERVA}g"
while true; do
  esegui || fallito "errore durante il dump"
  sleep "$INTERVALLO"
done
