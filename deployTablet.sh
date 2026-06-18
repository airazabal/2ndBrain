TABLET_TRANSPORT=$(adb devices -l | grep 'SM_X930' | awk '{print $NF}' | grep -o '[0-9]*' | head -1)
TABLET=$(adb devices -l | grep 'SM_X930' | awk '{print $1}')
echo "performing adb install on device: $TABLET"
adb -t "$TABLET_TRANSPORT" install -r /Users/alexirazabal/AndroidStudioProjects/2ndBrain/app/build/outputs/apk/debug/app-debug.apk
echo "Deployed Tablet"
