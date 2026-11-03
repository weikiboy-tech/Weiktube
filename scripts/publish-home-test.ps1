param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='website-first-1.2.6-test.1'
$branch='website-first-1.2.6'
$baseCommit='87b30c9a38f5ca7cad4106e570d770a84c7ada47'
$apk=Join-Path $project 'Weiktube-Home-Test.apk'
$source=Join-Path $project 'Weiktube-Quellcode.zip'
foreach($file in @($apk,$source)) { if(-not (Test-Path -LiteralPath $file)) { throw 'Build, validate and package source before publishing' } }
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksums=Join-Path $project 'validation/website-first-SHA256SUMS.txt'
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
$stage=Join-Path $project 'validation/website-first-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin original-home-proof-1.2.5
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 $parent=git rev-parse origin/original-home-proof-1.2.5
 if($LASTEXITCODE -ne 0 -or $parent -ne $baseCommit) { throw 'Unexpected base commit; review source history before publishing' }
 python scripts/check-home-scope.py
 if($LASTEXITCODE -ne 0) { throw 'Original app scope verification failed' }
 git add .
 if($LASTEXITCODE -ne 0) { throw 'Cannot stage sanitized source' }
 git add --renormalize .
 if($LASTEXITCODE -ne 0) { throw 'Cannot normalize sanitized source' }
 # This is a newly created, isolated staging repository. Build a commit with the
 # verified parent without resetting, checking out or replacing any working tree.
 $tree=git write-tree
 if($LASTEXITCODE -ne 0) { throw 'Source tree failed' }
 $commit=git commit-tree $tree -p $baseCommit -m 'Make website sign-in the primary entry and add focused Gecko Autofill assistance'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 git update-ref "refs/heads/$branch" $commit ('0' * 40)
 if($LASTEXITCODE -ne 0) { throw 'New staging branch creation failed' }
 $sourceStatus=git status --porcelain
 if($LASTEXITCODE -ne 0 -or $sourceStatus) { throw 'Staged source does not match commit' }
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.6-website-first-test, code10, com.weiktube.app. Same package and signing certificate as the original app. Install as an update; do not uninstall to preserve the website profile.

Under Du / Konto, YouTube-Startseite öffnen is the primary entry. It opens the existing original m.youtube.com experiment with the persistent Gecko profile. Native subscriptions/playlists authorization is optional and requested only on user action; existing API grants keep their current silent restore behavior. The native account card is explicitly for subscriptions/playlists and may differ from the active website account.

On a regular website sign-in page, focus the real email/password field and use Zugangsdaten vorschlagen to request the selected Android Autofill provider for that virtual input. Optional Autofill diagnostics show capability, focus and UI availability only. No credentials, tokens, cookies or form values are logged. Gecko Autofill was already enabled; this adds a focused request and diagnostics, not a promise that a provider will supply credentials.

There is no supported Google API token or Chrome-cookie transfer into Gecko in this app. First website sign-in is still required when no valid Gecko website session exists. Google may reject an embedded sign-in; no browser-identity spoof or security bypass is used. Explicit OAuth authorization remains outside the embedded website. Website session reuse lasts while Google accepts it.

See WEBSITE-FIRST-TEST.md for the changes and acceptance checklist. The original player, miniplayer, navigation, adblock and click handoff remain unchanged. Manual feed confirmation is a test checkpoint, not proof of authentication. This test does not replace the default homepage. Chrome remains a separate comparison, without arbitrary click interception or session transfer. No live authenticated phone or password-manager verification is claimed. ARM64 and x86-64, Android8+. No personal account data is bundled. Stable update feed remains unchanged.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.6 — Website-Anmeldung zuerst und Autofill-Hilfe';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
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
