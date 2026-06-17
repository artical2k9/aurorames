#requires -Version 7.0
<#
.SYNOPSIS
    Post-import Keycloak step: set confidential-client secrets that the realm export
    ships blank to the values the services authenticate with.

.DESCRIPTION
    keycloak/mes-realm.json deliberately exports confidential clients with an empty
    secret ("secret": "") so no secret is committed to the repo. Keycloak therefore
    generates a random secret on first --import-realm, which does NOT match the
    MES_*_SECRET env vars the services use. Any flow that depends on such a client
    then fails — e.g. the e-signature Direct Access Grant used by Work Instruction
    and route approval (client 'mes-signature-verify') returns HTTP 422
    SIGNATURE_VERIFICATION_FAILED, because the service authenticates with
    MES_SIGNATURE_VERIFY_SECRET but Keycloak holds a different (random) secret.

    This script reconciles each listed client's secret in Keycloak to the configured
    env value via the admin REST API. It is idempotent: a client whose secret already
    matches is left untouched, so it is safe to re-run.

    Run it once after the stack first comes up, and again after any fresh
    --import-realm (e.g. a Keycloak volume reset / container recreate).

.EXAMPLE
    ./scripts/set-keycloak-client-secrets.ps1

.EXAMPLE
    ./scripts/set-keycloak-client-secrets.ps1 -KeycloakUrl http://localhost:8080 -EnvFile docker/.env

.NOTES
    Dev/test convenience. Admin and client secrets are read from docker/.env (or the
    process environment). Never point this at a production realm with committed dev
    secrets — manage production secrets out of band.
#>
[CmdletBinding()]
param(
    [string] $KeycloakUrl   = $env:KC_URL ?? 'http://localhost:8080',
    [string] $Realm         = 'mes',
    [string] $AdminUser     = $env:KEYCLOAK_ADMIN,
    [string] $AdminPassword = $env:KEYCLOAK_ADMIN_PASSWORD,
    [string] $EnvFile       = (Join-Path $PSScriptRoot '..' 'docker' '.env')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Map of confidential clientId -> name of the env var that holds its secret.
# Add a row here whenever a new confidential client is introduced with a blank
# secret in the realm export.
$clientSecretEnv = [ordered]@{
    'mes-signature-verify' = 'MES_SIGNATURE_VERIFY_SECRET'
}

# ── Resolve secrets from the process environment, then from docker/.env ────────
function Import-EnvFile([string] $path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    foreach ($line in Get-Content $path) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $map[$t.Substring(0, $eq).Trim()] = $t.Substring($eq + 1).Trim()
    }
    return $map
}

$envFromFile = Import-EnvFile $EnvFile
function Resolve-Value([string] $name) {
    $v = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrEmpty($v)) { return $v }
    if ($envFromFile.ContainsKey($name)) { return $envFromFile[$name] }
    return $null
}

if ([string]::IsNullOrEmpty($AdminUser))     { $AdminUser     = (Resolve-Value 'KEYCLOAK_ADMIN') ?? 'admin' }
if ([string]::IsNullOrEmpty($AdminPassword)) { $AdminPassword = Resolve-Value 'KEYCLOAK_ADMIN_PASSWORD' }
if ([string]::IsNullOrEmpty($AdminPassword)) {
    throw "KEYCLOAK_ADMIN_PASSWORD not found in the environment or $EnvFile."
}

# ── Admin token (master realm, admin-cli password grant) ───────────────────────
Write-Host "→ Obtaining Keycloak admin token from $KeycloakUrl" -ForegroundColor Cyan
try {
    $tokenResp = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            grant_type = 'password'
            client_id  = 'admin-cli'
            username   = $AdminUser
            password   = $AdminPassword
        }
}
catch {
    throw "Could not obtain a Keycloak admin token. Is Keycloak up at $KeycloakUrl? $($_.Exception.Message)"
}
$headers = @{ Authorization = "Bearer $($tokenResp.access_token)" }

# ── Reconcile each client secret ───────────────────────────────────────────────
$changed = 0; $skipped = 0
foreach ($clientId in $clientSecretEnv.Keys) {
    $envName = $clientSecretEnv[$clientId]
    $desired = Resolve-Value $envName
    if ([string]::IsNullOrEmpty($desired)) {
        Write-Host "  ! $clientId — $envName is not set; skipping" -ForegroundColor Yellow
        continue
    }

    $clients = Invoke-RestMethod -Method Get -Headers $headers `
        -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$([uri]::EscapeDataString($clientId))"
    if (-not $clients -or $clients.Count -eq 0) {
        Write-Host "  ! $clientId — not found in realm '$Realm'; skipping" -ForegroundColor Yellow
        continue
    }
    $client  = $clients[0]
    $current = if ($client.PSObject.Properties.Name -contains 'secret') { $client.secret } else { $null }

    if ($current -eq $desired) {
        Write-Host "  = $clientId — secret already matches $envName" -ForegroundColor DarkGray
        $skipped++
        continue
    }

    $client.secret = $desired
    Invoke-RestMethod -Method Put -Headers $headers -ContentType 'application/json' `
        -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$($client.id)" `
        -Body ($client | ConvertTo-Json -Depth 30) | Out-Null
    Write-Host "  + $clientId — secret set from $envName" -ForegroundColor Green
    $changed++
}

Write-Host "`nDone. $changed client secret(s) set, $skipped already correct." -ForegroundColor Cyan
