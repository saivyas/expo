package expo.modules.updates;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import expo.modules.updates.db.Reaper;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.EmbeddedLoader;
import expo.modules.updates.loader.Manifest;
import expo.modules.updates.loader.RemoteLoader;

import java.io.File;
import java.util.Map;

public class UpdatesController {

  private static final String TAG = UpdatesController.class.getSimpleName();

  private static String URL_PLACEHOLDER = "EXPO_APP_URL";

  public static final String UPDATES_EVENT_NAME = "Expo.nativeUpdatesEvent";
  public static final String UPDATE_AVAILABLE_EVENT = "updateAvailable";
  public static final String UPDATE_NO_UPDATE_AVAILABLE_EVENT = "noUpdateAvailable";
  public static final String UPDATE_ERROR_EVENT = "error";

  private static UpdatesController sInstance;

  private Context mContext;
  private Uri mManifestUrl;
  private File mUpdatesDirectory;
  private Launcher mLauncher;

  // launch conditions
  private boolean mIsReadyToLaunch = false;
  private boolean mTimeoutFinished = false;

  private DatabaseHolder mDatabaseHolder;

  private UpdatesController(Context context, Uri url) {
    sInstance = this;
    mContext = context;
    mManifestUrl = url;
    mUpdatesDirectory = UpdateUtils.getOrCreateUpdatesDirectory(context);
    mDatabaseHolder = new DatabaseHolder(UpdatesDatabase.getInstance(context));
  }

  public static UpdatesController getInstance() {
    return sInstance;
  }

  public static void initialize(Context context) {
    if (sInstance == null) {
      String urlString = context.getString(R.string.expo_app_url);
      Uri url = URL_PLACEHOLDER.equals(urlString) ? null : Uri.parse(urlString);
      new UpdatesController(context, url);
    }
  }

  public synchronized void start() {
    int delay = 0;
    try {
      delay = Integer.parseInt(mContext.getString(R.string.expo_updates_launch_wait_ms));
    } catch (NumberFormatException e) {
      Log.e(TAG, "Could not parse expo_updates_launch_wait_ms; defaulting to 0", e);
    }
    new Handler().postDelayed(() -> this.finishTimeout(false), delay);

    UpdatesDatabase database = getDatabase();
    new EmbeddedLoader(mContext, database, mUpdatesDirectory).loadEmbeddedUpdate();
    mLauncher = new Launcher(mContext, mUpdatesDirectory);
    mLauncher.launch(database);
    releaseDatabase();

    mIsReadyToLaunch = true;
    notify();

    if (shouldCheckForUpdateOnLaunch()) {
      AsyncTask.execute(() -> {
        UpdatesDatabase db = getDatabase();
        new RemoteLoader(mContext, db, mUpdatesDirectory)
            .start(mManifestUrl, new RemoteLoader.LoaderCallback() {
              @Override
              public void onFailure(Exception e) {
                Log.e(TAG, "Failed to download remote update", e);
                releaseDatabase();

                WritableMap params = Arguments.createMap();
                params.putString("message", e.getMessage());
                sendEventToReactContext(UPDATE_ERROR_EVENT, params);

                runReaper();
              }

              @Override
              public boolean onManifestDownloaded(Manifest manifest) {
                UpdateEntity launchedUpdate = mLauncher.getLaunchedUpdate();
                if (launchedUpdate == null) {
                  return true;
                }
                return new SelectionPolicyNewest().shouldLoadNewUpdate(manifest.getUpdateEntity(), launchedUpdate);
              }

              @Override
              public void onSuccess(UpdateEntity update) {
                releaseDatabase();

                finishTimeout(true);

                if (update == null) {
                  sendEventToReactContext(UPDATE_NO_UPDATE_AVAILABLE_EVENT, null);
                } else {
                  WritableMap params = Arguments.createMap();
                  params.putString("manifestString", update.metadata.toString());
                  sendEventToReactContext(UPDATE_AVAILABLE_EVENT, params);
                }

                runReaper();
              }
            });
      });
    }
  }

  public boolean reloadReactApplication() {
    if (mContext instanceof ReactApplication) {
      UpdatesDatabase database = getDatabase();
      mLauncher = new Launcher(mContext, mUpdatesDirectory);
      mLauncher.launch(database);
      releaseDatabase();

      final ReactInstanceManager instanceManager = ((ReactApplication) mContext).getReactNativeHost().getReactInstanceManager();
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(instanceManager::recreateReactContextInBackground);
      return true;
    } else {
      return false;
    }
  }

