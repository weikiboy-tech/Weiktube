param([switch]$Publish)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$repository = 'weikiboy-tech/Weiktube'
$tag = 'probe-0.1.0'
$branch = 'engine-probe-0.1.0'
$apk = Join-Path $project 'Weiktube-Test.apk'
$source = Join-Path $project 'Weiktube-Quellcode.zip'
$notes = @'
Separate experimental application: Weiktube Test 0.1.0 (com.weiktube.probe).

Original mobile YouTube pages in an internal GeckoView engine, shared private website session, direct video-link routing into a dedicated in-app watch view, mini/full player and background pause. No Chrome, Brave or Custom Tab.

Google sign-in and personalized recommendations are NOT yet verified on a real account. This is the authentication feasibility test, not a finished YouTube replacement. No YouTube adblock or auto-updater in this test. Android 8+, ARM64 and x86-64. No account data is bundled. Existing Weiktube installation and stable updates remain unchanged.

Install Weiktube-Test.apk, open Konto, sign in through the regular website, return to Startseite and tap a recommendation. Report any visible login error. Full limitations and acceptance steps: ENGINE-TEST.md in the matching source asset and test branch.
'@
foreach ($file in @($apk,$source)) { if (-not (Test-Path -LiteralPath $file)) { throw "Missing prepared artifact: $file" } }
$sha = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksums = Join-Path $project 'validation/probe-SHA256SUMS.txt'
@("$sha  Weiktube-Test.apk", "$((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant())  Weiktube-Quellcode.zip") | Set-Content -LiteralPath $checksums -Encoding ascii
if (-not $Publish) { Write-Output "Prepared $tag; APK SHA256 $sha. No remote changes."; return }

# Credential is held in memory and sent only to official GitHub API/upload endpoints.
$env:GCM_INTERACTIVE = 'never'
$credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$credential = @{}
foreach ($line in $credentialLines) { if ($line -match '^([^=]+)=(.*)$') { $credential[$matches[1]] = $matches[2] } }
if (-not $credential['password']) { throw 'GitHub authentication unavailable.' }
$headers = @{ Authorization="Bearer $($credential['password'])"; Accept='application/vnd.github+json'; 'X-GitHub-Api-Version'='2022-11-28' }
$profile = Invoke-RestMethod 'https://api.github.com/user' -Headers $headers
if ($profile.login -ne 'weikiboy-tech') { throw 'Authenticated account differs from authorized repository owner.' }
$releases = Invoke-RestMethod "https://api.github.com/repos/$repository/releases?per_page=100" -Headers $headers
if ($releases | Where-Object tag_name -eq $tag) { throw 'Test release already exists; refusing to replace it.' }
$remote = "https://github.com/$repository.git"
$refs = git ls-remote $remote "refs/heads/$branch" "refs/tags/$tag"
if ($LASTEXITCODE -ne 0 -or $refs) { throw 'Cannot verify unused test branch/tag.' }
$stage = Join-Path $project 'validation/probe-release-source'
if (Test-Path -LiteralPath $stage) { throw 'Source stage already exists; review before retrying.' }
Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
    git init -b $branch | Out-Null
    git config user.name $profile.login
    git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
    git remote add origin $remote
    git fetch origin main
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fetch existing history.' }
    git reset --mixed origin/main | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot preserve existing history.' }
    git add .
    git commit -m 'Add isolated GeckoView authentication and internal player test'
    if ($LASTEXITCODE -ne 0) { throw 'Source commit failed.' }
    $commit = git rev-parse HEAD
    git push origin "HEAD:refs/heads/$branch"
    if ($LASTEXITCODE -ne 0) { throw 'Test branch push failed.' }
} finally { Pop-Location }
$body = @{tag_name=$tag;target_commitish=$commit;name='Weiktube Test 0.1.0';body=$notes;draft=$true;prerelease=$true;make_latest='false'} | ConvertTo-Json
$release = Invoke-RestMethod "https://api.github.com/repos/$repository/releases" -Headers $headers -Method Post -ContentType 'application/json' -Body $body
$upload = $release.upload_url -replace '\{.*$', ''
if (-not $upload.StartsWith('https://uploads.github.com/')) { throw 'Unexpected asset upload origin.' }
foreach ($asset in @(@{path=$apk;name='Weiktube-Test.apk';type='application/vnd.android.package-archive'},@{path=$source;name='Weiktube-Quellcode.zip';type='application/zip'},@{path=$checksums;name='SHA256SUMS.txt';type='text/plain'})) {
    $result = Invoke-RestMethod "$($upload)?name=$($asset.name)" -Headers $headers -Method Post -ContentType $asset.type -InFile $asset.path
    if ($result.size -ne (Get-Item -LiteralPath $asset.path).Length) { throw 'Uploaded asset size differs.' }
    if ($result.digest -and $result.digest -ne "sha256:$((Get-FileHash -LiteralPath $asset.path -Algorithm SHA256).Hash.ToLowerInvariant())") { throw 'Uploaded asset hash differs.' }
}
$published = Invoke-RestMethod "https://api.github.com/repos/$repository/releases/$($release.id)" -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false,"prerelease":true,"make_latest":"false"}'
Write-Output "Published prerelease: $($published.html_url)"
Write-Output "APK: https://github.com/$repository/releases/download/$tag/Weiktube-Test.apk"
