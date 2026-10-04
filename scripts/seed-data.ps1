# pepal Database Seeding Script - 1 Month of Realistic Entries
param(
  [string]$BaseUrl = "http://localhost:8080"
)

$entries = @(
  @{
    date = "2026-09-06"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "GOOD"
    body = "What felt steadying today? A quiet Sunday morning with freshly brewed adrak chai and rain on the window.`n`nIf today was a color: Warm honey amber, slow and unhurried.`n`nTomorrow's intention: Take tasks one at a time without tab-switching."
  },
  @{
    date = "2026-09-07"
    type = "FREEFORM"
    energy = "HIGH"
    mood = "GOOD"
    body = "Offline-first software feels like physical stationery. There are no tracking pixels, no telemetry, no cloud subscriptions. When you close the screen, your memories belong strictly to you."
  },
  @{
    date = "2026-09-08"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "What energised you today? Solved a tricky concurrency issue in the local journal backend. The feeling of tests passing after two hours of debugging was pure dopamine.`n`nPlayful note: Celebrated with extra samosas.`n`nNext step: Write integration tests for the restore conflict path."
  },
  @{
    date = "2026-09-10"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "BAD"
    body = "What drained you? Too many context-switching calls today. My mental battery hit zero by 3 PM.`n`nHonest feeling: Just felt scattered and unable to write deep code.`n`nGentle step: Closing my laptop early and getting a full 8 hours of sleep tonight."
  },
  @{
    date = "2026-09-11"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "GOOD"
    body = "What brought comfort? Took an evening walk near the lake. The cool breeze cleared the lingering brain fog from yesterday.`n`nSmall sensory joy: The smell of petrichor on the mud paths.`n`nForward look: A peaceful Saturday ahead."
  },
  @{
    date = "2026-09-12"
    type = "FREEFORM"
    energy = "LOW"
    mood = "GOOD"
    body = "Midnight thought: Why do we treat rest as something we have to earn? Resting is maintenance, not a reward. Need to remember this on hectic weeks."
  },
  @{
    date = "2026-09-14"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "What felt meaningful? Kicked off architectural planning for the offline companion. Sketched out pgvector cosine similarity pipeline on physical dot-grid paper.`n`nFun detail: Used a fountain pen that hasn't leaked for once.`n`nPriority: Keep the vector dimension strictly at 768."
  },
  @{
    date = "2026-09-15"
    type = "FREEFORM"
    energy = "LOW"
    mood = "BAD"
    body = "Posture check: Slouching at the desk for 6 hours straight gave me a sharp shoulder ache. Setting a 45-minute timer to stand up and stretch."
  },
  @{
    date = "2026-09-16"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "BAD"
    body = "What felt heavy? Dealing with flaky local Docker networking and Windows pipe issues. Struggled to stay patient.`n`nLow energy reflection: Allowed myself to feel annoyed without judging the emotion.`n`nNext step: Step away from the screen for an hour."
  },
  @{
    date = "2026-09-17"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "BAD"
    body = "Reflection: Two low days in a row. Energy is definitely dipping. Feeling that mid-month slump.`n`nSmall comfort: Hot soup and watching an old studio Ghibli movie with no phone in sight.`n`nOne gentle choice: Sleep by 10:30 PM."
  },
  @{
    date = "2026-09-19"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "What broke the slump? Went for a 25 km cycle ride early in the morning before the city woke up. Clear skies and lungs full of fresh air.`n`nPlayful note: Found a hidden roadside stall serving amazing poha.`n`nForward momentum: Feeling rejuvenated and ready for a creative weekend."
  },
  @{
    date = "2026-09-20"
    type = "FREEFORM"
    energy = "HIGH"
    mood = "GOOD"
    body = "Design idea for pepal: The Paper theme needs to feel like thick 120gsm cream notebook stock. Warm off-white #fcfaf7 background with subtle dark charcoal typography. It changes the psychology of writing."
  },
  @{
    date = "2026-09-21"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "Monday start: Knocked out the top three priorities before lunch. Deep focus block with lo-fi beats in the background.`n`nObservation: When I don't check social media first thing in the morning, my focus lasts twice as long.`n`nTomorrow: Review the settings persona prompts."
  },
  @{
    date = "2026-09-23"
    type = "FREEFORM"
    energy = "HIGH"
    mood = "GOOD"
    body = "Hinglish reflection: Aaj coding session me alag hi flow state tha. Jab logic bina kisi friction ke click karta hai na, that feeling is unmatched."
  },
  @{
    date = "2026-09-24"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "Steadying moment: Long phone call with Rohan after almost six months. We laughed about our first hostel hackathon and how much simpler things felt back then.`n`nTakeaway: Maintaining old friendships takes conscious effort, but it pays back in warmth tenfold.`n`nNext: Plan an in-person catch-up next month."
  },
  @{
    date = "2026-09-26"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "GOOD"
    body = "Saturday pace: Slept in till 9 AM. Spent the afternoon reading Ted Chiang's short stories on the balcony.`n`nAnimal mood: A cat dozing in a patch of sunlight on a cool tile floor.`n`nEvening plan: Cook pasta and listen to jazz."
  },
  @{
    date = "2026-09-28"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "BAD"
    body = "Sleep deprivation: Bad insomnia last night. Tossed and turned until 4 AM. Everything feels slightly muted and harder to do today.`n`nObservation: My patience threshold is noticeably thinner when sleep is under 5 hours.`n`nGrace to give myself: Just do the bare minimum today and don't feel guilty."
  },
  @{
    date = "2026-09-29"
    type = "FREEFORM"
    energy = "HIGH"
    mood = "GOOD"
    body = "Quote of the day: 'Simplicity is prerequisite for reliability.' - Edsger W. Dijkstra. This applies to code, software architecture, and personal daily routines."
  },
  @{
    date = "2026-09-30"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "End of September reflection: Journaled on 18 different days this month. Even on chaotic days, having this quiet notebook on my laptop grounded me.`n`nHighlight: The UI feels fast, snappy, and trustworthy.`n`nOctober intention: Keep journaling consistently and balance intense coding with daily evening walks."
  },
  @{
    date = "2026-10-01"
    type = "DAILY_PROMPT"
    energy = "HIGH"
    mood = "GOOD"
    body = "October Day 1: Crisp morning air. Set three clear quarterly goals on paper.`n`nCuriosity: Want to experiment with local LLM quantization and see how Qwen 4B compares to Llama.`n`nForward step: Ship pepal with one-command Docker compose."
  },
  @{
    date = "2026-10-02"
    type = "DAILY_PROMPT"
    energy = "LOW"
    mood = "GOOD"
    body = "National holiday calm: Quiet streets today. Spent time decluttering my physical desk and watering the balcony plants.`n`nSmall comfort: Simple home-cooked dal chawal and ghee for lunch. Nothing beats comfort food.`n`nEvening: Read two chapters of a new fantasy novel."
  },
  @{
    date = "2026-10-03"
    type = "FREEFORM"
    energy = "HIGH"
    mood = "GOOD"
    body = "Late night realization: True privacy isn't about hiding secrets; it's about having a sacred space where you don't have to perform for an algorithm or an audience."
  }
)

Write-Host "Seeding $($entries.Count) journal entries into pepal..." -ForegroundColor Cyan

$success = 0
$failed = 0

foreach ($e in $entries) {
  $payload = @{
    entryDate = $e.date
    entryType = $e.type
    energy = $e.energy
    mood = $e.mood
    body = $e.body
  } | ConvertTo-Json

  try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/entries" -Method Post -ContentType "application/json" -Body $payload
    Write-Host "  [+] Added $($e.date) - $($e.type) [$($e.mood)/$($e.energy)]" -ForegroundColor Green
    $success++
  } catch {
    Write-Host "  [-] Failed $($e.date) - $($_.Exception.Message)" -ForegroundColor Yellow
    $failed++
  }
}

Write-Host "`nSeeding Complete! Successfully added: $success entries (Failed: $failed)" -ForegroundColor Cyan
