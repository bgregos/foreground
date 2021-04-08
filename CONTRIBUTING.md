# Contributing to Foreground
Thank you for your interest in improving Foreground!

## Goals
Foreground aspires to be a fast, simple, and user-friendly mobile task manager.

## Filing Issues and Feature Requests
If you have found a bug or want a new feature in Foreground and wish to report it, thank you!

All issues are tracked in GitHub Issues, located on Foreground's [GitHub Repository](https://github.com/bgregos/foreground). Before opening an issue, search through the existing issues to ensure this hasn't been already reported. Don't worry too much on this - if your report happens to be a duplicate, I will link the issues, notify you, and close the duplicate.

### Bugs
- Try to include as much information as you can. Everything helps! Try to include steps to reproduce, device, OS version, logs, and which Taskwarrior service (if any) you use.
- Add the `bug` tag to the issue.

### Feature Requests
- Include information on how you want this feature to work.
- Add the `enhancement` tag to the issue.

## Contribute Code

### Before beginning
- Whether you're fixing a bug, making a technical improvement, or creating a new feature, ensure that the issue is not already being worked on by someone else. The `assignee` area of GitHub Issues is used for this.

#### For feature requests
For feature requests work with `bgregos`, the primary mantainer, to ensure that the feature is ready to include in Foreground. Plans for UI, functionality, and scope should be addressed before adding a feature. Anything tagged `Good first issue` is ready to be picked up!

### Code overview
Foreground's main codebase is 100% Kotlin. All code contributed to the main codebase must be Kotlin and should try to adhere to best coding practices. All submitted code must pass code review before being merged.

#### Dependency note
Foreground does currently include a module dependency named `taskwarrior-java-client` that is written in Java and included by source for F-Droid compatibility reasons. This is a slightly modified (and currently outdated) fork of `taskwarrior-java-client` that will be removed in the feature and migrated to the standard upstream release.

#### Architecture
Foreground uses the Google recommended Android MVVM architecture as explained by this [document](https://developer.android.com/jetpack/guide#recommended-app-arch).

##### Views
Most of Foreground exists in a single activity, where different screens are represented by fragments. On phones, one fragment is displayed at a time. On tablet, fragments are arranged into a primary-detail view. The task list fragment is always displayed as a primary, and other options are displayed in the detail pane. New features should be added as new detail fragments where sensible.

Foreground aims to support small-screen phones up through tablets. Be sure that your layouts look good on a diverse set of devices, and that long text runs are handled gracefully.

##### ViewModels
Foreground uses Jetpack ViewModels. ViewModel creation and dependency injection is handled by `Dagger`.

##### Data layer
Foreground uses the repository pattern for data. A repository may be self-contained or may utilize one or more Data Sources for fetching data. Data persistence should typically be handled by Room, but user settings can be handled by SharedPreferences.

Currently, task storage is accomplished by serializing the task list and storing the resulting string in SharedPreferences. This is not recommended and will be removed in a future release.

All data access from below the Repository level should go through a Repository. Do not persist data directly from a View or ViewModel.

Repositories (and their dependencies) are satisfied by `Dagger`.

#### Taskwarrior Sync
Taskwarrior sync is accomplished via the `taskwarrior-java-client` library. As a result, Foreground does not embed a `taskwarrior` binary.

### Testing
Before creating a PR, you should test your changes on both phones and tablets. Emulators are fine for this. A full regression will be performed by the project mantainer before each release.