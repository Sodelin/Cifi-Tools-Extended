# CIFI Tools & CIFI Tools Extended

Android experiments that put the community CIFI Tools website within reach on a phone. This repository is independent of CIFI, Octocube Games, and the CIFI Tools website maintainers.

- **CIFI Tools** (`apps/base`) opens the existing website using a browser-owned session. The current prototype can offer a browser chooser and compare two selected files. Its phone behavior and sign-in have not been verified for a general release.
- **CIFI Tools Extended** (`apps/extended`) displays the existing website inside an app window. A previous private-device test ran a Borge simulation using partially imported game data. This public source does not include anyone's save, profile, or account. The website's simulator is used; the experimental Rust code does not replace it.
- **Rust experiments** (`engine`) contain independent numerical primitives. They are not yet packaged into this Android build or connected to the site's simulations.

## Current limits

This is development source, not a finished release. Direct or automatic CIFI save import, account synchronization inside Extended, independent verification of simulator accuracy, offline use, and parity between apps are not complete. The Extended refresh and website-sync actions are disabled. The phone-specific Termux signer and runner have been removed from this public snapshot. Do not use it to replace an existing app expecting the same signing key or local data.

Both apps use the existing remotely hosted website; this repository does not contain or redistribute the website's simulator, game binaries, game assets, saved data, credentials, signing keys, or private recovery records. Availability and behavior of that website are outside this repository's control.

## Build

Open the project in Android Studio with Android SDK 35 and JDK 17. The Gradle files describe two application modules. No Gradle wrapper or signed APK is included yet. The base module also has a host-tested unsigned build script; Gradle builds and installation from this public snapshot have not been verified on Android. Android will not update an existing install signed with a different key, and uninstalling an existing app may remove its local data.

## Source and attribution

The Extended Android prototype and selected Rust source are a cleaned snapshot of local commit `44eed32`; the base launcher was prepared from base commit `031b879`. Phone-specific paths, keys and state are excluded; the Extended Termux launcher is replaced by an explicit unavailable stub. The original CIFI Tools website is a separate community project. The HunterSim / CIFI Tools Companion is another separate project: https://github.com/TmRxJD/cifi-tools. Neither its bundled website engine nor the game's binaries are included here. Inclusion of links or compatibility code does not imply endorsement.

No open-source license has yet been selected for this repository's authored code. All rights are reserved pending a license choice; third-party projects and trademarks have their own terms.
