param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='original-home-1.2.5-test.1'
$branch='original-home-proof-1.2.5'
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
$stage=Join-Path $project 'validation/original-home-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin home-config-fix-1.2.4
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 git reset --mixed origin/home-config-fix-1.2.4 | Out-Null
 if($LASTEXITCODE -ne 0) { throw 'Cannot base test on original history' }
 python scripts/check-home-scope.py
 if($LASTEXITCODE -ne 0) { throw 'Original app scope verification failed' }
 git add .
 git add --renormalize .
 git commit -m 'Add isolated visible YouTube Home and Chrome handoff experiments for the active website account'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 $commit=git rev-parse HEAD
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.5-original-home-test, code9, com.weiktube.app. Same package and signing certificate as the original app.

New approach: visible original m.youtube.com in an isolated Gecko experiment, plus a separate Chrome Custom Tabs comparison. No InnerTube calls, feed parsing or substitute recommendations in these experiments. The account selected by YouTube in each website session is authoritative; no fixed account name or native API account gate.

First confirm the signed-in original homepage visually; only then enable the player handoff experiment. Gecko uses its own existing website profile with the old config extension suspended. Ordinary first-party website sign-in is tested without changing browser identity, reading credentials or bypassing Google security. Explicit OAuth authorization is not embedded; a Google rejection remains a blocker. Chrome uses its own regular browser session and does not transfer cookies to Gecko.

Install as update, open Du / Konto → Originale YouTube-Startseite testen. Test Gecko first, then Chrome. For Chrome, phase2 offers a toolbar action and, where supported, a long-pressed link action. Neither is claimed to intercept ordinary taps. Gecko's minimal click bridge sends validated public video URLs to the existing player, without parsing the feed. These experiments do not replace the default homepage.

See ORIGINAL-HOME-TEST.md for the device acceptance checklist and limitations. The user will test their active account on their OnePlus; no live authenticated phone verification is claimed. Chrome cannot run Weiktube's network adblocker; existing app adblock/player code is preserved. Original app source protection remains. ARM64 and x86-64, Android8+. No personal account data is bundled. Stable update feed remains unchanged.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.5 — Original-Home und Player-Übergabe testen';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
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
