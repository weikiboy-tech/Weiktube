param([switch]$Publish)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$repo='weikiboy-tech/Weiktube'
$tag='gecko-return-1.2.9-test.1'
$branch='gecko-return-1.2.9'
$baseCommit='a2997cf926caaf8531ce02e99e2c8313534b4d30'
$apk=Join-Path $project 'Weiktube-Home-Test.apk'
$source=Join-Path $project 'Weiktube-Quellcode.zip'
foreach($file in @($apk,$source)) { if(-not (Test-Path -LiteralPath $file)) { throw 'Build, validate and package source before publishing' } }
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksums=Join-Path $project 'validation/gecko-return-SHA256SUMS.txt'
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
$stage=Join-Path $project 'validation/gecko-return-release-source'
if(Test-Path -LiteralPath $stage) { throw 'Staging exists; review before retry' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
 git init -b $branch | Out-Null
 git config core.autocrlf true
 git config user.name $profile.login
 git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
 git remote add origin $remote
 git fetch origin player-return-1.2.8
 if($LASTEXITCODE -ne 0) { throw 'Cannot fetch original history' }
 $parent=git rev-parse origin/player-return-1.2.8
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
 $commit=git commit-tree $tree -p $baseCommit -m 'Keep Gecko progress module enabled while retaining the original home document'
 if($LASTEXITCODE -ne 0) { throw 'Source commit failed' }
 git update-ref "refs/heads/$branch" $commit ('0' * 40)
 if($LASTEXITCODE -ne 0) { throw 'New staging branch creation failed' }
 $sourceStatus=git status --porcelain
 if($LASTEXITCODE -ne 0 -or $sourceStatus) { throw 'Staged source does not match commit' }
 git push origin "HEAD:refs/heads/$branch"
 if($LASTEXITCODE -ne 0) { throw 'Test branch push failed' }
} finally { Pop-Location }
$notes=@'
Weiktube 1.2.9-gecko-return-test, code13, com.weiktube.app. Same package and signing certificate. Install as an update to retain the website profile.

Version 1.2.8 did not resolve the reported device failure. The concrete root cause is now reproduced using the actual Gecko module methods bundled in the APK: setting progressDelegate to null while parked and restoring it re-enables GeckoViewProgress, which emits synthetic about:blank PageStart/LocationChange events. The existing app then invalidates its live bridge and replaces the known YouTube URL.

The runtime correction is one assignment in OriginalHomeReturn: install an activity-free non-null ProgressDelegate while parked. Progress stays enabled and does not replay that synthetic navigation on return. The existing session, page URL, bridge and scroll state remain owned by the retained page. No blanket about:blank exception was added; genuine document navigation still invalidates the old bridge.

Original player, MainActivity, OriginalHomeActivity, click bridge, adblock engine and Google API account logic are unchanged. See GECKO-RETURN-TEST.md for exact changes, regression evidence from the bundled Gecko module, automated tests and limitations. No Android device was reachable; ten real video/back cycles and actual scroll return remain untested. This is a test prerelease, not completed device acceptance.

The default homepage and stable update feed remain unchanged. ARM64 and x86-64, Android8+. No account sessions, credentials or device screenshots are bundled. Diagnostics use fixed reason codes, not sensitive website responses or URLs.
'@
$body=@{tag_name=$tag;target_commitish=$commit;name='Weiktube 1.2.9 - Gecko-Rueckkehr ohne verlorene Klickverbindung';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
$release=Invoke-RestMethod "https://api.github.com/repos/$repo/releases" -Headers $headers -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
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
