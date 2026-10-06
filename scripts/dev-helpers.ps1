# Outils de test local. Usage :  . .\scripts\dev-helpers.ps1   puis   Init
$base = "http://localhost:8080"

function WaitApi { do { Start-Sleep -Seconds 5; $st = try { (Invoke-RestMethod "$base/actuator/health").status } catch { "DOWN" } } until ($st -eq "UP"); "API prete" }

function LoginAs($email) {
  $b = @{ email=$email; password="motdepasse123" } | ConvertTo-Json
  $r = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $b
  @{ Authorization = "Bearer $($r.token)" }
}

function Call($method, $path, $headers, $body) {
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Method $method -Uri "$base$path" -Headers $headers -ContentType "application/json" -Body $body
    $json = if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null }
    [pscustomobject]@{ Code = [int]$r.StatusCode; Body = $json }
  } catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
    [pscustomobject]@{ Code = $code; Body = $null }
  }
}

function Sql($q) { ((docker compose exec -T db psql -U footbooking -d footbooking -t -A -c $q) -join " ").Trim() }

function Sign($message, $secret) {
  $hm = New-Object System.Security.Cryptography.HMACSHA256
  $hm.Key = [Text.Encoding]::UTF8.GetBytes($secret)
  (($hm.ComputeHash([Text.Encoding]::UTF8.GetBytes($message)) | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Ipn($fields) {
  try { [int](Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$base/api/payments/paytech/ipn" -Body $fields).StatusCode }
  catch { if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 } }
}

function IpnFor($ref, $price, $event) {
  Ipn @{ type_event=$event; ref_command=$ref; item_price=$price; currency="XOF"; payment_method="Orange Money"; hmac_compute=(Sign "$price|$ref|test-key" "test-secret") }
}

function NewBooking($client, $start) {
  $r = Call Post "/api/bookings" $client (@{ fieldId=$f.id; start=$start } | ConvertTo-Json)
  if ($r.Code -ne 201) { throw "reservation impossible pour $start : code $($r.Code)" }
  $r.Body
}

function PayBooking($client, $id) {
  $r = Call Post "/api/bookings/$id/pay" $client $null
  if ($r.Code -ne 200) { throw "paiement impossible : code $($r.Code)" }
  $r.Body
}

function RefOf($id) {
  $x = Sql "SELECT provider_ref FROM payment WHERE booking_id='$id' ORDER BY created_at DESC LIMIT 1;"
  if (-not $x) { throw "aucun paiement pour la reservation $id" }
  $x
}

function State($id) { "booking=" + (Sql "SELECT status FROM booking WHERE id='$id';") + " / payment=" + (Sql "SELECT status FROM payment WHERE booking_id='$id' ORDER BY created_at DESC LIMIT 1;") }

function PayRow($id) { Sql "SELECT status || ' / tentatives=' || refund_attempts FROM payment WHERE booking_id='$id' ORDER BY created_at DESC LIMIT 1;" }

function Init {
  $global:h  = LoginAs "owner@test.com"
  $global:c1 = LoginAs "client1@test.com"
  $global:c2 = LoginAs "client2@test.com"
  $global:ha = LoginAs "admin@test.com"
  $v = (Invoke-RestMethod -Uri "$base/api/owner/venues" -Headers $global:h) | Where-Object { $_.name -eq "Stade Pikine" }
  $global:f = (Invoke-RestMethod -Uri "$base/api/owner/venues/$($v.id)/fields" -Headers $global:h)[0]
  "terrain : " + $global:f.id
}