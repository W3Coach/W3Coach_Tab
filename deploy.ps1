#Requires -Version 5.1
<#
.SYNOPSIS
    W3Coach Tab - Interaktives Deployment-Script
.DESCRIPTION
    Fuehrt durch die vollstaendige Erstinstallation auf einem neuen
    Android-Tablet (USB-Verbindung). Automatisiert alle ADB-Befehle
    und gibt Hinweise fuer manuelle Schritte am Geraet.
.NOTES
    Voraussetzungen:
    - ADB im PATH oder im selben Verzeichnis wie dieses Script
    - APK im selben Verzeichnis wie dieses Script oder unter app\release
      (Gradle-Build-Output, z.B. app-release.apk)
    - Tablet per USB-Kabel angeschlossen
#>

Set-StrictMode -Version Latest
# Hinweis: bewusst NICHT $ErrorActionPreference = "Stop" - bei nativen Kommandos
# (adb) mit 2>&1 wuerde jede stderr-Ausgabe sofort das Skript abbrechen, bevor
# die eigene Erfolgs-/Fehlerpruefung unten ueberhaupt laeuft. Fehler werden hier
# bewusst manuell per Textmatch/$LASTEXITCODE geprueft und per exit 1 behandelt.
$ErrorActionPreference = "Continue"

function Write-Step    { param($n, $text) Write-Host "`n[$n] $text" -ForegroundColor Cyan }
function Write-OK      { param($text) Write-Host "  [OK]  $text" -ForegroundColor Green }
function Write-Hint    { param($text) Write-Host "  [>>]  $text" -ForegroundColor Yellow }
function Write-Waiting { param($text) Write-Host "  [...] $text" -ForegroundColor DarkCyan }
function Write-Err     { param($text) Write-Host "  [!!]  $text" -ForegroundColor Red }

function Pause-ForUser {
    param([string]$message = "Druecke ENTER wenn fertig...")
    Write-Host ""
    Write-Host "  $message" -ForegroundColor Magenta -NoNewline
    Read-Host
}

function Confirm-Step {
    param([string]$question)
    Write-Host ""
    Write-Host "  $question [J/N]: " -ForegroundColor Magenta -NoNewline
    $answer = Read-Host
    return $answer -match '^[JjYy]'
}

