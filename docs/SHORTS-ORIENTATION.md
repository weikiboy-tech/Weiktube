> Release 1.3.15: Dieser Entwicklungsbericht beschreibt den Stand vor Veröffentlichung. Aktuelle Release-Hinweise: [1.3.15](../RELEASE-1.3.15.md).

# Shorts routing and upright rotation — local 1.3.15 build

## Targeted corrections

- Preserve `/shorts/VIDEO_ID` through Gecko's URL policy and the watch-page navigation bridge. Route the Shorts feed entry (`/shorts`) to the existing Shorts tab as well.
- Retain validated public IDs and the existing one-shot handoff/return ticket. Normal videos still use the existing watch player; existing Shorts feed links remain in that feed.
- MainActivity exits a custom fullscreen surface before showing Shorts. Back from a Home-launched Short restores the retained Home session.
- Replace FULL_SENSOR with a three-way orientation policy: portrait, landscape, reverse-landscape. At 180 degrees the previous orientation is retained; neither automatic nor custom fullscreen exits. Only upright holding (0 degrees) returns to portrait. Diagonal dead bands avoid rotation chatter. Restored fullscreen/Activity orientation cannot re-enable reverse portrait.
- No changes to PiP controller, video sizing/transforms, playback or ad-filter algorithms in this change set. PlayerController changes are limited to routing Shorts at its entry points.

## Tests and limitations

Bridge fixtures cover direct Shorts links, Shorts feed entry, renderer reel endpoints, same-document return, duplicate input and privacy validation. Browser/watch fixtures verify Shorts routing and preservation of feed navigation. JVM tests exhaustively check sensor angles, old restored orientation modes and ten rotation sequences.

No Android device is currently attached through ADB. Actual playback, swiping, back/scroll restoration and physical rotation are NOT device-tested for this build. Android can ignore orientation requests on certain large screens/managed devices; this policy targets the phone layout.

The local build also contains the previously requested, unpublished progress/history work documented in SHORTS-RESUME.md. No GitHub publication or update-channel change was performed for this task.

Final validation: 347 JVM tests (249 app / 78 home / 20 filter), zero failures/errors. Release build successful. Lint: {'Error': 0, 'Warning': 43}. Home/browser/watch bridge fixtures, 19 player script fixtures, native PiP contracts, adblock script tests and the original-source scope check passed. Both APKs verified with the existing signing certificate, package com.weiktube.app, version 1.3.15/code 29 and debuggable=false.
