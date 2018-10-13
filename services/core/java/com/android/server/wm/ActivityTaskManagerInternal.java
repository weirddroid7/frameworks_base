/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.wm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppProtoEnums;
import android.app.IActivityManager;
import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.voice.IVoiceInteractionSession;
import android.util.SparseIntArray;

import android.util.proto.ProtoOutputStream;
import com.android.internal.app.IVoiceInteractor;
import com.android.server.am.ActivityServiceConnectionsHolder;
import com.android.server.am.PendingIntentRecord;
import com.android.server.am.SafeActivityOptions;
import com.android.server.am.TaskRecord;
import com.android.server.am.UserState;
import com.android.server.am.WindowProcessController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Activity Task manager local system service interface.
 * @hide Only for use within system server
 */
public abstract class ActivityTaskManagerInternal {

    /**
     * Type for {@link #notifyAppTransitionStarting}: The transition was started because we drew
     * the splash screen.
     */
    public static final int APP_TRANSITION_SPLASH_SCREEN =
              AppProtoEnums.APP_TRANSITION_SPLASH_SCREEN; // 1

    /**
     * Type for {@link #notifyAppTransitionStarting}: The transition was started because we all
     * app windows were drawn
     */
    public static final int APP_TRANSITION_WINDOWS_DRAWN =
              AppProtoEnums.APP_TRANSITION_WINDOWS_DRAWN; // 2

    /**
     * Type for {@link #notifyAppTransitionStarting}: The transition was started because of a
     * timeout.
     */
    public static final int APP_TRANSITION_TIMEOUT =
              AppProtoEnums.APP_TRANSITION_TIMEOUT; // 3

    /**
     * Type for {@link #notifyAppTransitionStarting}: The transition was started because of a
     * we drew a task snapshot.
     */
    public static final int APP_TRANSITION_SNAPSHOT =
              AppProtoEnums.APP_TRANSITION_SNAPSHOT; // 4

    /**
     * Type for {@link #notifyAppTransitionStarting}: The transition was started because it was a
     * recents animation and we only needed to wait on the wallpaper.
     */
    public static final int APP_TRANSITION_RECENTS_ANIM =
            AppProtoEnums.APP_TRANSITION_RECENTS_ANIM; // 5

    /**
     * The bundle key to extract the assist data.
     */
    public static final String ASSIST_KEY_DATA = "data";

    /**
     * The bundle key to extract the assist structure.
     */
    public static final String ASSIST_KEY_STRUCTURE = "structure";

    /**
     * The bundle key to extract the assist content.
     */
    public static final String ASSIST_KEY_CONTENT = "content";

    /**
     * The bundle key to extract the assist receiver extras.
     */
    public static final String ASSIST_KEY_RECEIVER_EXTRAS = "receiverExtras";

    public interface ScreenObserver {
        void onAwakeStateChanged(boolean isAwake);
        void onKeyguardStateChanged(boolean isShowing);
    }

    /**
     * Sleep tokens cause the activity manager to put the top activity to sleep.
     * They are used by components such as dreams that may hide and block interaction
     * with underlying activities.
     */
    public static abstract class SleepToken {

        /** Releases the sleep token. */
        public abstract void release();
    }

    /**
     * Acquires a sleep token for the specified display with the specified tag.
     *
     * @param tag A string identifying the purpose of the token (eg. "Dream").
     * @param displayId The display to apply the sleep token to.
     */
    public abstract SleepToken acquireSleepToken(@NonNull String tag, int displayId);

    /**
     * Returns home activity for the specified user.
     *
     * @param userId ID of the user or {@link android.os.UserHandle#USER_ALL}
     */
    public abstract ComponentName getHomeActivityForUser(int userId);

    public abstract void onLocalVoiceInteractionStarted(IBinder callingActivity,
            IVoiceInteractionSession mSession,
            IVoiceInteractor mInteractor);

    /**
     * Callback for window manager to let activity manager know that we are finally starting the
     * app transition;
     *
     * @param reasons A map from windowing mode to a reason integer why the transition was started,
     *                which must be one of the APP_TRANSITION_* values.
     * @param timestamp The time at which the app transition started in
     *                  {@link SystemClock#uptimeMillis()} timebase.
     */
    public abstract void notifyAppTransitionStarting(SparseIntArray reasons, long timestamp);

    /**
     * Callback for window manager to let activity manager know that the app transition was
     * cancelled.
     */
    public abstract void notifyAppTransitionCancelled();

    /**
     * Callback for window manager to let activity manager know that the app transition is finished.
     */
    public abstract void notifyAppTransitionFinished();

    /**
     * Returns the top activity from each of the currently visible stacks. The first entry will be
     * the focused activity.
     */
    public abstract List<IBinder> getTopVisibleActivities();

    /**
     * Callback for window manager to let activity manager know that docked stack changes its
     * minimized state.
     */
    public abstract void notifyDockedStackMinimizedChanged(boolean minimized);

