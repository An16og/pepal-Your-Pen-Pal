# pepal PowerShell Smoke Test Suite
param(
  [string]$BaseUrl = "http://localhost:8080"
)

$FailedTests = 0
$TotalTests = 0

Write-Host "=== Running pepal API Smoke Tests against $BaseUrl ===" -ForegroundColor Cyan

function Check-Endpoint {
  param(
    [string]$Name,
    [int]$ExpectedStatus,
    [string]$Method,
    [string]$Path,
    [string]$Body = $null
  )
  $script:TotalTests++
  $url = "$BaseUrl$Path"
  $headers = @{ "Accept" = "application/json" }
  if ($Body) { $headers["Content-Type"] = "application/json" }

  try {
    $params = @{
      Uri = $url
      Method = $Method
      Headers = $headers
      UseBasicParsing = $true
      ErrorAction = "Stop"
    }
    if ($Body) { $params["Body"] = $Body }
    $resp = Invoke-WebRequest @params
    $actual = [int]$resp.StatusCode
  } catch {
    if ($_.Exception.Response) {
      $actual = [int]$_.Exception.Response.StatusCode
    } else {
      $actual = 0
    }
  }

  if ($actual -eq $ExpectedStatus) {
    Write-Host "  [PASS] $Name (Status: $actual)" -ForegroundColor Green
    return $true
  } else {
    Write-Host "  [FAIL] $Name (Expected: $ExpectedStatus, Got: $actual)" -ForegroundColor Red
    $script:FailedTests++
    return $false
  }
}

# 1. ENTRIES: Get history
Write-Host "`n--- Entries API ---" -ForegroundColor Yellow
Check-Endpoint -Name "GET /api/entries returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/entries"

# 2. ENTRIES: Create daily reflection
$today = (Get-Date).ToString("yyyy-MM-dd")
$dailyBody = @{
  body = "Smoke test daily reflection: Aaj ka din kafi shaant tha! Backticks test code included."
  entryType = "DAILY_PROMPT"
  entryDate = $today
  energy = "HIGH"
  mood = "GOOD"
} | ConvertTo-Json

Check-Endpoint -Name "POST /api/entries (daily) returns 201" -ExpectedStatus 201 -Method "POST" -Path "/api/entries" -Body $dailyBody

# 3. ENTRIES: Duplicate daily reflection
$dupBody = @{
  body = "Duplicate daily reflection"
  entryType = "DAILY_PROMPT"
  entryDate = $today
} | ConvertTo-Json

Check-Endpoint -Name "POST /api/entries duplicate daily returns 409" -ExpectedStatus 409 -Method "POST" -Path "/api/entries" -Body $dupBody

# 4. ENTRIES: Invalid inputs
$blankBody = @{
  body = "   "
  entryDate = $today
} | ConvertTo-Json
Check-Endpoint -Name "POST /api/entries with blank body returns 400" -ExpectedStatus 400 -Method "POST" -Path "/api/entries" -Body $blankBody

$invalidMood = @{
  body = "Valid body"
  entryDate = $today
  mood = "INVALID"
} | ConvertTo-Json
Check-Endpoint -Name "POST /api/entries with invalid mood returns 400" -ExpectedStatus 400 -Method "POST" -Path "/api/entries" -Body $invalidMood

# 5. ENTRIES: Create freeform
$freeBody = @{
  body = "Freeform smoke test entry: 100% offline local AI journal."
  entryType = "FREEFORM"
  entryDate = $today
  energy = "LOW"
  mood = "BAD"
} | ConvertTo-Json

Check-Endpoint -Name "POST /api/entries (freeform) returns 201" -ExpectedStatus 201 -Method "POST" -Path "/api/entries" -Body $freeBody

# 6. TRASH: Trash listing
Write-Host "`n--- Trash API ---" -ForegroundColor Yellow
Check-Endpoint -Name "GET /api/trash returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/trash"

# 7. PROMPTS: Today prompt & caching
Write-Host "`n--- Daily Prompts API ---" -ForegroundColor Yellow
Check-Endpoint -Name "GET /api/prompts/today returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/prompts/today"
Check-Endpoint -Name "GET /api/prompts/today cached returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/prompts/today"
Check-Endpoint -Name "GET /api/prompts/today future date returns 400" -ExpectedStatus 400 -Method "GET" -Path "/api/prompts/today?date=2099-01-01"

# 8. SETTINGS API
Write-Host "`n--- Settings API ---" -ForegroundColor Yellow
Check-Endpoint -Name "GET /api/settings returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/settings"

$coachBody = @{
  personaPreset = "COACH"
  language = "EN"
} | ConvertTo-Json
Check-Endpoint -Name "PUT /api/settings (COACH) returns 200" -ExpectedStatus 200 -Method "PUT" -Path "/api/settings" -Body $coachBody

$blankCustom = @{
  personaPreset = "CUSTOM"
  customPersona = "   "
  language = "EN"
} | ConvertTo-Json
Check-Endpoint -Name "PUT /api/settings (CUSTOM blank) returns 400" -ExpectedStatus 400 -Method "PUT" -Path "/api/settings" -Body $blankCustom

Check-Endpoint -Name "GET /api/settings/preview returns 200" -ExpectedStatus 200 -Method "GET" -Path "/api/settings/preview?preset=GENTLE&language=EN"

# Reset settings to GENTLE
$resetBody = @{ personaPreset = "GENTLE"; language = "EN" } | ConvertTo-Json
try { Invoke-RestMethod -Uri "$BaseUrl/api/settings" -Method Put -Body $resetBody -ContentType "application/json" | Out-Null } catch {}

Write-Host "`n=== Smoke Test Summary ===" -ForegroundColor Cyan
Write-Host "Total checks: $TotalTests"
Write-Host "Failed checks: $FailedTests"

if ($FailedTests -eq 0) {
  Write-Host "All live API smoke tests PASSED!" -ForegroundColor Green
  exit 0
} else {
  Write-Host "Some smoke tests FAILED!" -ForegroundColor Red
  exit 1
}
