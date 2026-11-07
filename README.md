# TowerRadar

An independent Android application for showing nearby telecommunications masts and potentially mobile-network installations on a live map.

**Version:** 0.1.0 (prototype). **Language:** Russian. **Platform:** Android 8.0+.

## Features

- GPS-based search with user location marker and updates while the app is open.
- MapLibre / OpenFreeMap map, with OpenStreetMap attribution.
- OpenStreetMap Overpass API search for publicly mapped telecom sites.
- Radius selector: 1, 3, 5, 10, 20, 50 km.
- Filtering by reported operator, map pin detail panel, OSM source link.
- Blue markers indicate OSM tags mentioning mobile communication; orange markers indicate unconfirmed telecom structures.
- Cached result for recent searches, manual refresh, network error messages.
- No location history collection and no background location tracking.

**Data limitations:** OpenStreetMap is crowd-sourced and incomplete. A mapped mast is not necessarily a mobile cellular base station. No frequency, live serving-cell identity, precise radio cell estimate, or operator ownership is inferred when the tags do not specify it. An empty region does not prove there are no towers. On manual refresh, approximate search coordinates are sent to the public Overpass endpoint.

## Development

Open the repository in Android Studio with JDK 17. Android Gradle Plugin 8.11.1, Gradle 8.13, Kotlin 2.2.21, compileSdk 35, MapLibre Android 13.7.1.

The GitHub Actions workflow on push to main runs JVM unit tests, Android lint, debug APK assembly, signature verification and checksum generation. The resulting **TowerRadar-debug-APK** is available under the successful run's **Artifacts** section.

Debug APKs are signed with GitHub Actions' temporary debug signing key. Do **not** rely on the signature staying identical across builds or devices; Android app upgrades may require uninstalling the previous debug build. A stable, user-owned release keystore is a later milestone and must never be checked into the repository.

## Sources and licenses

Map tiles: https://openfreemap.org/
Map data and telecom object metadata: https://www.openstreetmap.org/copyright
Query service: https://wiki.openstreetmap.org/wiki/Overpass_API
Map renderer: https://maplibre.org/

OpenStreetMap data © OpenStreetMap contributors, licensed under ODbL. Provider terms and rate limits apply.