    /**
     * Start activity {@code intents} as if {@code packageName} on user {@code userId} did it.
     *
     * - DO NOT call it with the calling UID cleared.
     * - All the necessary caller permission checks must be done at callsites.
     *
     * @return error codes used by {@link IActivityManager#startActivity} and its siblings.
     */
    public abstract int startActivitiesAsPackage(String packageName,
            int userId, Intent[] intents, Bundle bOptions);

    /**
     * Start intents as a package.
     *
     * @param uid Make a call as if this UID did.
     * @param callingPackage Make a call as if this package did.
     * @param intents Intents to start.
     * @param userId Start the intents on this user.
     * @param validateIncomingUser Set true to skip checking {@code userId} with the calling UID.
     * @param originatingPendingIntent PendingIntentRecord that originated this activity start or
     *        null if not originated by PendingIntent
     */
    public abstract int startActivitiesInPackage(int uid, String callingPackage, Intent[] intents,
            String[] resolvedTypes, IBinder resultTo, SafeActivityOptions options, int userId,
            boolean validateIncomingUser, PendingIntentRecord originatingPendingIntent);

    public abstract int startActivityInPackage(int uid, int realCallingPid, int realCallingUid,
            String callingPackage, Intent intent, String resolvedType, IBinder resultTo,
            String resultWho, int requestCode, int startFlags, SafeActivityOptions options,
            int userId, TaskRecord inTask, String reason, boolean validateIncomingUser,
            PendingIntentRecord originatingPendingIntent);

    /**
     * Start activity {@code intent} without calling user-id check.
     *
     * - DO NOT call it with the calling UID cleared.
     * - The caller must do the calling user ID check.
     *
     * @return error codes used by {@link IActivityManager#startActivity} and its siblings.
     */
    public abstract int startActivityAsUser(IApplicationThread caller, String callingPackage,
            Intent intent, @Nullable Bundle options, int userId);

    /**
     * Called when Keyguard flags might have changed.
     *
     * @param callback Callback to run after activity visibilities have been reevaluated. This can
     *                 be used from window manager so that when the callback is called, it's
     *                 guaranteed that all apps have their visibility updated accordingly.
     */
    public abstract void notifyKeyguardFlagsChanged(@Nullable Runnable callback);

    /**
     * Called when the trusted state of Keyguard has changed.
     */
    public abstract void notifyKeyguardTrustedChanged();

    /**
     * Called after virtual display Id is updated by
     * {@link com.android.server.vr.Vr2dDisplay} with a specific
     * {@param vr2dDisplayId}.
     */
    public abstract void setVr2dDisplayId(int vr2dDisplayId);

    /**
     * Set focus on an activity.
     * @param token The IApplicationToken for the activity
     */
    public abstract void setFocusedActivity(IBinder token);

    public abstract void registerScreenObserver(ScreenObserver observer);

    /**
     * Returns is the caller has the same uid as the Recents component
     */
    public abstract boolean isCallerRecents(int callingUid);

    /**
     * Returns whether the recents component is the home activity for the given user.
     */
    public abstract boolean isRecentsComponentHomeActivity(int userId);

    /**
     * Cancels any currently running recents animation.
     */
    public abstract void cancelRecentsAnimation(boolean restoreHomeStackPosition);

    /**
     * This enforces {@code func} can only be called if either the caller is Recents activity or
     * has {@code permission}.
     */
    public abstract void enforceCallerIsRecentsOrHasPermission(String permission, String func);

    /**
     * Called after the voice interaction service has changed.
     */
    public abstract void notifyActiveVoiceInteractionServiceChanged(ComponentName component);

    /**
     * Set a uid that is allowed to bypass stopped app switches, launching an app
     * whenever it wants.
     *
     * @param type Type of the caller -- unique string the caller supplies to identify itself
     * and disambiguate with other calles.
     * @param uid The uid of the app to be allowed, or -1 to clear the uid for this type.
     * @param userId The user it is allowed for.
     */
    public abstract void setAllowAppSwitches(@NonNull String type, int uid, int userId);

    /**
     * Called when a user has been deleted. This can happen during normal device usage
     * or just at startup, when partially removed users are purged. Any state persisted by the
     * ActivityManager should be purged now.
     *
     * @param userId The user being cleaned up.
     */
    public abstract void onUserStopped(int userId);
    public abstract boolean isGetTasksAllowed(String caller, int callingPid, int callingUid);

    public abstract void onProcessAdded(WindowProcessController proc);
    public abstract void onProcessRemoved(String name, int uid);
    public abstract void onCleanUpApplicationRecord(WindowProcessController proc);
    public abstract int getTopProcessState();
    public abstract boolean isHeavyWeightProcess(WindowProcessController proc);
    public abstract void clearHeavyWeightProcessIfEquals(WindowProcessController proc);
    public abstract void finishHeavyWeightApp();

    public abstract boolean isSleeping();
    public abstract boolean isShuttingDown();
    public abstract boolean shuttingDown(boolean booted, int timeout);
    public abstract void enableScreenAfterBoot(boolean booted);
    public abstract boolean showStrictModeViolationDialog();
    public abstract void showSystemReadyErrorDialogsIfNeeded();

