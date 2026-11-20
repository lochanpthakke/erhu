package com.erhu.erhu.feature.library.remote

/**
 * Which remote libraries this build can offer. Set once at start-up by the app from its build
 * configuration. Google Drive sign-in only works for builds whose signing certificate has a
 * registered OAuth client, so public builds show Drive as unavailable; Navidrome / Subsonic always work.
 */
object RemoteLibraryFeatures {
    @Volatile
    var googleDriveAvailable: Boolean = false
}
