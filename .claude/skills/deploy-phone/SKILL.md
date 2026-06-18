---
name: deploy-phone
description: >
  Deploy a debug build to the Samsung Galaxy S24 Ultra (SM_S948U1) phone via ADB.
  Triggers on: "deploy to phone", "install on phone", "push to phone", "deploy phone",
  or any request to sideload the APK onto the physical phone device.
---

# Deploy to Phone

Installs the debug APK onto the connected Samsung Galaxy S24 Ultra (SM_S948U1).

## Steps

1. Ensure a debug APK exists (`./gradlew assembleDebug` if needed).
2. Run the deploy script:
```bash
PHONE=$(adb devices -l | grep 'SM_S948U1' | grep -v '(2)' | awk '{print $1}')
echo "performing adb install on device: $PHONE"
adb -s "$PHONE" install -r /Users/alexirazabal/AndroidStudioProjects/2ndBrain/app/build/outputs/apk/debug/app-debug.apk
echo "Phone Deployed"
```

## What the script does
- Detects the S24 Ultra by model string `SM_S948U1` via `adb devices -l`
- Runs `adb -s <serial> install -r` to reinstall over any existing build
- Prints confirmation on success
