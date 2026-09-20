#!/usr/bin/env bash
# raccolta-ambiente.sh — Sprint 0, risposta a Q1 e Q2 (versione Linux/macOS)
# Sola lettura. L'IP pubblico viene mascherato: serve la classificazione, non il valore.
set -uo pipefail
OUT="$(dirname "$0")/ambiente.txt"
exec > >(tee "$OUT") 2>&1

echo "=== AMBIENTE SERVER — generato il $(date '+%Y-%m-%d %H:%M') ==="
echo
echo "[SISTEMA]"
echo "  OS            : $(uname -srm)"
[ -f /etc/os-release ] && echo "  Distribuzione : $(. /etc/os-release; echo "$PRETTY_NAME")"
echo "  Sempre acceso : <-- COMPILA A MANO: si / no / solo quando gioco"
echo
echo "[CPU]"
if [ -f /proc/cpuinfo ]; then
  echo "  Modello       : $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2 | xargs)"
  echo "  Thread        : $(nproc)"
else
  echo "  Modello       : $(sysctl -n machdep.cpu.brand_string 2>/dev/null)"
  echo "  Thread        : $(sysctl -n hw.ncpu 2>/dev/null)"
fi
echo
echo "[MEMORIA]"
free -h 2>/dev/null | sed 's/^/  /' || echo "  $(sysctl -n hw.memsize 2>/dev/null | awk '{printf "%.1f GB\n", $1/1073741824}')"
echo
echo "[GPU]"
(lspci 2>/dev/null | grep -Ei 'vga|3d|display' | sed 's/^/  /') || echo "  n/d"
echo "  NOTA: una GPU dedicata recente cambia i tempi di trascrizione di un ordine di grandezza."
echo
echo "[DISCHI]"
df -h --output=source,size,avail,target 2>/dev/null | grep -v tmpfs | sed 's/^/  /' || df -h | sed 's/^/  /'
echo "  Secondo disco per i backup (F11): <-- COMPILA A MANO"
echo
echo "[CONTAINER]"
echo "  Docker : $(command -v docker >/dev/null && docker --version 2>&1 || echo 'non installato')"
echo
echo "[RETE]"
PUB=""
for U in https://api.ipify.org https://ifconfig.me/ip https://icanhazip.com; do
  PUB=$(curl -fsS --max-time 8 "$U" 2>/dev/null | tr -d '[:space:]') && [ -n "$PUB" ] && break
done
if [ -n "$PUB" ]; then
  echo "  IP pubblico visto da internet : $(echo "$PUB" | cut -d. -f1-2).x.x  (mascherato)"
else
  echo "  IP pubblico : non rilevato"
fi
echo "  Primi hop verso internet:"
CGNAT=0
if command -v traceroute >/dev/null; then TR="traceroute -n -m 6 -w 2 8.8.8.8";
elif command -v tracepath >/dev/null; then TR="tracepath -n -m 6 8.8.8.8"; else TR=""; fi
if [ -n "$TR" ]; then
  while read -r line; do
    ip=$(echo "$line" | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | head -1)
    [ -z "$ip" ] && continue
    o1=${ip%%.*}; rest=${ip#*.}; o2=${rest%%.*}
    tag="pubblico"
    if [ "$o1" -eq 100 ] && [ "$o2" -ge 64 ] && [ "$o2" -le 127 ]; then tag="CGNAT (100.64.0.0/10)"; CGNAT=1
    elif [ "$o1" -eq 10 ] || { [ "$o1" -eq 192 ] && [ "$o2" -eq 168 ]; } || { [ "$o1" -eq 172 ] && [ "$o2" -ge 16 ] && [ "$o2" -le 31 ]; }; then tag="privato"; fi
    echo "    $ip  [$tag]"
  done < <($TR 2>/dev/null | tail -n +2)
else
  echo "    traceroute non disponibile"
fi
echo
if [ "$CGNAT" -eq 1 ]; then
  echo "  >>> ESITO: CGNAT RILEVATO. L'inoltro delle porte NON funzionera'."
  echo "      Non e' un problema: l'architettura prevede un tunnel in uscita."
else
  echo "  >>> ESITO: nessun indizio di CGNAT nei primi hop."
  echo "      Conferma incrociata con l'IP WAN mostrato dal router."
fi
echo
echo "  Banda misurata (speedtest, poi compila): download ____ Mbps / UPLOAD ____ Mbps"
echo "  L'UPLOAD e' il numero che conta."
echo
echo "=== FINE — mandami questo file ==="
