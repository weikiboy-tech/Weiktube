"""Package only explicitly selected portable project sources and verify the archive inventory."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

# Same source roots as publish-release.ps1. Never walk the whole project root:
# it also contains account screenshots, device captures and local build helpers.
REQUIRED_FILES = {
    "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    ".gitignore", "README.md", "BUILD.md", "TESTING.md", "app/build.gradle.kts",
}
OPTIONAL_FILES = {"UPDATES.md", "AUDIT.md", "CHANGELOG.md", "PRIVACY.md", "RESTORATION.md"}
SOURCE_DIRECTORIES = {"gradle", "scripts", ".github", "app/src"}
PRIVATE_PARTS = {
    "build", ".gradle", ".kotlin", ".git", "__pycache__", "validation", "screenshots",
    "signing", "keys", "private", "credentials", ".aws", ".codex", ".agents",
}
PRIVATE_SUFFIXES = {".apk", ".zip", ".log", ".keystore", ".jks", ".p12", ".pfx", ".pem", ".key"}
PRIVATE_FILES = {"local.properties", "key.properties", "keystore.properties", "signing.properties", ".env"}


def permitted(relative: Path) -> bool:
    parts = tuple(part.lower() for part in relative.parts)
    if any(part in PRIVATE_PARTS for part in parts):
        return False
    if relative.name.lower() in PRIVATE_FILES or relative.name.lower().startswith(".env."):
        return False
    if relative.suffix.lower() in PRIVATE_SUFFIXES:
        return False
    name = relative.as_posix()
    return name in REQUIRED_FILES | OPTIONAL_FILES or any(name.startswith(directory + "/") for directory in SOURCE_DIRECTORIES)


def sources(root: Path) -> list[Path]:
    root = root.resolve()
    missing = sorted(name for name in REQUIRED_FILES if not (root / name).is_file())
    if missing:
        raise ValueError(f"Missing required source files: {missing}")
    candidates = [root / name for name in REQUIRED_FILES | OPTIONAL_FILES if (root / name).is_file()]
    for directory in SOURCE_DIRECTORIES:
        candidates.extend(path for path in (root / directory).rglob("*") if path.is_file())
    selected = []
    for path in candidates:
        if not permitted(path.relative_to(root)):
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError(f"Source points outside the portable project: {path.relative_to(root)}")
        selected.append(path)
    return sorted(set(selected))


def package(root: Path) -> Path:
    root = root.resolve()
    destination = root / "Weiktube-Quellcode.zip"
    selected = sources(root)
    expected = {"Weiktube/" + path.relative_to(root).as_posix() for path in selected}
    with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
        for path in selected:
            archive.write(path, "Weiktube/" + path.relative_to(root).as_posix())
    with ZipFile(destination) as archive:
        names = archive.namelist()
        if archive.testzip() is not None or len(names) != len(set(names)) or set(names) != expected:
            raise ValueError("Archive inventory or integrity does not match the allowed sources")
        if not all(name.startswith("Weiktube/") and permitted(Path(name.removeprefix("Weiktube/"))) for name in names):
            raise ValueError("Archive contains a private or non-allowlisted path")
        for required in ("gradle/wrapper/gradle-wrapper.jar", "app/src/main/AndroidManifest.xml"):
            if "Weiktube/" + required not in names:
                raise ValueError(f"Archive lacks {required}")
    print(f"Created and verified {destination.name}: {len(names)} allowed files, {destination.stat().st_size} bytes")
    return destination


if __name__ == "__main__":
    package(Path(__file__).resolve().parents[1])
