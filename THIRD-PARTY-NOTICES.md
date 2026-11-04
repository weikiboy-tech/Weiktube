# Third-party notices

Weiktube's source for version 1.3.5 is distributed under GPL-3.0-only; see
[LICENSE](LICENSE). Third-party components retain their own licenses and notices.

## AdGuard Scriptlets 2.5.1

Copyright Adguard Software Ltd. and contributors. GPL-3.0.
Upstream: https://github.com/AdguardTeam/Scriptlets/tree/v2.5.1

The APK contains four selected scriptlets, generated from the pinned upstream
bundle. Weiktube's generator disables response/body logging, propagates install
errors, and reports counts for actual pruning mutations. The runtime and generator
are GPL-3.0-only. See
[provenance and exact inputs](content-filter/src/main/assets/weiktube-adblock/PROVENANCE.md).

The source distribution includes the exact published package, readable bundle,
corresponding upstream source archive and deterministic generator in
`content-filter/vendor/` and `scripts/vendor-adblock.mjs`.
Run `node scripts/vendor-adblock.mjs --check` to verify the inputs and generated
output without downloading dependencies.

YouTube filtering rules are based on the GPL-3.0 AdGuard BaseFilter YouTube section:
https://github.com/AdguardTeam/AdguardFilters/blob/master/BaseFilter/sections/specific.txt
The included rule subset, local adaptations and limitations are documented in
the provenance file. This is not the full AdGuard application or filtering engine.

## Other dependencies

GeckoView is a Mozilla component, with its own licensing and bundled notices:
https://www.mozilla.org/MPL/
AndroidX, Kotlin, kotlinx.coroutines and other Gradle dependencies retain their
upstream licenses. Their versions are declared in the module Gradle files.
Google Play services are distributed under Google's applicable terms and are
not relicensed by this repository. No third-party trademark ownership is claimed.

Private APK/filter signing keys, cookies, account data and local device captures
are never part of the source distribution. Public verification keys are included.