    public abstract long inputDispatchingTimedOut(int pid, boolean aboveSystem, String reason);
    public abstract void onProcessMapped(int pid, WindowProcessController proc);
    public abstract void onProcessUnMapped(int pid);

    public abstract void onPackageDataCleared(String name);
    public abstract void onPackageUninstalled(String name);
    public abstract void onPackageAdded(String name, boolean replacing);
    public abstract void onPackageReplaced(ApplicationInfo aInfo);

    public abstract CompatibilityInfo compatibilityInfoForPackage(ApplicationInfo ai);

    /**
     * Set the corresponding display information for the process global configuration. To be called
     * when we need to show IME on a different display.
     *
     * @param pid The process id associated with the IME window.
     * @param displayId The ID of the display showing the IME.
     */
    public abstract void onImeWindowSetOnDisplay(int pid, int displayId);

    public abstract void sendActivityResult(int callingUid, IBinder activityToken,
            String resultWho, int requestCode, int resultCode, Intent data);
    public abstract void clearPendingResultForActivity(
            IBinder activityToken, WeakReference<PendingIntentRecord> pir);
    public abstract IIntentSender getIntentSender(int type, String packageName,
            int callingUid, int userId, IBinder token, String resultWho,
            int requestCode, Intent[] intents, String[] resolvedTypes, int flags,
            Bundle bOptions);

    /** @return the service connection holder for a given activity token. */
    public abstract ActivityServiceConnectionsHolder getServiceConnectionsHolder(IBinder token);

    /** @return The intent used to launch the home activity. */
    public abstract Intent getHomeIntent();
    public abstract boolean startHomeActivity(int userId, String reason);
    /** @return true if the given process is the factory test process. */
    public abstract boolean isFactoryTestProcess(WindowProcessController wpc);
    public abstract void updateTopComponentForFactoryTest();
    public abstract void handleAppDied(WindowProcessController wpc, boolean restarting,
            Runnable finishInstrumentationCallback);
    public abstract void closeSystemDialogs(String reason);

    /** Removes all components (e.g. activities, recents, ...) belonging to a disabled package. */
    public abstract void cleanupDisabledPackageComponents(
            String packageName, Set<String> disabledClasses, int userId, boolean booted);

    /** Called whenever AM force stops a package. */
    public abstract boolean onForceStopPackage(String packageName, boolean doit,
            boolean evenPersistent, int userId);
    /**
     * Resumes all top activities in the system if they aren't resumed already.
     * @param scheduleIdle If the idle message should be schedule after the top activities are
     *                     resumed.
     */
    public abstract void resumeTopActivities(boolean scheduleIdle);

    /** Called by AM just before it binds to an application process. */
    public abstract void preBindApplication(WindowProcessController wpc);

    /** Called by AM when an application process attaches. */
    public abstract boolean attachApplication(WindowProcessController wpc) throws RemoteException;

    /** @see IActivityManager#notifyLockedProfile(int) */
    public abstract void notifyLockedProfile(@UserIdInt int userId, int currentUserId);

    /** @see IActivityManager#startConfirmDeviceCredentialIntent(Intent, Bundle) */
    public abstract void startConfirmDeviceCredentialIntent(Intent intent, Bundle options);

    /** Writes current activity states to the proto stream. */
    public abstract void writeActivitiesToProto(ProtoOutputStream proto);

    /**
     * Saves the current activity manager state and includes the saved state in the next dump of
     * activity manager.
     */
    public abstract void saveANRState(String reason);

    /** Clears the previously saved activity manager ANR state. */
    public abstract void clearSavedANRState();

    /** Dump the current state based on the command. */
    public abstract void dump(String cmd, FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, boolean dumpClient, String dumpPackage);

    /** Dump the current state for inclusion in process dump. */
    public abstract boolean dumpForProcesses(FileDescriptor fd, PrintWriter pw, boolean dumpAll,
            String dumpPackage, int dumpAppId, boolean needSep, boolean testPssMode,
            int wakefulness);

    /** Writes the current window process states to the proto stream. */
    public abstract void writeProcessesToProto(ProtoOutputStream proto, String dumpPackage);

    /** Dump the current activities state. */
    public abstract boolean dumpActivity(FileDescriptor fd, PrintWriter pw, String name,
            String[] args, int opti, boolean dumpAll, boolean dumpVisibleStacksOnly,
            boolean dumpFocusedStackOnly);

    /** @return true if it the activity management system is okay with GC running now. */
    public abstract boolean canGcNow();

    /** @return the process for the top-most resumed activity in the system. */
    public abstract WindowProcessController getTopApp();

    /** Generate oom-score-adjustment rank for all tasks in the system based on z-order. */
    public abstract void rankTaskLayersIfNeeded();

    /** Destroy all activities. */
    public abstract void scheduleDestroyAllActivities(String reason);

    /** Remove user association with activities. */
    public abstract void removeUser(int userId);

    /** Switch current focused user for activities. */
    public abstract boolean switchUser(int userId, UserState userState);

    /** Called whenever an app crashes. */
    public abstract void onHandleAppCrash(WindowProcessController wpc);
}
