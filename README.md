![Foreground Logo](docs/img/foreground.png)

Foreground is an easy to use task manager app. Designed to be beautiful, simple and unintrusive, Foreground helps you know what needs done without getting in your way. Foreground also supports syncing with Taskwarrior servers, so your task list can be always with you, no matter your device.

In other words, Foreground helps you know what needs to be in your own foreground!

You can find Foreground on the Play Store here: https://play.google.com/store/apps/details?id=me.bgregos.brighttask

## Features
Foreground includes the following features:
- Mobile task management
- Syncing with Taskwarrior servers, also known as taskservers. This feature is in Beta.
- Automatically sync on an interval
- Task due notifications
- Filters. You can filter by name, project, tags, and more.
- A enhanced tablet experience for optimum productivity

More features are planned!

## Syncing
To set up sync, add your Taskwarrior server credentials (provided by your taskd server or a service like inthe.am or FreeCinc) to the settings menu, and enable sync using the toggle. A test will be run to check if the settings are valid, and the enable will stick if your configuration is correct. You can also configure automatic syncing in this menu, or click the sync button on the main page to start a sync.

## Builds
Builds are released on Google Play and under the Releases tab of this page. F-Droid releases are planned in the near future.

## Issues
Want to report a bug, submit a feature request, or contribute code? Read [CONTRIBUTING.md](CONTRIBUTING.md)! It contains information on this project's procedures, standards, and architecture.

## Current Limitations
Unlike existing Taskwarrior apps for Android, Foreground does not run the taskwarrior binary inside, but rather implements a taskwarrior sync client.

That being said, Foreground currently supports most features of Taskwarrior. Notable exceptions involve handling of recurring events and display/editing of User Defined Attributes. Recurring event instances created by other clients will be shown by Foreground, but Foreground does not create recurring event instances of its own at this time. User Defined Attributes are preserved internally but are not yet exposed for viewing or editing. These limitations will be resolved in future releases.
