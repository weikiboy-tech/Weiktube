param(
    [Parameter(Mandatory=$true)][int]$VersionCode,
    [Parameter(Mandatory=$true)][string]$VersionName,
    [Parameter(Mandatory=$true)][string]$ReleaseNotes,
    [string]$Repository = 'weikiboy-tech/Weiktube',
    [switch]$Publish,
    [switch]$PublishPrepared
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $project 'Weiktube.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build and verify Weiktube.apk before publishing.' }
if ($VersionCode -lt 1 -or $VersionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?$') { throw 'Invalid release version.' }
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid repository.' }
$tag = "v$VersionName"
$stage = Join-Path (Split-Path -Parent $project) "Weiktube-release-$VersionName"
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$feedPath = Join-Path $stage 'update.json'
if ($PublishPrepared) {
    if (-not (Test-Path -LiteralPath $feedPath)) { throw 'No prepared update manifest exists.' }
    $prepared = Get-Content -LiteralPath $feedPath -Raw | ConvertFrom-Json
    if ($prepared.versionCode -ne $VersionCode -or $prepared.versionName -ne $VersionName -or
        $prepared.sha256 -ne $hash -or $prepared.apkUrl -ne "https://github.com/$Repository/releases/download/$tag/Weiktube.apk") {
        throw 'Prepared manifest does not match the selected APK, version or repository.'
    }
    if (Test-Path -LiteralPath (Join-Path $stage '.git')) { throw 'Staging already has Git state; review the prior attempt before retrying.' }
    $ReleaseNotes = $prepared.notes
    $Publish = $true
} else {
    if (Test-Path -LiteralPath $stage) { throw "Staging folder already exists: $stage. Review it and use -PublishPrepared, or prepare a new version." }
    New-Item -ItemType Directory -Path $stage | Out-Null

# Explicit allowlist: no SDK paths, build logs, caches, device captures or signing keys.
$files = @('settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','.gitignore','README.md','BUILD.md','TESTING.md')
foreach ($file in $files) { Copy-Item -LiteralPath (Join-Path $project $file) -Destination $stage }
foreach ($directory in @('gradle','scripts','.github')) {
    $path = Join-Path $project $directory
    if (Test-Path -LiteralPath $path) { Copy-Item -LiteralPath $path -Destination $stage -Recurse }
}
New-Item -ItemType Directory -Path (Join-Path $stage 'app') | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'app/build.gradle.kts') -Destination (Join-Path $stage 'app')
Copy-Item -LiteralPath (Join-Path $project 'app/src') -Destination (Join-Path $stage 'app') -Recurse
foreach ($extra in @('UPDATES.md','CHANGELOG.md','PRIVACY.md')) {
    if (Test-Path -LiteralPath (Join-Path $project $extra)) { Copy-Item -LiteralPath (Join-Path $project $extra) -Destination $stage }
}
$feed = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = "https://github.com/$Repository/releases/download/$tag/Weiktube.apk"
    sha256 = $hash
    notes = $ReleaseNotes
}
$feed | ConvertTo-Json | Set-Content -LiteralPath $feedPath -Encoding utf8NoBOM
}
if (-not $Publish) {
    Write-Output "Prepared sanitized source and update manifest: $stage"
    Write-Output 'No remote changes made. Review files, then publish this exact source and manifest using -PublishPrepared.'
    return
}

# Credentials remain in memory and are sent only to official GitHub API/upload endpoints.
$env:GCM_INTERACTIVE = 'never'
$credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$credential = @{}
foreach ($line in $credentialLines) { if ($line -match '^([^=]+)=(.*)$') { $credential[$matches[1]] = $matches[2] } }
if (-not $credential['password']) { throw 'GitHub authentication is unavailable. Sign in using Git Credential Manager first.' }
$headers = @{ Authorization="Bearer $($credential['password'])"; Accept='application/vnd.github+json'; 'X-GitHub-Api-Version'='2022-11-28' }
$profile = Invoke-RestMethod 'https://api.github.com/user' -Headers $headers
if ($profile.login -ne $Repository.Split('/')[0]) { throw 'Authenticated account differs from repository owner.' }

Push-Location $stage
try {
    git init -b main | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'git init failed' }
    git config user.name $profile.login
    git config user.email "$($profile.id)+$($profile.login)@users.noreply.github.com"
    git remote add origin "https://github.com/$Repository.git"
    # Start from the existing main history when present; never rewrite remote commits.
    $remoteRefs = git ls-remote --heads origin
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect repository refs' }
    if ($remoteRefs) {
        if (-not ($remoteRefs | Select-String 'refs/heads/main$')) { throw 'Remote repository has no main branch; review its existing branch structure.' }
        git fetch origin main
        if ($LASTEXITCODE -ne 0) { throw 'git fetch failed' }
        git reset --mixed origin/main | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot base release on existing history' }
    }
    git add .
    git commit -m "Weiktube $VersionName"
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
    git push -u origin main
    if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
    $sourceZip = Join-Path $stage 'Weiktube-Quellcode.zip'
    git archive --format=zip --prefix=Weiktube/ -o $sourceZip HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Source archive failed' }
} finally { Pop-Location }

$body = @{ tag_name=$tag; target_commitish='main'; name="Weiktube $VersionName"; body=$ReleaseNotes; draft=$true; prerelease=$false } | ConvertTo-Json
$release = Invoke-RestMethod "https://api.github.com/repos/$Repository/releases" -Headers $headers -Method Post -ContentType 'application/json' -Body $body
$uploadBase = $release.upload_url -replace '\{.*$', ''
foreach ($asset in @(@{path=$apk;name='Weiktube.apk';type='application/vnd.android.package-archive'}, @{path=$feedPath;name='update.json';type='application/json'}, @{path=$sourceZip;name='Weiktube-Quellcode.zip';type='application/zip'})) {
    $uploaded = Invoke-RestMethod "$($uploadBase)?name=$($asset.name)" -Headers $headers -Method Post -ContentType $asset.type -InFile $asset.path
    if ($uploaded.size -ne (Get-Item -LiteralPath $asset.path).Length) { throw "Upload size mismatch: $($asset.name)" }
}
$published = Invoke-RestMethod "https://api.github.com/repos/$Repository/releases/$($release.id)" -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false,"make_latest":"true"}'
Write-Output "Published: $($published.html_url)"
Write-Output "Update feed: https://github.com/$Repository/releases/latest/download/update.json"
