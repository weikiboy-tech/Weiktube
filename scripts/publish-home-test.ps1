param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='player-return-1.2.8-test.1'
$branch='player-return-1.2.8'
$baseCommit='f0669ef9aa6fd180a46f657cac9f9719d82b2f9f'
$apk=Join-Path $project 'Weiktube-Home-Test.apk'
$source=Join-Path $project 'Weiktube-Quellcode.zip'
foreach($file in @($apk,$source)) { if(-not (Test-Path -LiteralPath $file)) { throw 'Build, validate and package source before publishing' } }
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksums=Join-Path $project 'validation/player-return-SHA256SUMS.txt'
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
$stage=Join-Path $project 'validation/player-return-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin player-handoff-1.2.7
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 $parent=git rev-parse origin/player-handoff-1.2.7
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
 $commit=git commit-tree $tree -p $baseCommit -m 'Rearm the retained Gecko click bridge after native player return'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 git update-ref "refs/heads/$branch" $commit ('0' * 40)
 if($LASTEXITCODE -ne 0) { throw 'New staging branch creation failed' }
 $sourceStatus=git status --porcelain
 if($LASTEXITCODE -ne 0 -or $sourceStatus) { throw 'Staged source does not match commit' }
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.8-player-return-test, code12, com.weiktube.app. Same package and signing certificate. Install as an update to retain the website profile.

Targeted fix for video clicks remaining blocked after returning from the existing player. The retained document's bridge now has an explicit resume/reconnection handshake, generation-scoped suspension and bounded acknowledgement recovery. Event listeners are installed once; repeated return does not reload the feed or replace the player.

The working video detection and ActivityResult handoff remain in place. Original player, MainActivity navigation, adblock engine, Google API account logic and original personalized website are unchanged. No account sessions, credentials or device screenshots are bundled.

This is a test prerelease, not a claim of completed Android/OnePlus acceptance. See PLAYER-RETURN-TEST.md for reproduced protocol failures, automated repeated-return tests and the eight required device checks. No Android device was reachable for this change; fixtures do not prove playback, actual touch delivery or scroll preservation on a device. No new Google login fix is claimed.

The default homepage and stable update feed remain unchanged. ARM64 and x86-64, Android8+. No account sessions, credentials or device screenshots are bundled. Diagnostics use fixed reason codes, not sensitive website responses or URLs.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.8 — Videoklicks nach Player-Rückkehr wieder aktivieren';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
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
