#requires -Version 7.0
<#
.SYNOPSIS
    Idempotent dev/test data seed for Aurora MES — Item Master parts and BOMs.

.DESCRIPTION
    Re-creates a small, realistic set of Item Master records and Bills of Material
    through the gateway API (not direct SQL), so it survives a Postgres volume
    reset and stays consistent with all service-side validation and audit rules.

    The script is idempotent: every part and BOM is checked via a GET first and
    only created when missing. Re-running it makes no changes once the data exists.

    Auth uses the Keycloak direct-access (password) grant on the public
    'mes-frontend' client, signing in as the dev SYSTEM_ADMIN seeded in the realm.

.EXAMPLE
    ./scripts/seed-dev-data.ps1

.EXAMPLE
    ./scripts/seed-dev-data.ps1 -GatewayUrl http://localhost:8082 -Username admin@test.org

.NOTES
    Dev/test only. The default credentials are the dev realm seed values committed
    in keycloak/mes-realm.json — never point this at a production realm.
#>
[CmdletBinding()]
param(
    [string] $GatewayUrl  = $env:SEED_GATEWAY_URL  ?? 'http://localhost:8082',
    [string] $KeycloakUrl = $env:SEED_KEYCLOAK_URL ?? 'http://localhost:8080',
    [string] $Realm       = $env:SEED_REALM        ?? 'mes',
    [string] $ClientId    = $env:SEED_CLIENT_ID    ?? 'mes-frontend',
    [string] $Username    = $env:SEED_USERNAME     ?? 'admin@test.org',
    [string] $Password    = $env:SEED_PASSWORD     ?? 'Admin123!'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── Auth ─────────────────────────────────────────────────────────────────────

function Get-AccessToken {
    Write-Host "→ Authenticating as $Username at $KeycloakUrl/realms/$Realm" -ForegroundColor Cyan
    try {
        $resp = Invoke-RestMethod -Method Post `
            -Uri "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{
                grant_type = 'password'
                client_id  = $ClientId
                username   = $Username
                password   = $Password
            }
    }
    catch {
        throw "Failed to obtain access token from Keycloak. Is the stack up? $($_.Exception.Message)"
    }
    return $resp.access_token
}

$script:AuthHeaders = $null

function Invoke-Api {
    param(
        [Parameter(Mandatory)] [ValidateSet('GET', 'POST', 'PATCH', 'DELETE')] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [object] $Body
    )
    $uri = "$GatewayUrl$Path"
    $args = @{
        Method  = $Method
        Uri     = $uri
        Headers = $script:AuthHeaders
    }
    if ($null -ne $Body) {
        $args.ContentType = 'application/json'
        $args.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod @args
}

function Get-EscapedQuery([string] $value) {
    return [uri]::EscapeDataString($value)
}

# ── Item Master ──────────────────────────────────────────────────────────────

# Each definition is the create request plus a logical key (partNumber) used for
# the idempotency lookup. Components are seeded before the assemblies that use them.
$itemDefs = @(
    @{ partNumber = 'RM-AL6061-BAR'; revision = 'A'; description = 'Aluminium 6061-T6 bar stock, 25mm';
       unitOfMeasure = 'M'; cageCode = 'DEV01'; classification = 'RAW_MATERIAL';
       makeBuyCode = 'BUY'; traceabilityMethod = 'HEAT_CODE'; counterfeitRiskLevel = 'LOW' }
    @{ partNumber = 'PP-BOLT-M6'; revision = 'A'; description = 'Hex bolt M6x20 A2-70 stainless';
       unitOfMeasure = 'EA'; cageCode = 'DEV01'; classification = 'PURCHASED_PART';
       makeBuyCode = 'BUY'; traceabilityMethod = 'LOT'; counterfeitRiskLevel = 'MEDIUM' }
    @{ partNumber = 'FB-BRKT-1001'; revision = 'A'; description = 'Machined mounting bracket';
       unitOfMeasure = 'EA'; cageCode = 'DEV01'; classification = 'FABRICATED';
       makeBuyCode = 'MAKE'; traceabilityMethod = 'SERIAL'; counterfeitRiskLevel = 'LOW' }
    @{ partNumber = 'AS-SUBASSY-2001'; revision = 'A'; description = 'Bracket sub-assembly';
       unitOfMeasure = 'EA'; cageCode = 'DEV01'; classification = 'ASSEMBLY';
       makeBuyCode = 'MAKE'; traceabilityMethod = 'SERIAL'; counterfeitRiskLevel = 'LOW' }
    @{ partNumber = 'AS-TOPASSY-3000'; revision = 'A'; description = 'Top-level airframe assembly';
       unitOfMeasure = 'EA'; cageCode = 'DEV01'; classification = 'ASSEMBLY';
       makeBuyCode = 'MAKE'; traceabilityMethod = 'SERIAL'; counterfeitRiskLevel = 'LOW' }
)

function Approve-Item([string] $itemId) {
    Invoke-Api POST "/api/v1/item-master/$itemId/submit" @{} | Out-Null
    return Invoke-Api POST "/api/v1/item-master/$itemId/approve" @{}
}

function Find-Item([string] $partNumber) {
    $page = Invoke-Api GET "/api/v1/item-master?search=$(Get-EscapedQuery $partNumber)&size=50"
    return $page.content | Where-Object { $_.partNumber -eq $partNumber } | Select-Object -First 1
}

function Ensure-Item($def) {
    $existing = Find-Item $def.partNumber
    if ($existing) {
        if ($existing.revisionStatus -ne 'APPROVED') {
            try { $existing = Approve-Item $existing.id }
            catch { Write-Host "    (could not auto-approve $($def.partNumber): $($_.Exception.Message))" -ForegroundColor Yellow }
        }
        Write-Host "  = $($def.partNumber) already present (reused)" -ForegroundColor DarkGray
        return @{ partNumber = $def.partNumber; id = $existing.id; revisionId = $existing.revisionId }
    }

    $created  = Invoke-Api POST '/api/v1/item-master' $def
    $approved = Approve-Item $created.id
    Write-Host "  + $($def.partNumber) created and approved" -ForegroundColor Green
    return @{ partNumber = $def.partNumber; id = $approved.id; revisionId = $approved.revisionId }
}

# ── BOMs ─────────────────────────────────────────────────────────────────────

# parent + ordered component lines (referenced by part number; resolved to the
# approved revision id seeded above).
$bomDefs = @(
    @{ parent = 'AS-SUBASSY-2001'; description = 'Bracket sub-assembly BOM'; lines = @(
        @{ component = 'FB-BRKT-1001'; quantity = 1; unitOfMeasure = 'EA'; findNumber = '10' }
        @{ component = 'PP-BOLT-M6';   quantity = 4; unitOfMeasure = 'EA'; findNumber = '20' }
    ) }
    @{ parent = 'AS-TOPASSY-3000'; description = 'Top-level airframe BOM'; lines = @(
        @{ component = 'AS-SUBASSY-2001'; quantity = 2; unitOfMeasure = 'EA'; findNumber = '10' }
        @{ component = 'RM-AL6061-BAR';   quantity = 1; unitOfMeasure = 'M';  findNumber = '20' }
    ) }
)

function Find-Bom([string] $parentItemId, [string] $parentPartNumber) {
    $page = Invoke-Api GET "/api/v1/boms/headers?search=$(Get-EscapedQuery $parentPartNumber)&size=50"
    return $page.content | Where-Object { $_.parentItemId -eq $parentItemId } | Select-Object -First 1
}

function Ensure-Bom($def, $itemsByPartNumber) {
    $parent = $itemsByPartNumber[$def.parent]
    if (-not $parent) { throw "BOM parent '$($def.parent)' was not seeded — cannot create BOM." }

    if (Find-Bom $parent.id $def.parent) {
        Write-Host "  = BOM for $($def.parent) already present (skipped)" -ForegroundColor DarkGray
        return
    }

    $bom = Invoke-Api POST '/api/v1/boms' @{ parentItemId = $parent.id; description = $def.description }
    foreach ($line in $def.lines) {
        $component = $itemsByPartNumber[$line.component]
        if (-not $component) { throw "BOM component '$($line.component)' was not seeded." }
        Invoke-Api POST "/api/v1/boms/$($bom.id)/lines" @{
            componentItemRevisionId = $component.revisionId
            quantity                = $line.quantity
            unitOfMeasure           = $line.unitOfMeasure
            findNumber              = $line.findNumber
        } | Out-Null
    }
    Invoke-Api POST "/api/v1/boms/$($bom.id)/submit" @{} | Out-Null
    Invoke-Api POST "/api/v1/boms/$($bom.id)/approve" @{} | Out-Null
    Write-Host "  + BOM for $($def.parent) created with $($def.lines.Count) line(s) and approved" -ForegroundColor Green
}

# ── Run ──────────────────────────────────────────────────────────────────────

$token = Get-AccessToken
$script:AuthHeaders = @{ Authorization = "Bearer $token" }

Write-Host "`nSeeding Item Master..." -ForegroundColor Cyan
$itemsByPartNumber = @{}
foreach ($def in $itemDefs) {
    $item = Ensure-Item $def
    $itemsByPartNumber[$item.partNumber] = $item
}

Write-Host "`nSeeding BOMs..." -ForegroundColor Cyan
foreach ($def in $bomDefs) {
    Ensure-Bom $def $itemsByPartNumber
}

Write-Host "`nDone. Seeded $($itemDefs.Count) item(s) and $($bomDefs.Count) BOM(s)." -ForegroundColor Green
