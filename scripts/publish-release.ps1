param(
    [int]$VersionCode = 16,
    [string]$VersionName = '1.3.2',
    [string]$ReleaseNotesFile = '',
    [string]$Repository = 'weikiboy-tech/Weiktube',
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Run this publisher in PowerShell 7 or newer.' }
$project = Split-Path -Parent $PSScriptRoot
if ($VersionCode -lt 1 -or $VersionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw 'A stable version and positive version code are required.' }
if ($Repository -ne 'weikiboy-tech/Weiktube') { throw 'This reviewed publisher is restricted to weikiboy-tech/Weiktube.' }
$tag = "v$VersionName"
$baseBranch = 'website-login-1.3.1'
$baseCommit = 'b88b863c7a0a3cc011341a10028b9008aac5dbe0'
$expectedMain = '8006faaa49463565d638dd67d82c1e0a2649c4dc'
$expectedCertificate = 'f484083f7ee443d598c9216c76c3cd028d76d59ad73d6f5abc8ad6ded84eabc3'
$output = Join-Path $project 'dist/stable'
$source = Join-Path $project 'Weiktube-Quellcode.zip'
$stage = Join-Path $project "validation/stable-$VersionName-release-source"
$verificationPath = Join-Path $output 'release-verification.json'
if (-not $ReleaseNotesFile) { $ReleaseNotesFile = Join-Path $project "RELEASE-$VersionName.md" }
foreach ($file in @($source, $verificationPath, $ReleaseNotesFile)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing prepared release file: $file" }
}
$notes = Get-Content -LiteralPath $ReleaseNotesFile -Raw -Encoding utf8
if ([string]::IsNullOrWhiteSpace($notes) -or $notes.Length -gt 10000) { throw 'Release notes must contain 1 to 10000 characters.' }
$updateNotes = 'Die originale YouTube-Startseite öffnet direkt in Weiktube. Die Anmeldung wird einmal angeboten und kann übersprungen werden; unter Du kannst du dich jederzeit anmelden oder das Konto wechseln. Kleinere APKs ermöglichen Updates. Deine gespeicherte Website-Sitzung bleibt beim Aktualisieren erhalten.'
$build = Get-Content -LiteralPath (Join-Path $project 'app/build.gradle.kts') -Raw -Encoding utf8
if ($build -notmatch "(?m)^\s*versionCode\s*=\s*$VersionCode\s*$" -or
    $build -notmatch ('(?m)^\s*versionName\s*=\s*"' + [regex]::Escape($VersionName) + '"\s*$')) {
    throw 'Source version does not match the requested stable release.'
}

# The source packager uses an explicit allowlist. Verify every ZIP entry against
# the current reviewed sources BEFORE extraction; never publish a stale archive.
$verifySource = @(
    'import pathlib, runpy, sys, zipfile',
    'root = pathlib.Path(sys.argv[1]).resolve()',
    'archive = pathlib.Path(sys.argv[2])',
    'sources = runpy.run_path(str(root / "scripts/package-source.py"))["sources"](root)',
    'expected = {"Weiktube/" + p.relative_to(root).as_posix(): p for p in sources}',
    'with zipfile.ZipFile(archive) as z:',
    '    names = z.namelist()',
    '    if len(names) != len(set(names)) or set(names) != set(expected) or z.testzip() is not None:',
    '        raise SystemExit("Source ZIP inventory/integrity mismatch")',
    '    for name, path in expected.items():',
    '        if z.read(name) != path.read_bytes():',
    '            raise SystemExit("Source ZIP is stale: " + name)',
    'print("Verified sanitized source ZIP against current portable sources")'
) -join "`n"
python -c $verifySource $project $source
if ($LASTEXITCODE -ne 0) { throw 'Sanitized source verification failed.' }

$verification = Get-Content -LiteralPath $verificationPath -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable
$apks = [ordered]@{}
$assets = @()
foreach ($variant in @(@{abi='arm64-v8a';name='Weiktube.apk'}, @{abi='x86_64';name='Weiktube-x86_64.apk'})) {
    $path = Join-Path $output $variant.name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing APK: $($variant.name)" }
    $size = (Get-Item -LiteralPath $path).Length
    if ($size -lt 1 -or $size -gt (128L * 1024 * 1024)) { throw "APK exceeds the existing updater limit: $($variant.name)" }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $verified = $verification.artifacts[$variant.name]
    if (-not $verified -or $verified.sha256 -ne $hash -or $verified.bytes -ne $size -or
        $verified.abi -ne $variant.abi -or $verified.versionCode -ne $VersionCode -or
        $verified.versionName -ne $VersionName -or $verified.packageName -ne 'com.weiktube.app' -or
        $verified.debuggable -isnot [bool] -or $verified.debuggable -ne $false -or
        $verified.certificateSha256 -ne $expectedCertificate) {
        throw "APK does not match the checked release metadata: $($variant.name)"
    }
    $apks[$variant.abi] = [ordered]@{apkUrl="https://github.com/$Repository/releases/download/$tag/$($variant.name)";sha256=$hash}
    $assets += @{path=$path;name=$variant.name;type='application/vnd.android.package-archive';hash=$hash;size=$size}
}

# Top-level fields remain compatible with existing ARM64 clients. New clients
# select a matching ABI from apks. The alternate feed supports legacy x86 users.
foreach ($feedVariant in @(@{abi='arm64-v8a';name='update.json'}, @{abi='x86_64';name='update-x86_64.json'})) {
    $selected = $apks[$feedVariant.abi]
    $feed = [ordered]@{versionCode=$VersionCode;versionName=$VersionName;apkUrl=$selected.apkUrl;sha256=$selected.sha256;notes=$updateNotes;apks=$apks}
    $json = $feed | ConvertTo-Json -Depth 6
    if ([Text.Encoding]::UTF8.GetByteCount($json) -gt 65536) { throw 'Update manifest exceeds the existing feed limit.' }
    $path = Join-Path $output $feedVariant.name
    $json | Set-Content -LiteralPath $path -Encoding utf8NoBOM
    $assets += @{path=$path;name=$feedVariant.name;type='application/json'}
}
$assets += @{path=$source;name='Weiktube-Quellcode.zip';type='application/zip'}
$checksums = Join-Path $output 'SHA256SUMS.txt'
$checksumLines = foreach ($asset in $assets) {
    $asset.hash = (Get-FileHash -LiteralPath $asset.path -Algorithm SHA256).Hash.ToLowerInvariant()
    $asset.size = (Get-Item -LiteralPath $asset.path).Length
    "$($asset.hash)  $($asset.name)"
}
$checksumLines | Set-Content -LiteralPath $checksums -Encoding ascii
$assets += @{path=$checksums;name='SHA256SUMS.txt';type='text/plain';hash=(Get-FileHash -LiteralPath $checksums -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item -LiteralPath $checksums).Length}
if (-not $Publish) {
    Write-Output "Prepared stable $($tag): both APKs fit the legacy 128 MiB limit; metadata, source and manifests verified."
    Write-Output "Prepared files: $output. No remote changes."
    return
}
if (Test-Path -LiteralPath $stage) { throw 'Staging already exists; inspect the prior attempt before retrying.' }

# Credentials remain in memory and are used only with official GitHub endpoints.
$env:GCM_INTERACTIVE = 'never'
$lines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
if ($LASTEXITCODE -ne 0) { throw 'GitHub authentication unavailable.' }
$credential = @{}
foreach ($line in $lines) { if ($line -match '^([^=]+)=(.*)$') { $credential[$matches[1]] = $matches[2] } }
if (-not $credential['password']) { throw 'GitHub authentication unavailable.' }
$headers = @{Authorization="Bearer $($credential['password'])";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28'}
$githubProfile = Invoke-RestMethod 'https://api.github.com/user' -Headers $headers
if ($githubProfile.login -ne 'weikiboy-tech') { throw 'Unexpected authenticated repository owner.' }
$page = 1
do {
    $releases = @(Invoke-RestMethod "https://api.github.com/repos/$Repository/releases?per_page=100&page=$page" -Headers $headers)
    if ($releases | Where-Object tag_name -eq $tag) { throw 'Release already exists; refusing overwrite.' }
    $page++
} while ($releases.Count -eq 100)
$remote = "https://github.com/$Repository.git"
$tagRefs = git ls-remote $remote "refs/tags/$tag"
if ($LASTEXITCODE -ne 0 -or $tagRefs) { throw 'Cannot verify unused release tag.' }
$mainRefs = @(git ls-remote $remote 'refs/heads/main')
if ($LASTEXITCODE -ne 0 -or $mainRefs.Count -ne 1 -or ($mainRefs[0] -split '\s+')[0] -ne $expectedMain) {
    throw 'Main changed since review; preserve and review its new commits before publishing.'
}

Expand-Archive -LiteralPath $source -DestinationPath $stage
Push-Location (Join-Path $stage 'Weiktube')
try {
    git init -b main | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Isolated staging initialization failed.' }
    git config core.autocrlf true
    git config user.name $githubProfile.login
    git config user.email "$($githubProfile.id)+$($githubProfile.login)@users.noreply.github.com"
    git remote add origin $remote
    git fetch origin "main:refs/remotes/origin/main" "$($baseBranch):refs/remotes/origin/$baseBranch"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fetch reviewed history.' }
    $parent = git rev-parse "origin/$baseBranch"
    if ($LASTEXITCODE -ne 0 -or $parent -ne $baseCommit) { throw 'Confirmed source branch changed; review before publishing.' }
    $main = git rev-parse origin/main
    if ($LASTEXITCODE -ne 0 -or $main -ne $expectedMain) { throw 'Main changed during preparation.' }
    git merge-base --is-ancestor $main $parent
    if ($LASTEXITCODE -ne 0) { throw 'Main is not an ancestor of the reviewed source; do not discard unrelated history.' }
    python scripts/check-home-scope.py
    if ($LASTEXITCODE -ne 0) { throw 'Original-source scope check failed.' }
    git add .
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stage sanitized source.' }
    git add --renormalize .
    if ($LASTEXITCODE -ne 0) { throw 'Cannot normalize sanitized source.' }
    $tree = git write-tree
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create source tree.' }
    # A new isolated repository: preserve the reviewed history without replacing
    # any existing working tree or rewriting any local/remote branch.
    $commit = git commit-tree $tree -p $baseCommit -m "Weiktube $VersionName stable release"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create release commit.' }
    git update-ref refs/heads/main $commit ('0' * 40)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated main ref.' }
    $status = git status --porcelain
    if ($LASTEXITCODE -ne 0 -or $status) { throw 'Prepared source differs from the release commit.' }
    git push origin HEAD:refs/heads/main
    if ($LASTEXITCODE -ne 0) { throw 'Normal fast-forward push failed; review remote state before retrying.' }
} finally { Pop-Location }

$body = @{tag_name=$tag;target_commitish=$commit;name="Weiktube $VersionName";body=$notes;draft=$true;prerelease=$false;make_latest='false'} | ConvertTo-Json
$release = Invoke-RestMethod "https://api.github.com/repos/$Repository/releases" -Headers $headers -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$upload = $release.upload_url -replace '\{.*$', ''
if (-not $upload.StartsWith('https://uploads.github.com/')) { throw 'Unexpected upload origin.' }
foreach ($asset in $assets) {
    # Detect a local replacement while staging, before using the checked hash.
    if ((Get-Item -LiteralPath $asset.path).Length -ne $asset.size -or
        (Get-FileHash -LiteralPath $asset.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $asset.hash) {
        throw "Prepared asset changed: $($asset.name)"
    }
    $uploaded = Invoke-RestMethod "$($upload)?name=$($asset.name)" -Headers $headers -Method Post -ContentType $asset.type -InFile $asset.path
    if ($uploaded.size -ne $asset.size -or $uploaded.digest -ne "sha256:$($asset.hash)" -or $uploaded.state -ne 'uploaded') {
        throw "Uploaded asset did not pass size/hash verification: $($asset.name)"
    }
}
$published = Invoke-RestMethod "https://api.github.com/repos/$Repository/releases/$($release.id)" -Headers $headers -Method Patch -ContentType 'application/json' -Body '{"draft":false,"prerelease":false,"make_latest":"true"}'
Write-Output "Published: $($published.html_url)"
Write-Output "Source commit: $commit"
Write-Output "APK: https://github.com/$Repository/releases/latest/download/Weiktube.apk"
Write-Output "Update feed: https://github.com/$Repository/releases/latest/download/update.json"
