package dev.jobscn.tvhome;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.List;

/** Foreground-only MediaSession observer. Call its methods from the main thread. */
public final class MediaBridge {
    public static final String LAST_PACKAGE_KEY = "media_last_package";
    private final Context context;
    private final Runnable onChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ComponentName listenerComponent;
    private final MediaSessionManager manager;
    private final SharedPreferences preferences;
    private boolean started, registered, accessGranted;
    private MediaController controller;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = sessions -> {
        if (!started) return;
        try { select(sessions); }
        catch (RuntimeException unavailable) { invalidateAccess(); }
    };
    private MediaController.Callback controllerCallback;

    private MediaController.Callback callbackFor(final MediaController expected) {
        return new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                if (controller == expected) changed();
            }
            @Override public void onMetadataChanged(MediaMetadata metadata) {
                if (controller == expected) changed();
            }
            @Override public void onSessionDestroyed() {
                if (!started || controller != expected) return;
                detachController();
                refresh();
            }
        };
    }

    public MediaBridge(Context context, Runnable onChanged) {
        this.context = context.getApplicationContext();
        this.onChanged = onChanged;
        listenerComponent = new ComponentName(context, MediaAccessService.class);
        manager = context.getSystemService(MediaSessionManager.class);
        preferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    public void start() {
        if (started) return;
        started = true;
        try {
            NotificationManager notifications = context.getSystemService(NotificationManager.class);
            accessGranted = notifications != null
                    && notifications.isNotificationListenerAccessGranted(listenerComponent);
            if (accessGranted && manager != null) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler);
                registered = true;
                refresh();
            } else {
                changed();
            }
        } catch (RuntimeException unavailable) {
            invalidateAccess();
        }
    }

    public void stop() {
        started = false;
        if (registered && manager != null) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsListener); }
            catch (RuntimeException ignored) { }
        }
        registered = false;
        detachController();
        handler.removeCallbacksAndMessages(null);
    }

    private void refresh() {
        if (!started || manager == null) return;
        try { select(manager.getActiveSessions(listenerComponent)); }
        catch (RuntimeException unavailable) { invalidateAccess(); }
    }

    private void select(List<MediaController> sessions) {
        if (!started) return;
        MediaController chosen = null;
        if (sessions != null) {
            // Keep a valid selected session stable as other apps update metadata.
            if (controller != null) {
                for (MediaController candidate : sessions) {
                    if (candidate.getSessionToken().equals(controller.getSessionToken())) {
                        chosen = controller;
                        break;
                    }
                }
            }
            if (chosen == null) {
                for (MediaController candidate : sessions) {
                    PlaybackState state = candidate.getPlaybackState();
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        chosen = candidate;
                        break;
                    }
                }
            }
            if (chosen == null && !sessions.isEmpty()) chosen = sessions.get(0);
        }
        if (chosen != controller) {
            detachController();
            controller = chosen;
            if (controller != null) {
                try {
                    controllerCallback = callbackFor(controller);
                    controller.registerCallback(controllerCallback, handler);
                    // History is only a launch destination, never proof of a live session.
                    preferences.edit().putString(LAST_PACKAGE_KEY, controller.getPackageName()).apply();
                } catch (RuntimeException unavailable) { detachController(); }
            }
        }
        changed();
    }

    private void detachController() {
        if (controller != null) {
            try { if (controllerCallback != null) controller.unregisterCallback(controllerCallback); }
            catch (RuntimeException ignored) { }
            controller = null;
            controllerCallback = null;
        }
    }

    private void invalidateAccess() {
        accessGranted = false;
        if (registered && manager != null) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsListener); }
            catch (RuntimeException ignored) { }
        }
        registered = false;
        detachController();
        changed();
    }

    private void changed() {
        if (started && onChanged != null) onChanged.run();
    }

    public Snapshot snapshot() {
        if (!started || controller == null) return Snapshot.empty(accessGranted);
        try {
            PlaybackState playback = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            int state = playback == null ? PlaybackState.STATE_NONE : playback.getState();
            long actions = playback == null ? 0 : playback.getActions();
            boolean playing = state == PlaybackState.STATE_PLAYING;
            boolean paused = state == PlaybackState.STATE_PAUSED;
            boolean canToggle = (playing && (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0)
                    || (paused && (actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0);
            String title = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null || title.isEmpty()) title = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            return new Snapshot(accessGranted, true, playing, canToggle,
                    (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0,
                    (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0,
                    controller.getPackageName(), clean(title), clean(artist));
        } catch (RuntimeException unavailable) {
            // An invalid session must lose transport controls immediately.
            detachController();
            return Snapshot.empty(accessGranted);
        }
    }

    public boolean playPause() { return command(0); }
    public boolean previous() { return command(-1); }
    public boolean next() { return command(1); }

    private boolean command(int direction) {
        Snapshot state = snapshot();
        if (!started || controller == null || !state.live) return false;
        if ((direction == 0 && !state.canPlayPause) || (direction < 0 && !state.canPrevious)
                || (direction > 0 && !state.canNext)) return false;
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (direction < 0) controls.skipToPrevious();
            else if (direction > 0) controls.skipToNext();
            else if (state.playing) controls.pause();
            else controls.play();
            // Do not optimistically change playback state: wait for the provider callback.
            return true;
        } catch (RuntimeException unavailable) {
            detachController();
            changed();
            return false;
        }
    }

    /** Call only in response to the user's explicit settings action. May be unsupported. */
    public static Intent accessSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        return intent.resolveActivity(context.getPackageManager()) != null ? intent : null;
    }

    private static String clean(String value) { return value == null ? "" : value; }

    public static final class Snapshot {
        public final boolean accessGranted, live, playing, canPlayPause, canPrevious, canNext;
        public final String packageName, title, artist;
        Snapshot(boolean access, boolean live, boolean playing, boolean toggle,
                boolean previous, boolean next, String name, String title, String artist) {
            this.accessGranted = access; this.live = live; this.playing = playing;
            this.canPlayPause = toggle; this.canPrevious = previous; this.canNext = next;
            this.packageName = name; this.title = title; this.artist = artist;
        }
        static Snapshot empty(boolean access) {
            return new Snapshot(access, false, false, false, false, false, "", "", "");
        }
    }
}
