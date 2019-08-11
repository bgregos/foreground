# Foreground
Foreground is an easy to use task manager app. Designed to be beautiful, simple and unintrusive, Foreground helps you know what needs done without getting in your way.

Currently, Foreground is a beta. Certain features (namely Taskwarrior sync) are not yet complete, and small bugs may be found elsewhere. You can find Foreground on the Play Store here: https://play.google.com/store/apps/details?id=me.bgregos.brighttask

## Syncing
To set up sync, add your taskwarrior server credentials (provided by your taskd server or a service like inthe.am or FreeCinc) to the settings menu, and enable sync using the toggle. A test will be run to check if the settings are valid, and the enable will stick if the config works. You can then press the sync button from the main menu to run a sync. Automatic syncing will be added in a future release.

## Builds
Builds are released on Google Play and under the Releases tab of this page. Please open an issue if you want the app published elsewhere.

## Implementation Details
Unlike existing Taskwarrior apps for Android, Foreground does not run the taskwarrior binary inside, but rather implements a taskwarrior sync client. This allows for greater device compatibility and simplifies UI development but it can cause issues when syncing if not done correctly.