function Wait-ForDevice {
    Write-Waiting "Warte auf ADB-Verbindung..."
    $timeout = 60
    $elapsed = 0
    while ($elapsed -lt $timeout) {
        $devices = & adb devices 2>&1 | Select-String "device$"
        if ($devices) {
            Write-OK "Geraet verbunden"
            return $true
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "." -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Err "Timeout: Kein Geraet gefunden nach $timeout Sekunden"
    return $false
}

function Test-PackageInstalled {
    param([string]$package)
    $result = & adb shell pm list packages 2>&1 | Select-String $package
    return $null -ne $result
}

function Test-DeviceOwner {
    $result = & adb shell dpm list-owners 2>&1
    return ($result -join "") -match "com.w3coach.w3coachtab"
}

function Find-APK {
    $scriptDir  = Split-Path -Parent $MyInvocation.ScriptName
    $searchDirs = @($scriptDir, (Join-Path $scriptDir "app\release")) |
                  Where-Object { Test-Path $_ }

    $apks = @()
    foreach ($dir in $searchDirs) {
        $apks += @(Get-ChildItem -Path $dir -Filter "w3coachtab-*.apk" -ErrorAction SilentlyContinue)
    }
    if ($apks.Count -eq 0) {
        foreach ($dir in $searchDirs) {
            $apks += @(Get-ChildItem -Path $dir -Filter "*.apk" -ErrorAction SilentlyContinue)
        }
    }
    if ($apks.Count -eq 0) { return $null }
    if ($apks.Count -eq 1) { return $apks[0].FullName }
    Write-Host ""
    Write-Host "  Mehrere APKs gefunden:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $apks.Count; $i++) {
        Write-Host "  [$i] $($apks[$i].FullName)"
    }
    Write-Host "  Auswahl [0-$($apks.Count-1)]: " -NoNewline
    $choice = Read-Host
    return $apks[[int]$choice].FullName
}

Clear-Host
Write-Host ""
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host "           W3Coach Tab - Deployment Script" -ForegroundColor Cyan
Write-Host "           Tablet Erstinstallation (USB)" -ForegroundColor Cyan
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dieses Script fuehrt durch die komplette Erstinstallation." -ForegroundColor Gray
Write-Host "  Manuelle Schritte werden klar angezeigt." -ForegroundColor Gray
Write-Host ""

$apkPath = Find-APK
if (-not $apkPath) {
    Write-Err "Keine W3Coach Tab APK gefunden!"
    Write-Hint "Bitte w3coachtab-*.apk in dasselbe Verzeichnis legen."
    exit 1
}
Write-OK "APK gefunden: $(Split-Path -Leaf $apkPath)"

$installWireGuard = Confirm-Step "WireGuard VPN konfigurieren? (optional - fuer Fernwartung)"
$wgEndpoint   = ""
$wgServerKey  = ""
$wgClientIp   = ""
$wgPrivateKey = ""
$wgPublicKey  = ""

if ($installWireGuard) {
    Write-Host ""
    Write-Waiting "Generiere WireGuard-Schluesselpaar fuer dieses Tablet..."
    try {
        $wgPrivateKey = (& wg genkey).Trim()
        $wgPublicKey  = ($wgPrivateKey | & wg pubkey).Trim()
    } catch {
        Write-Err "Konnte kein Schluesselpaar generieren."
        Write-Hint "wg.exe (WireGuard-Tools) nicht gefunden oder nicht im PATH."
        exit 1
    }
    if ([string]::IsNullOrWhiteSpace($wgPrivateKey) -or [string]::IsNullOrWhiteSpace($wgPublicKey)) {
        Write-Err "Schluesselpaar-Generierung lieferte keinen gueltigen Wert."
        exit 1
    }
    Write-OK "Schluesselpaar generiert (Private Key bleibt nur auf diesem Tablet)"

    Write-Host ""
    Write-Host "  WireGuard-Konfiguration:" -ForegroundColor Cyan
    Write-Host "  Server (IP:Port):              " -NoNewline; $wgEndpoint  = Read-Host
    Write-Host "  Server Public Key:             " -NoNewline; $wgServerKey = Read-Host
    Write-Host "  Client IP (z.B. 10.0.0.3/32): " -NoNewline; $wgClientIp  = Read-Host

    Write-Host ""
    Write-Host "  ------------------------------------------------------------" -ForegroundColor Yellow
    Write-Hint "Neuen Peer JETZT auf dem WireGuard-Server eintragen:"
    Write-Hint "  Public Key: $wgPublicKey"
    Write-Hint "  Allowed IP: $wgClientIp"
    Write-Host "  ------------------------------------------------------------" -ForegroundColor Yellow
    Pause-ForUser "Peer auf dem Server eingetragen? ENTER druecken..."
}

Write-Step "1/8" "Tablet einrichten"
Write-Host ""
Write-Hint "Fuehre den Setup-Wizard am Geraet vollstaendig durch:"
Write-Hint "  1. Sprache waehlen"
Write-Hint "  2. WLAN einrichten"
Write-Hint "  3. Google-Konto NICHT einrichten (Schritt ueberspringen, falls moeglich)"
Write-Hint "  4. Alle weiteren Setup-Schritte abschliessen"
Write-Host ""
Write-Hint "WICHTIG: Den Setup-Wizard NICHT abbrechen!"
Pause-ForUser "Setup-Wizard abgeschlossen? ENTER druecken..."

Write-Step "2/8" "Entwickleroptionen aktivieren"
Write-Host ""
Write-Hint "Am Geraet:"
Write-Hint "  Einstellungen -> Ueber das Tablet"
Write-Hint "  -> 7x auf 'Build-Nummer' tippen"
Write-Hint "  -> Meldung 'Du bist jetzt Entwickler' erscheint"
Pause-ForUser "Entwickleroptionen aktiviert? ENTER druecken..."

Write-Step "3/8" "ADB-Debugging aktivieren und verbinden"
Write-Host ""
Write-Hint "Tablet per USB-Kabel mit diesem PC verbinden."
Write-Hint "Am Geraet:"
Write-Hint "  Einstellungen -> System -> Entwickleroptionen"
Write-Hint "  -> 'USB-Debugging' aktivieren"
Write-Host ""
Write-Hint "Am Tablet erscheint ein Dialog 'USB-Debugging zulassen?'"
Write-Hint "  -> Haken bei 'Von diesem Computer immer zulassen' setzen -> Zulassen"
Pause-ForUser "USB-Debugging aktiviert und am Geraet bestaetigt? ENTER druecken..."

if (-not (Wait-ForDevice)) { exit 1 }

Write-Step "4/8" "Kein Google-Konto vorhanden?"
Write-Host ""
Write-Hint "Falls beim Setup doch ein Google-Konto hinzugefuegt wurde, jetzt entfernen:"
Write-Hint "  Einstellungen -> Konten -> Google-Konto auswaehlen"
Write-Hint "  -> Konto entfernen -> bestaetigen"
Write-Host ""
Write-Hint "WICHTIG: Mit einem vorhandenen Konto schlaegt 'Device Owner setzen' fehl!"
Pause-ForUser "Kein Google-Konto auf dem Geraet (ggf. entfernt)? ENTER druecken..."

Write-Step "5/8" "W3Coach Tab installieren"
if (Test-PackageInstalled "com.w3coach.w3coachtab") {
    Write-Hint "W3Coach Tab ist bereits installiert - wird aktualisiert..."
    $installArgs = @("install", "-r", $apkPath)
} else {
    $installArgs = @("install", $apkPath)
}
Write-Waiting "Installiere APK..."
$installResult = & adb @installArgs 2>&1
$installOutput = $installResult -join ""

if ($installOutput -match "Success") {
    Write-OK "W3Coach Tab erfolgreich installiert"
} elseif ($installOutput -match "INSTALL_FAILED_VERSION_DOWNGRADE") {
    Write-Hint "Installierte Version hat einen hoeheren Versionscode als diese APK."
    if (Confirm-Step "Downgrade erzwingen und trotzdem installieren?") {
        Write-Waiting "Installiere APK mit erzwungenem Downgrade..."
        $installArgsForce = if ($installArgs -contains "-r") {
            @("install", "-r", "-d", $apkPath)
        } else {
            @("install", "-d", $apkPath)
        }
        $installResult = & adb @installArgsForce 2>&1
        $installOutput = $installResult -join ""
        if ($installOutput -match "Success") {
            Write-OK "W3Coach Tab erfolgreich installiert (Downgrade)"
        } else {
            Write-Err "Installation fehlgeschlagen:"
            Write-Host "  $installResult" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Err "Installation abgebrochen."
        exit 1
    }
} else {
    Write-Err "Installation fehlgeschlagen:"
    Write-Host "  $installResult" -ForegroundColor Red
    exit 1
}

Write-Step "6/8" "Device Owner setzen"
if (Test-DeviceOwner) {
    Write-OK "W3Coach Tab ist bereits Device Owner"
} else {
    Write-Waiting "Setze Device Owner..."
    $dpmResult = & adb shell dpm set-device-owner com.w3coach.w3coachtab/.KioskAdminReceiver 2>&1
    if (($dpmResult -join "") -match "Success") {
        Write-OK "Device Owner erfolgreich gesetzt"
    } else {
        Write-Err "Device Owner konnte nicht gesetzt werden:"
        Write-Host "  $dpmResult" -ForegroundColor Red
        Write-Host ""
        if (($dpmResult -join "") -match "account") {
            Write-Hint "Ursache: Google-Konto noch aktiv"
            Write-Hint "Bitte Schritt 4 wiederholen und Google-Konto entfernen."
        }
        exit 1
    }
}

Write-Step "7/8" "W3Coach Tab starten"
Write-Waiting "Starte App..."
& adb shell am start -n com.w3coach.w3coachtab/.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 3
$running = & adb shell dumpsys activity 2>&1 | Select-String "com.w3coach.w3coachtab"
if ($running) {
    Write-OK "W3Coach Tab laeuft"
} else {
    Write-Hint "App moeglicherweise noch nicht gestartet - bitte am Geraet pruefen"
}

if ($installWireGuard) {
    Write-Step "8/8" "WireGuard VPN konfigurieren"
    Write-Host ""
    Write-Hint "WireGuard-Konfiguration im App-Menue eingeben:"
    Write-Hint "  Logo-Symbol antippen -> System (PIN) -> VPN konfigurieren"
    Write-Host ""
    Write-Hint "Folgende Werte eingeben:"
    Write-Hint "  Server:      $wgEndpoint"
    Write-Hint "  Server Key:  $wgServerKey"
    Write-Hint "  Client IP:   $wgClientIp"
    Write-Hint "  Private Key: $wgPrivateKey"
    try {
        Set-Clipboard -Value $wgPrivateKey
        Write-Hint "  (Private Key liegt zusaetzlich in der Zwischenablage zum Einfuegen)"
    } catch { }
    Write-Host ""
    Write-Hint "Verbindung testen:"
    Write-Hint "  Logo-Symbol antippen -> 'Quicksupport verbinden'"
} else {
    Write-Step "8/8" "WireGuard uebersprungen"
    Write-OK "WireGuard kann spaeter ueber System-Menue -> VPN konfigurieren eingerichtet werden"
}

Write-Host ""
Write-Host ""
Write-Host "  ============================================================" -ForegroundColor Green
Write-Host "           Installation abgeschlossen!" -ForegroundColor Green
Write-Host "  ============================================================" -ForegroundColor Green
Write-Host ""
Write-OK "W3Coach Tab installiert und als Device Owner gesetzt"
Write-OK "App startet automatisch nach jedem Neustart"
Write-OK "Auto-Update: im Menue konfigurieren"
Write-Host ""
Write-Hint "Naechste Schritte am Geraet:"
Write-Hint "  1. Logo-Symbol antippen -> System (PIN) -> URL 1/2/3 konfigurieren"
Write-Hint "  2. Logo-Symbol antippen -> Auto-Update aktivieren"
if ($installWireGuard) {
    Write-Hint "  3. Logo-Symbol antippen -> System (PIN) -> VPN konfigurieren"
}
Write-Host ""
Write-Host "  Geraet ist einsatzbereit." -ForegroundColor Green
Write-Host ""