  public synchronized String getLaunchAssetFile() {
    while (!mIsReadyToLaunch || !mTimeoutFinished) {
      try {
        wait();
      } catch (InterruptedException e) {
        Log.e(TAG, "Interrupted while waiting for launch asset file", e);
      }
    }

    if (mLauncher == null) {
      return null;
    }
    return mLauncher.getLaunchAssetFile();
  }

  public Map<String, String> getLocalAssetFiles() {
    if (mLauncher == null) {
      return null;
    }
    return mLauncher.getLocalAssetFiles();
  }

  public Uri getManifestUrl() {
    return mManifestUrl;
  }

  public File getUpdatesDirectory() {
    return mUpdatesDirectory;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLauncher.getLaunchedUpdate();
  }

  private class DatabaseHolder {
    private UpdatesDatabase mDatabase;
    private boolean isInUse = false;

    public DatabaseHolder(UpdatesDatabase database) {
      mDatabase = database;
    }

    public synchronized UpdatesDatabase getDatabase() {
      while (isInUse) {
        try {
          wait();
        } catch (InterruptedException e) {
          Log.e(TAG, "Interrupted while waiting for database", e);
        }
      }

      isInUse = true;
      return mDatabase;
    }

    public synchronized void releaseDatabase() {
      isInUse = false;
      notify();
    }
  }

  public UpdatesDatabase getDatabase() {
    return mDatabaseHolder.getDatabase();
  }

  public void releaseDatabase() {
    mDatabaseHolder.releaseDatabase();
  }

  private boolean shouldCheckForUpdateOnLaunch() {
    if (mManifestUrl == null) {
      return false;
    }

    String developerSetting = mContext.getString(R.string.expo_updates_check_on_launch);
    if ("ALWAYS".equals(developerSetting)) {
      return true;
    } else if ("NEVER".equals(developerSetting)) {
      return false;
    } else if ("WIFI_ONLY".equals(developerSetting)) {
      ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) {
        Log.e(TAG, "Could not determine active network connection is metered; not checking for updates");
        return false;
      }
      return !cm.isActiveNetworkMetered();
    } else {
      Log.e(TAG, "Invalid value for expo_updates_check_on_launch; defaulting to ALWAYS");
      return true;
    }
  }

  private synchronized void finishTimeout(boolean relaunch) {
    if (mTimeoutFinished) {
      // already finished, do nothing
      return;
    }

    if (relaunch) {
      UpdatesDatabase database = getDatabase();
      Launcher newLauncher = new Launcher(mContext, mUpdatesDirectory);
      newLauncher.launch(database);
      releaseDatabase();

      mLauncher = newLauncher;
    }

    mTimeoutFinished = true;
    notify();
  }

  private void runReaper() {
    UpdatesDatabase database = getDatabase();
    Reaper.reapUnusedUpdates(database, mUpdatesDirectory, mLauncher.getLaunchedUpdate());
    releaseDatabase();
  }

  private void sendEventToReactContext(final String eventName, final WritableMap params) {
    if (mContext instanceof ReactApplication) {
      final ReactInstanceManager instanceManager = ((ReactApplication) mContext).getReactNativeHost().getReactInstanceManager();
      AsyncTask.execute(() -> {
        try {
          ReactContext reactContext = null;
          // in case we're trying to send an event before the reactContext has been initialized
          // continue to retry for 5000ms
          for (int i = 0; i < 5; i++) {
            reactContext = instanceManager.getCurrentReactContext();
            if (reactContext != null) {
              break;
            }
            Thread.sleep(1000);
          }

          if (reactContext != null) {
            DeviceEventManagerModule.RCTDeviceEventEmitter emitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
            if (emitter != null) {
              WritableMap eventParams = params;
              if (eventParams == null) {
                eventParams = Arguments.createMap();
              }
              eventParams.putString("type", eventName);
              emitter.emit(UPDATES_EVENT_NAME, eventParams);
              return;
            }
          }

          Log.e(TAG, "Could not emit " + eventName + " event; no event emitter was found.");
        } catch (Exception e) {
          Log.e(TAG, "Could not emit " + eventName + " event; no react context was found.");
        }
      });
    } else {
      Log.e(TAG, "Could not emit " + eventName + " event; UpdatesController was not initialized with an instance of ReactApplication.");
    }
  }
}
