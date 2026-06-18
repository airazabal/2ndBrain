PHONE=$(adb devices -l | grep 'SM_S948U1' | grep -v '(2)' | awk '{print $1}')
echo "performing adb install on device: $PHONE"
adb -s "$PHONE" install -r /Users/alexirazabal/AndroidStudioProjects/2ndBrain/app/build/outputs/apk/debug/app-debug.apk
echo "Phone Deployed"
