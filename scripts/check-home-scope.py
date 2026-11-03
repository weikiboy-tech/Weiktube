"""Verify original app source, allowing only reviewed connection entry/clear hooks."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
baseline = json.loads((root / "scripts/home-original-source-hashes.json").read_text(encoding="utf-8"))
allowed = {
    "app/src/main/java/com/weiktube/app/MainActivity.kt": [
        ("        com.weiktube.home.HomeEngine.clearIfInitialized(this)\n", "")],
    "app/src/main/java/com/weiktube/app/WeiktubeUi.kt": [
        ("AccountScreen(a.youtubeAccount, a::openAccountUrl, homeConnection = { HomeConnectionEntry(a, a.youtubeAccount) })",
         "AccountScreen(a.youtubeAccount, a::openAccountUrl)")],
    "app/src/main/java/com/weiktube/app/AccountUi.kt": [
        ("subscriptionsOnly: Boolean = false, homeConnection: @Composable () -> Unit = {}", "subscriptionsOnly: Boolean = false"),
        ('            if (!subscriptionsOnly) item(key = "home_connection") { homeConnection() }\n', "")],
}
failures = []
for name, expected in baseline.items():
    file = root / name
    if not file.is_file():
        failures.append(name + " missing")
        continue
    content = file.read_text(encoding="utf-8").replace("\r\n", "\n")
    for added, original in allowed.get(name, []):
        if content.count(added) != 1:
            failures.append(name + " reviewed connection hook missing or duplicated")
        content = content.replace(added, original, 1)
    if hashlib.sha256(content.encode()).hexdigest() != expected:
        failures.append(name + " differs outside reviewed connection hooks")
if failures:
    raise SystemExit("FAIL original app scope:\n" + "\n".join(failures))
print(f"PASS {len(baseline)} original source files preserved; only account entry and web-data clear hooks allowed.")
