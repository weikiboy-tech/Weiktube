param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='home-1.2.4-test.1'
$branch='home-connection-1.2.4'
$apk=Join-Path $project 'Weiktube-Home-Test.apk'
$source=Join-Path $project 'Weiktube-Quellcode.zip'
foreach($file in @($apk,$source)) { if(-not (Test-Path -LiteralPath $file)) { throw 'Build, validate and package source before publishing' } }
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksums=Join-Path $project 'validation/home-test-SHA256SUMS.txt'
@("$hash  Weiktube-Home-Test.apk","$((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant())  Weiktube-Quellcode.zip") | Set-Content -LiteralPath $checksums -Encoding ascii
if(-not $Publish) { Write-Output "Prepared test $tag; APK SHA256 $hash. No remote changes."; return }
$env:GCM_INTERACTIVE='never'
$lines="protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$credential=@{}
foreach($line in $lines) { if($line -match '^([^=]+)=(.*)$') { $credential[$matches[1]]=$matches[2] } }
if(-not $credential['password']) { throw 'GitHub authentication unavailable' }
$headers=@{Authorization="Bearer $($credential['password'])";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28'}
$profile=Invoke-RestMethod 'https://api.github.com/user' -Headers $headers
if($profile.login -ne 'weikiboy-tech') { throw 'Unexpected authenticated owner' }
$releases=Invoke-RestMethod "https://api.github.com/repos/$repo/releases?per_page=100" -Headers $headers
if($releases | Where-Object tag_name -eq $tag) { throw 'Test release exists; refusing overwrite' }
$remote="https://github.com/$repo.git"
$refs=git ls-remote $remote "refs/heads/$branch" "refs/tags/$tag"
if($LASTEXITCODE -ne 0 -or $refs) { throw 'Cannot verify unused test refs' }
$stage=Join-Path $project 'validation/home-test-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin restore-original-1.2.3
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 git reset --mixed origin/restore-original-1.2.3 | Out-Null
 if($LASTEXITCODE -ne 0) { throw 'Cannot base test on original history' }
 python scripts/check-home-scope.py
 if($LASTEXITCODE -ne 0) { throw 'Original app scope verification failed' }
 git add .
 git add --renormalize .
 git commit -m 'Add explicit account-only Home connection proof while preserving original homepage and player'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 $commit=git rev-parse HEAD
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.4-home-test, code7, com.weiktube.app. This updates the ORIGINAL app, not the withdrawn separate Test app.

Only a deliberate connection test was added under Du / Konto → YouTube-Startseite verbinden. Original Home, player, miniplayer, video navigation, actions and shield behavior remain. This is phase1 of the agreed plan: first prove regular Google/YouTube website login, matching profile and real Home data with pagination. The new data source is NOT yet connected to the native homepage.

Google login/private endpoint compatibility has NOT been verified on the target phone. A real device test is required. No Chrome-cookie import, TV OAuth credentials, changed browser identity or substitute recommendation feed. Existing Google API grant is unchanged. The additional engine is used only for explicit login/data checks and never as a replacement player/Home UI.

Install as update, open Du, load your existing account, choose YouTube-Startseite verbinden, sign in to the same YouTube profile, then Verbindung prüfen and Weitere Ergebnisse prüfen. Use the native proof preview to check an actual returned video in the original player. Close and reopen to check session persistence. Report visible error codes only; never share cookies, passwords or tokens.

See HOME-CONNECTION-TEST.md in the matching source for exact acceptance steps and limitations. APK supports ARM64 phones and x86-64 emulators, Android8+. Larger APK includes the internal engine. No personal account data is bundled. Stable update feed remains unchanged.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.4 — Homepage-Verbindungstest';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
$release=Invoke-RestMethod "https://api.github.com/repos/$repo/releases" -Headers $headers -Method Post -ContentType 'application/json' -Body $body
$upload=$release.upload_url -replace '\{.*$', ''
if(-not $upload.StartsWith('https://uploads.github.com/')) { throw 'Unexpected upload origin' }
foreach($asset in @(@{path=$apk;name='Weiktube-Home-Test.apk';type='application/vnd.android.package-archive'},@{path=$source;name='Weiktube-Quellcode.zip';type='application/zip'},@{path=$checksums;name='SHA256SUMS.txt';type='text/plain'})) {
 $result=Invoke-RestMethod "$($upload)?name=$($asset.name)" -Headers $headers -Method Post -ContentType $asset.type -InFile $asset.path
 if($result.size -ne (Get-Item -LiteralPath $asset.path).Length) { throw 'Asset size mismatch' }
 if($result.digest -and $result.digest -ne "sha256:$((Get-FileHash -LiteralPath $asset.path -Algorithm SHA256).Hash.ToLowerInvariant())") { throw 'Asset hash mismatch' }
}
$published=Invoke-RestMethod "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false,"prerelease":true,"make_latest":"false"}'
Write-Output "Published: $($published.html_url)"
Write-Output "APK: https://github.com/$repo/releases/download/$tag/Weiktube-Home-Test.apk"
