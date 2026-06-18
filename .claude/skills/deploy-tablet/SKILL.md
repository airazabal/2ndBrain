---
name: deploy-tablet
description: >
  Deploy a debug build to the Samsung Galaxy Tab S8 Ultra (SM_X930) tablet via ADB.
  Triggers on: "deploy to tablet", "install on tablet", "push to tablet", "deploy tablet",
  or any request to sideload the APK onto the physical tablet device.
---

# Deploy to Tablet

Installs the debug APK onto the connected Samsung Galaxy Tab S8 Ultra (SM_X930).

## Steps

1. Ensure a debug APK exists (`./gradlew assembleDebug` if needed).
2. Run the deploy script:
```bash
TABLET_TRANSPORT=$(adb devices -l | grep 'SM_X930' | awk '{print $NF}' | grep -o '[0-9]*' | head -1)
TABLET=$(adb devices -l | grep 'SM_X930' | awk '{print $1}')
echo "performing adb install on device: $TABLET"
adb -t "$TABLET_TRANSPORT" install -r /Users/alexirazabal/AndroidStudioProjects/2ndBrain/app/build/outputs/apk/debug/app-debug.apk
echo "Deployed Tablet"
```

## What the script does
- Detects the Tab S8 Ultra by model string `SM_X930` via `adb devices -l`
- Uses the transport ID (`adb -t`) for reliable targeting when multiple devices are connected
- Runs `adb -t <transport> install -r` to reinstall over any existing build
- Prints confirmation on success
