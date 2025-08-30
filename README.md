
# Dock Editor for Meta Quest
A simple tool for the Quest 3/3s that allows you to edit the pinned applications on the dock.

# About The Project
This app was created so that users with root access can edit their dock's pinned apps without having to disable apps or editing the AUI_PREFERENCES.xml manually.

# Features
Pinned Dock App Customization: Edit, reorder, remove, or add up to 5 apps on your Quest's Dock UI

Add any installed application to the pinned apps, including sideloaded apps and system components. With the ability to select which activity it will launch.

Backup & Restore: Create, restore, and manage local backups of your dock configuration (AUI_PREFERENCES.xml file).

One-Tap Apply: A built-in "Restart SystemUI" button immediately applies your changes without needing a full headset reboot.

Light & Dark Themes

# Requirements
A Meta Quest 3/3s with root access and sideloading enabled.
ADB Shell with root access setup (This is a backup plan explained further down)

# Installation
Download the latest .apk file from the Releases page of this repository.

Sideload the APK onto your Quest headset.

Open the app from the "Unknown Sources" section of your app library.

Grant root access when prompted.

# ⚠️ Disclaimer
I do not take responsibility for any damage caused by this app. Use at your own risk. I have tried my best to make sure that this app does it's best to not mess up. This does not mess with anything other than the AUI_PREFERENCES.xml file, and folders/files in this apps data folder.

If for any reason SystemUX crashes, you can delete the AUI_PREFERENCES.xml file in /data/data/com.oculus.systemux/shared_prefs/ and restart, or disable then enable, SystemUX with adb.

# How It Works
The app uses a persistent root shell (su) to safely read and write to the AUI_PREFERENCES.xml file located at:
/data/data/com.oculus.systemux/shared_prefs/

It parses the JSON array stored within the XML, allows modification through the UI, and then writes the file back with your modifications.

# Acknowledgements
Thanks to @lexd0g for initial info on the XML file.