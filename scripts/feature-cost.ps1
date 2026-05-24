<#
.SYNOPSIS
    Reports Claude Code token spend for the current feature branch.

.DESCRIPTION
    Calculates the spend from the date this branch diverged from Develop to today.
    Output is formatted as a markdown ## Usage Cost block — paste it into the PR description.

.EXAMPLE
    .\scripts\feature-cost.ps1
#>

$ErrorActionPreference = "Stop"

# Resolve project name as codeburn sees it (path relative to home directory)
$userHome = [System.IO.Path]::GetFullPath($env:USERPROFILE)
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$projectName = $repo.Substring($userHome.Length).TrimStart('\', '/').Replace('\', '/')

# Find the commit where this branch diverged from Develop
$mergeBase = git merge-base HEAD origin/Develop 2>$null
if (-not $mergeBase) {
    # Fall back to local Develop if remote not fetched
    $mergeBase = git merge-base HEAD Develop 2>$null
}
if (-not $mergeBase) {
    Write-Error "Cannot find merge-base with Develop. Ensure origin/Develop exists or run 'git fetch'."
    exit 1
}

$divergeDate = git log --format="%ad" --date=short -1 $mergeBase
$branch = git branch --show-current

Write-Host ""
Write-Host "Branch  : $branch"
Write-Host "Project : $projectName"
Write-Host "From    : $divergeDate (branch diverged from Develop)"
Write-Host ""

codeburn --project $projectName --from $divergeDate
