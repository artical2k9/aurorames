#!/usr/bin/env pwsh
# Generates Aurora MES module icon SVGs in dark and light variants.
# Output: docs/brand/icons/{dark,light}/ic-{module}.svg

$outBase = Join-Path $PSScriptRoot ".." "docs" "brand" "icons"
New-Item -ItemType Directory -Force "$outBase/dark" | Out-Null
New-Item -ItemType Directory -Force "$outBase/light" | Out-Null

# Icon inner paths — each designed on a 48×48 canvas, fill="none", stroke="{{COLOR}}"
# stroke-width="2", stroke-linecap="round", stroke-linejoin="round"
$icons = [ordered]@{

  "production" = @'
<rect x="10" y="26" width="28" height="18" rx="2"/>
<path d="M10 26 L24 15 L38 26"/>
<rect x="14" y="14" width="5" height="12"/>
<rect x="26" y="17" width="5" height="9"/>
<rect x="21" y="34" width="6" height="10"/>
'@

  "flow" = @'
<rect x="4" y="18" width="10" height="12" rx="2"/>
<rect x="19" y="18" width="10" height="12" rx="2"/>
<rect x="34" y="18" width="10" height="12" rx="2"/>
<line x1="14" y1="24" x2="19" y2="24"/>
<path d="M17 21 L19 24 L17 27"/>
<line x1="29" y1="24" x2="34" y2="24"/>
<path d="M32 21 L34 24 L32 27"/>
'@

  "quality" = @'
<path d="M24 4 L40 10 L40 26 Q40 37 24 44 Q8 37 8 26 L8 10 Z"/>
<path d="M15 24 L21 31 L33 17"/>
'@

  "trace" = @'
<circle cx="20" cy="20" r="12"/>
<line x1="29" y1="29" x2="43" y2="43"/>
'@

  "inventory" = @'
<path d="M12 22 L36 22 L36 42 L12 42 Z"/>
<path d="M12 22 L20 14 L44 14 L36 22"/>
<path d="M36 22 L44 14 L44 34 L36 42"/>
<line x1="24" y1="22" x2="24" y2="42"/>
<line x1="12" y1="32" x2="36" y2="32"/>
'@

  "maintain" = @'
<path d="M32 8 C38 8 44 13 44 20 C44 23 42 26 39 28 L22 45 C20 47 17 47 15 45 C13 43 13 40 15 38 L32 21 C30 18 30 12 32 8 Z"/>
<circle cx="34" cy="17" r="4" fill="none"/>
'@

  "materials" = @'
<path d="M8 36 L24 43 L40 36 L24 29 Z"/>
<path d="M8 28 L24 35 L40 28 L24 21 Z"/>
<path d="M8 20 L24 27 L40 20 L24 13 Z"/>
'@

  "compliance" = @'
<rect x="10" y="10" width="28" height="34" rx="2"/>
<path d="M18 6 L18 14 L30 14 L30 6"/>
<line x1="16" y1="22" x2="32" y2="22"/>
<line x1="16" y1="29" x2="32" y2="29"/>
<path d="M16 36 L19 40 L26 32"/>
'@

  "vault" = @'
<rect x="10" y="24" width="28" height="20" rx="3"/>
<path d="M17 24 L17 17 Q17 9 24 9 Q31 9 31 17 L31 24"/>
<circle cx="24" cy="33" r="3"/>
<line x1="24" y1="36" x2="24" y2="42"/>
'@

  "connect" = @'
<circle cx="24" cy="24" r="4"/>
<circle cx="8" cy="12" r="3"/>
<circle cx="40" cy="12" r="3"/>
<circle cx="8" cy="36" r="3"/>
<circle cx="40" cy="36" r="3"/>
<line x1="11" y1="14" x2="21" y2="21"/>
<line x1="37" y1="14" x2="27" y2="21"/>
<line x1="11" y1="34" x2="21" y2="27"/>
<line x1="37" y1="34" x2="27" y2="27"/>
'@

  "insight" = @'
<line x1="4" y1="43" x2="44" y2="43"/>
<line x1="4" y1="5" x2="4" y2="43"/>
<rect x="8" y="28" width="8" height="15"/>
<rect x="20" y="20" width="8" height="23"/>
<rect x="32" y="13" width="8" height="30"/>
<path d="M8 30 L24 21 L40 14" stroke-dasharray="0"/>
'@

  "ai" = @'
<path d="M24 8 C16 8 10 13 10 20 C10 25 13 27 13 27 C11 30 9 34 10 38 C11 42 15 44 18 42 C20 45 22 46 24 46 C26 46 28 45 30 42 C33 44 37 42 38 38 C39 34 37 30 35 27 C35 27 38 25 38 20 C38 13 32 8 24 8 Z"/>
<circle cx="18" cy="22" r="1.5"/>
<circle cx="24" cy="18" r="1.5"/>
<circle cx="30" cy="24" r="1.5"/>
<line x1="18" y1="22" x2="24" y2="18"/>
<line x1="24" y1="18" x2="30" y2="24"/>
<line x1="18" y1="22" x2="18" y2="30"/>
'@

  "reports" = @'
<path d="M10 4 L32 4 L38 10 L38 44 L10 44 Z"/>
<path d="M32 4 L32 10 L38 10"/>
<line x1="16" y1="18" x2="32" y2="18"/>
<line x1="16" y1="24" x2="32" y2="24"/>
<line x1="16" y1="30" x2="26" y2="30"/>
<rect x="16" y="34" width="4" height="6"/>
<rect x="22" y="30" width="4" height="10"/>
<rect x="28" y="26" width="4" height="14"/>
'@

  "suppliers" = @'
<rect x="4" y="18" width="24" height="18" rx="1"/>
<path d="M28 24 L28 18 L36 18 L44 30 L44 36 L28 36 Z"/>
<circle cx="12" cy="38" r="4"/>
<circle cx="36" cy="38" r="4"/>
<line x1="4" y1="36" x2="28" y2="36"/>
'@

  "training" = @'
<path d="M24 6 L44 16 L24 26 L4 16 Z"/>
<path d="M32 21 L32 36 Q32 40 24 40 Q16 40 16 36 L16 21"/>
<line x1="44" y1="16" x2="44" y2="30"/>
<circle cx="44" cy="32" r="2"/>
'@

  "ncr" = @'
<path d="M24 5 L45 42 L3 42 Z"/>
<line x1="24" y1="18" x2="24" y2="30"/>
<circle cx="24" cy="35" r="1.5"/>
'@

  "calibration" = @'
<circle cx="24" cy="24" r="18"/>
<circle cx="24" cy="24" r="10"/>
<circle cx="24" cy="24" r="2"/>
<line x1="6" y1="24" x2="14" y2="24"/>
<line x1="34" y1="24" x2="42" y2="24"/>
<line x1="24" y1="6" x2="24" y2="14"/>
<line x1="24" y1="34" x2="24" y2="42"/>
'@

  "audit" = @'
<path d="M8 6 L30 6 L36 12 L36 38 L8 38 Z"/>
<path d="M30 6 L30 12 L36 12"/>
<line x1="14" y1="18" x2="30" y2="18"/>
<line x1="14" y1="24" x2="30" y2="24"/>
<line x1="14" y1="30" x2="22" y2="30"/>
<circle cx="34" cy="36" r="7"/>
<line x1="39" y1="41" x2="44" y2="46"/>
'@

  "change" = @'
<path d="M8 24 A16 16 0 0 1 32 8"/>
<path d="M30 5 L32 8 L29 11"/>
<path d="M40 24 A16 16 0 0 1 16 40"/>
<path d="M18 43 L16 40 L19 37"/>
'@

  "thread" = @'
<path d="M8 6 Q4 6 4 10 L4 24 Q4 28 8 28 L10 28 L10 33 L16 28 L28 28 Q32 28 32 24 L32 10 Q32 6 28 6 Z"/>
<path d="M18 20 Q16 20 16 24 L16 36 Q16 40 20 40 L26 40 L26 45 L32 40 L40 40 Q44 40 44 36 L44 22 Q44 18 40 18 L20 18 Q18 18 18 20 Z"/>
'@

  "command" = @'
<rect x="4" y="6" width="40" height="28" rx="3"/>
<path d="M18 38 L16 44 L32 44 L30 38"/>
<line x1="12" y1="44" x2="36" y2="44"/>
<path d="M12 15 L18 20 L12 25"/>
<line x1="22" y1="25" x2="34" y2="25"/>
'@
}

$darkStroke  = "#2E8BF5"
$lightStroke = "#2563EB"

$template = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none"
     stroke="{{STROKE}}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <title>Aurora MES — {{TITLE}}</title>
{{PATHS}}
</svg>
'@

foreach ($name in $icons.Keys) {
    $title  = (Get-Culture).TextInfo.ToTitleCase($name.Replace("-"," "))
    $paths  = $icons[$name]

    $darkContent  = $template `
        -replace '{{STROKE}}', $darkStroke `
        -replace '{{TITLE}}',  $title `
        -replace '{{PATHS}}',  $paths

    $lightContent = $template `
        -replace '{{STROKE}}', $lightStroke `
        -replace '{{TITLE}}',  $title `
        -replace '{{PATHS}}',  $paths

    $darkContent  | Set-Content "$outBase/dark/ic-$name.svg"  -Encoding UTF8
    $lightContent | Set-Content "$outBase/light/ic-$name.svg" -Encoding UTF8
}

Write-Host "Generated $($icons.Count) icons × 2 modes = $($icons.Count * 2) files in docs/brand/icons/"
