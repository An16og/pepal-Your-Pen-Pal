# pepal RAG quality smoke eval — prints questions + answers for manual review
param(
  [string]$BaseUrl = "http://localhost:8080"
)

$questions = @(
  "What drained my energy in September?",
  "What helped me recharge?",
  "Did I go cycling?",
  "Who did I talk to on the phone?",
  "What happened on 28 September 2026?",
  "What did I write on 2026-09-15?",
  "How has my sleep been?",
  "Tell me about my focus and social media habit",
  "Aaj kal energy kaisi lagi?",
  "Did I mention Dijkstra?"
)

Write-Host "RAG eval against $BaseUrl" -ForegroundColor Cyan
Write-Host ("=" * 72)

foreach ($q in $questions) {
  Write-Host "`nQ: $q" -ForegroundColor Yellow
  $payload = @{ message = $q } | ConvertTo-Json
  try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/chat" -Method Post -ContentType "application/json" -Body $payload -TimeoutSec 180
    Write-Host "A: $($res.answer)" -ForegroundColor Green
  } catch {
    Write-Host "A: ERROR $($_.Exception.Message)" -ForegroundColor Red
  }
}

Write-Host "`nDone." -ForegroundColor Cyan
