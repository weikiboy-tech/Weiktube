param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='home-1.2.4-test.2'
$branch='home-config-fix-1.2.4'
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
$stage=Join-Path $project 'validation/home-config-fix-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin home-connection-1.2.4
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 git reset --mixed origin/home-connection-1.2.4 | Out-Null
 if($LASTEXITCODE -ne 0) { throw 'Cannot base test on original history' }
 python scripts/check-home-scope.py
 if($LASTEXITCODE -ne 0) { throw 'Original app scope verification failed' }
 git add .
 git add --renormalize .
 git commit -m 'Fix Home configuration diagnostics and separate website session from native OAuth'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 $commit=git rev-parse HEAD
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.4-home-test.2, code8, com.weiktube.app. Same package and signing certificate as the original app and test.1.

Targeted CONFIG_UNAVAILABLE fix. The previous initial signin URL reproduced a redirect to YouTube /oops (HTTP 200, no configuration). The connection now starts at the mobile homepage and distinguishes error pages, consent, missing website authentication, unsupported configuration, wrong profile and unavailable feed data. Config parsing accepts supported page-provided variants without executing page scripts.

Opt-in diagnostics show sanitized navigation categories, observed HTTP status when available, JavaScript readiness and missing configuration fields. No passwords, cookies, tokens or complete authenticated responses are logged. Google authentication pages are not loaded in the embedded engine. External browser checks do not transfer their session into the app. Existing native Google API authorization remains unchanged and does not expose the personalized YouTube homepage.

Install as update, open Du / Konto → YouTube-Startseite verbinden, enable Diagnosemodus and press Verbindung prüfen. A missing website session is a real blocker, not a successful connection. Only a confirmed matching website profile can yield original Home entries and their continuations; no subscription replacement is returned. The native homepage has not been switched to an unverified feed.

See HOME-CONNECTION-TEST.md in the source for reproduction, local checks and remaining limitations. No live phone/account verification was possible. Original player, miniplayer, UI navigation and adblock sources are protected by the scope check. APK supports ARM64 and x86-64, Android8+. No personal account data is bundled. Stable update feed remains unchanged.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.4 — CONFIG_UNAVAILABLE Diagnose-Fix';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
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
