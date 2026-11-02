"""Package portable Weiktube sources without SDK paths, caches or binaries."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
destination = root / "Weiktube-Quellcode.zip"
excluded_dirs = {"build", ".gradle", ".kotlin", ".git", "__pycache__"}
with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
    for source in sorted(root.rglob("*")):
        relative = source.relative_to(root)
        if not source.is_file() or any(part in excluded_dirs for part in relative.parts):
            continue
        if source.name == "local.properties" or source.suffix in {".apk", ".zip"}:
            continue
        archive.write(source, Path("Weiktube") / relative)
with ZipFile(destination) as archive:
    assert archive.testzip() is None
    names = archive.namelist()
    assert "Weiktube/gradle/wrapper/gradle-wrapper.jar" in names
    assert "Weiktube/app/src/main/AndroidManifest.xml" in names
    assert not any("local.properties" in name for name in names)
print(f"Created and verified {destination.name}: {len(names)} files, {destination.stat().st_size} bytes")
