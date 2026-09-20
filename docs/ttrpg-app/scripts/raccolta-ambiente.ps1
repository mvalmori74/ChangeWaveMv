<#
  raccolta-ambiente.ps1 — Sprint 0, risposta a Q1 e Q2
  Raccoglie le caratteristiche del PC che ospitera' il server e verifica
  se la linea e' sotto CGNAT.

  Uso:  tasto destro sul file -> "Esegui con PowerShell"
        oppure:  powershell -ExecutionPolicy Bypass -File .\raccolta-ambiente.ps1

  Sola lettura: non installa, non modifica, non apre porte.
  L'indirizzo IP pubblico viene MASCHERATO: serve la classificazione, non il valore.
#>

$ErrorActionPreference = 'SilentlyContinue'
$out = Join-Path $PSScriptRoot 'ambiente.txt'
$r   = New-Object System.Collections.ArrayList
function Riga($s) { [void]$r.Add($s); Write-Host $s }

Riga "=== AMBIENTE SERVER — generato il $(Get-Date -Format 'yyyy-MM-dd HH:mm') ==="
Riga ""

# ---------- Sistema ----------
$os = Get-CimInstance Win32_OperatingSystem
$cs = Get-CimInstance Win32_ComputerSystem
Riga "[SISTEMA]"
Riga "  OS            : $($os.Caption) build $($os.BuildNumber)"
Riga "  Tipo macchina : $(if ($cs.PCSystemType -eq 2) {'Portatile'} else {'Desktop/altro'})"
Riga "  Sempre acceso : <-- COMPILA A MANO: si / no / solo quando gioco"
Riga ""

# ---------- CPU ----------
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
Riga "[CPU]"
Riga "  Modello       : $($cpu.Name.Trim())"
Riga "  Core / thread : $($cpu.NumberOfCores) / $($cpu.NumberOfLogicalProcessors)"
Riga "  Clock max MHz : $($cpu.MaxClockSpeed)"
Riga ""

# ---------- RAM ----------
$ramGB = [math]::Round($cs.TotalPhysicalMemory / 1GB, 1)
$freeGB = [math]::Round($os.FreePhysicalMemory / 1MB, 1)
Riga "[MEMORIA]"
Riga "  RAM totale GB : $ramGB"
Riga "  RAM libera GB : $freeGB"
Riga ""

# ---------- GPU ----------
Riga "[GPU]"
foreach ($g in Get-CimInstance Win32_VideoController) {
    $vram = if ($g.AdapterRAM -gt 0) { "$([math]::Round($g.AdapterRAM/1GB,1)) GB" } else { "n/d" }
    Riga "  $($g.Name) — VRAM dichiarata: $vram — driver $($g.DriverVersion)"
}
Riga "  NOTA: una GPU dedicata recente cambia i tempi di trascrizione di un ordine di grandezza."
Riga ""

# ---------- Disco ----------
Riga "[DISCHI]"
foreach ($d in Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3") {
    $tot = [math]::Round($d.Size/1GB,0); $lib = [math]::Round($d.FreeSpace/1GB,0)
    Riga "  $($d.DeviceID) totale ${tot} GB, liberi ${lib} GB"
}
Riga "  Secondo disco per i backup (F11): <-- COMPILA A MANO: quale lettera, oppure 'non ce l'ho'"
Riga ""

# ---------- Virtualizzazione / Docker ----------
Riga "[CONTAINER]"
$hyperv = (Get-CimInstance Win32_ComputerSystem).HypervisorPresent
Riga "  Hypervisor attivo : $hyperv"
$docker = (Get-Command docker -ErrorAction SilentlyContinue)
Riga "  Docker installato : $(if ($docker) {'si'} else {'no'})"
if ($docker) { Riga "  Versione          : $(docker --version 2>&1)" }
Riga ""

# ---------- Rete: CGNAT ----------
Riga "[RETE]"
$pub = $null
foreach ($u in @('https://api.ipify.org','https://ifconfig.me/ip','https://icanhazip.com')) {
    try { $pub = (Invoke-RestMethod -Uri $u -TimeoutSec 8).ToString().Trim(); if ($pub) { break } } catch {}
}
if ($pub) {
    $p = $pub.Split('.')
    Riga "  IP pubblico visto da internet : $($p[0]).$($p[1]).x.x  (mascherato)"
} else {
    Riga "  IP pubblico : non rilevato (nessuna rete?)"
}

# hop iniziali: un indirizzo in 100.64.0.0/10 tra i primi hop indica CGNAT
Riga "  Primi hop verso internet:"
$cgnat = $false; $hop = 0
try {
    $tr = & tracert -d -h 6 -w 1200 8.8.8.8 2>&1
    foreach ($l in $tr) {
        if ($l -match '(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})') {
            $ip = $Matches[1]; $hop++
            $o = $ip.Split('.') | ForEach-Object { [int]$_ }
            $isPriv  = ($o[0] -eq 10) -or ($o[0] -eq 192 -and $o[1] -eq 168) -or ($o[0] -eq 172 -and $o[1] -ge 16 -and $o[1] -le 31)
            $isCgnat = ($o[0] -eq 100 -and $o[1] -ge 64 -and $o[1] -le 127)
            $tag = if ($isCgnat) { 'CGNAT (100.64.0.0/10)' } elseif ($isPriv) { 'privato' } else { 'pubblico' }
            if ($isCgnat) { $cgnat = $true }
            Riga "    hop $hop : $ip  [$tag]"
            if ($hop -ge 5) { break }
        }
    }
} catch { Riga "    tracert non disponibile" }

Riga ""
if ($cgnat) {
    Riga "  >>> ESITO: CGNAT RILEVATO. L'inoltro delle porte sul router NON funzionera'."
    Riga "      Non e' un problema: l'architettura prevede un tunnel in uscita."
} else {
    Riga "  >>> ESITO: nessun indizio di CGNAT nei primi hop."
    Riga "      Conferma incrociata: confronta l'IP WAN nell'interfaccia del router con"
    Riga "      quello mascherato qui sopra. Se differiscono, sei comunque sotto CGNAT."
}
Riga ""
Riga "  Banda misurata (fai uno speedtest e compila): download ____ Mbps / UPLOAD ____ Mbps"
Riga "  L'UPLOAD e' il numero che conta: da li' passano i media verso tutti i giocatori."
Riga ""
Riga "=== FINE — mandami questo file ==="

$r | Out-File -FilePath $out -Encoding UTF8
Write-Host ""
Write-Host "Salvato in: $out" -ForegroundColor Green
Read-Host "Premi INVIO per chiudere"
