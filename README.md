# Stop It! - An Android audio bookmark manager

*Stop It!* creates and manages bookmarks for audio streams played in *any* third party audio player, such as music players or podcatchers. The universal and easy-to-use mechanism that creates a bookmark is to pause and unpause the playback. For each bookmark, *Stop It!* records the audio player's package name, the artist, album and track name, the time code (in seconds) and other meta-data (if available). The user can resume playback for bookmarks at a later time (for selected audio players).

What's the use case? Just like with "normal" bookmarks you know from physical books or the web browser, *Stop It!* let's you resume audio at a later time, because you don't have time right now. It's ideal for scenarios where you're on the go (doing sports, commuting, etc.). Your hands are full, but you don't want to forget about that great inspirational quote you just heard in a podcast, or painstakingly search for that great song you just heard on Spotify an hour later. All you need to do is pause and unpause the track. You no longer need to pull out your phone, unlock it, and manually type the title and current time code into a separate notes app, just to make sure you don't forget it.

![Main screen showing bookmarks](screenshots/mainscreen_v0.2.jpg)

# Usage
1. Download the latest [release](https://github.com/MShekow/stop-it/releases) APK to your Android device (Android 5.1+) and install it.
2. Start the app, navigate to the settings (cog wheel), click on the *Permissions* setting, and enable the notification access for *Stop It!*.
3. Optional: back in the settings dialog, tweak other settings.
4. To create an audio bookmark, pause the playback (e.g. using your headphone controls) and unpause it right away (the grace period is configurable in the settings). Your device will vibrate and play a sound to indicate that a bookmark was created (feedback is configurable in the settings). You will find the bookmark in the main screen of the app.
5. To play back (resume) a bookmark, click the black play button. If it is disabled (gray) this means that the specific audio app does not allow other apps (like ours) to start playing selected apps.

# How it works
Stop It! uses the [Media API](https://developer.android.com/guide/topics/media-apps/media-apps-overview) to get notified about changes in playback state of other audio apps. Creating bookmarks should work for *any* video/audio app. However, the support for *playing* previously created bookmarks is limited. Only few apps allow to start a specific track using the [TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls) API.

Further technical details are documented in the [Wiki](https://github.com/MShekow/stop-it/wiki).

# Contributing
I'm happy to accept PRs for bug fixes or feature implementations.