# Trail Tracker

Android trail hiking tracker. Save a "set point" (trailhead, camp, parking) and get live distance and bearing back to it. Switch between street and topographic map layers. Built for low power draw — GPS only, no cell/internet required for positioning.

## Features

- GNSS location tracking via `FusedLocationProviderClient`, balanced-power priority
- Street map and topographic map layer toggle (OpenStreetMap / OpenTopoMap tiles — free, no API key)
- Set Point / Return with live distance and bearing
- Trail logged to disk and redrawn on relaunch
- Foreground service keeps tracking while the screen is off

## Build

GitHub Actions builds a debug APK on every push to `main`. Download it from the **Actions** tab → latest run → **Artifacts**.

To build locally, you'll need the Android SDK and JDK 17, then run:

```
./gradlew assembleDebug
```

## License

Proprietary — All rights reserved. See [LICENSE](LICENSE).

## Disclaimer

This app is provided for personal, recreational use. GPS accuracy depends on device hardware, sky visibility, and terrain, and can be degraded or unavailable in canyons, dense forest, or bad weather. It is not a substitute for a map, compass, and basic navigation skills, and should not be relied on as your only means of route-finding in the backcountry. The developer is not responsible for injury, loss, or getting lost. Use at your own risk.
