package com.mellefresh13.radio

/**
 * Destructive session-only actions must not be exposed to arbitrary installed apps.
 *
 * Media playback controls remain available according to the existing session contract;
 * this policy only gates the additional "Close Radio" command.
 */
internal object MediaControllerAccessPolicy {
    fun canCloseRadio(controllerIsTrusted: Boolean): Boolean = controllerIsTrusted
}
