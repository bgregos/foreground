# Foreground
Foreground is an easy to use task manager app. Designed to be beautiful, simple and unintrusive, Foreground helps you know what needs done without getting in your way. Foreground also supports syncing with Taskwarrior servers.

You can find Foreground on the Play Store here: https://play.google.com/store/apps/details?id=me.bgregos.brighttask

## Syncing
To set up sync, add your taskwarrior server credentials (provided by your taskd server or a service like inthe.am or FreeCinc) to the settings menu, and enable sync using the toggle. A test will be run to check if the settings are valid, and the enable will stick if the config works. You can also configure automatic syncing in this menu, or click the sync button on the main page to start a sync.

## Builds
Builds are released on Google Play and under the Releases tab of this page. Please open an issue if you want the app published elsewhere.

## Implementation Details
Unlike existing Taskwarrior apps for Android, Foreground does not run the taskwarrior binary inside, but rather implements a taskwarrior sync client. This allows for greater device compatibility and simplifies UI development but it can cause issues when syncing if not done correctly.

## Current Limitations
Foreground currently supports most features of Taskwarrior. Notable exceptions involve handling of recurring events and display/editing of User Defined Attributes. Recurring event instances created by other clients will be shown by Foreground, but Foreground does not create recurring event instances of its own at this time. User Definined attributes are preserved internally but are not yet exposed for viewing or editing. These limitations will be resolved in future releases.
