# YouTube filtering source and scope

AdGuard Scriptlets **2.5.1**, GPL-3.0, copyright Adguard Software Ltd. and contributors:

- https://github.com/AdguardTeam/Scriptlets/tree/v2.5.1
- https://registry.npmjs.org/@adguard/scriptlets/-/scriptlets-2.5.1.tgz
- npm integrity: `sha512-008B4qm6mXU4mqHOl1HE/jfRa0D/LUX2Lx4k00G45csiBF/fnIGzVivPsT1Q+Uhp9C+zj5tKoekkL39W8HqjzA==`
- Exact published bundle, package, tarball and corresponding tagged source archive are in `content-filter/vendor/`. The bundled license is `ADGUARD-LICENSE.txt`.
- Run `node scripts/vendor-adblock.mjs --check` to verify all pinned SHA-256 inputs and deterministic output. Run without `--check` to regenerate. No dependency installation or network access is needed.

Only `set-constant`, `json-prune`, `json-prune-fetch-response`, and `json-prune-xhr-response` are included. Their helpers remain upstream implementations. Local generator adaptations silence all upstream diagnostic/body logging, propagate outer installation exceptions, and count only successful pruning mutations. Hook installations and unchanged responses are not counted as removed ads. Constant traps are not counted.

Bundled rule paths were reviewed against AdGuard BaseFilter's YouTube section on 2026-11-04: https://github.com/AdguardTeam/AdguardFilters/blob/master/BaseFilter/sections/specific.txt (GPL-3.0). This is a deliberately limited port of initial player ad-field suppression and JSON/fetch/XHR ad-field removal. Response URL matching is narrowed to YouTube player/home/watch endpoints; DRM endpoints, ordinary media segments and creator sponsorships are excluded. Explicit sponsored-renderer cosmetic selectors supplement data filtering. This is not a claim that all upstream YouTube workarounds or server-inserted advertisements are covered.

The handwritten runtime and generator are GPL-3.0-only. Existing navigation, media guards, account session and native player are independent. A recognized advertisement is paused, muted and covered; no content seeking, playback forcing, credential handling or ad-triggered account/session changes occur. Page-reported counters are diagnostics, never an authorization signal.
