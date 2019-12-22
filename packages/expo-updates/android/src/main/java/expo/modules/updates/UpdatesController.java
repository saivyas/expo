package expo.modules.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;

import expo.modules.updates.db.Reaper;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.EmbeddedLoader;
import expo.modules.updates.loader.Manifest;
import expo.modules.updates.loader.RemoteLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class UpdatesController {

  private static final String TAG = UpdatesController.class.getSimpleName();

  private static String UPDATES_DIRECTORY_NAME = ".expo-internal";
  private static String URL_PLACEHOLDER = "EXPO_APP_URL";

  private static UpdatesController sInstance;

  private Context mContext;
  private Uri mManifestUrl;
  private File mUpdatesDirectory;
  private Launcher mLauncher;

  private UpdatesDatabase mDatabase;
  private ReentrantLock mDatabaseLock = new ReentrantLock();

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

  private UpdatesController(Context context, Uri url) {
    sInstance = this;
    mContext = context;
    mManifestUrl = url;
    mUpdatesDirectory = getOrCreateUpdatesDirectory();
    mDatabase = UpdatesDatabase.getInstance(context);
  }

  public boolean reloadReactApplication() {
    if (mContext instanceof ReactApplication) {
      UpdatesDatabase database = getDatabase();
      mLauncher = new Launcher(mContext,  mUpdatesDirectory);
      mLauncher.launch(database);
      releaseDatabase();

      final ReactInstanceManager instanceManager = ((ReactApplication) mContext).getReactNativeHost().getReactInstanceManager();
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(new Runnable() {
        @Override
        public void run() {
          instanceManager.recreateReactContextInBackground();
        }
      });
      return true;
    } else {
      return false;
    }
  }

  public void start() {
    UpdatesDatabase database = getDatabase();
    new EmbeddedLoader(mContext, database, mUpdatesDirectory).loadEmbeddedUpdate();
    mLauncher = new Launcher(mContext, mUpdatesDirectory);
    mLauncher.launch(database);
    releaseDatabase();

    if (mManifestUrl != null) {
      AsyncTask.execute(new Runnable() {
        @Override
        public void run() {
          UpdatesDatabase database = getDatabase();
          new RemoteLoader(mContext, database, mUpdatesDirectory)
              .start(mManifestUrl, new RemoteLoader.LoaderCallback() {
                @Override
                public void onFailure(Exception e) {
                  Log.e(TAG, "Failed to download remote update", e);
                  releaseDatabase();
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
                  runReaper();
                }
              });
        }
      });
    }
  }

  public Uri getManifestUrl() {
    return mManifestUrl;
  }

  public File getUpdatesDirectory() {
    return mUpdatesDirectory;
  }

  public UpdatesDatabase getDatabase() {
    // mDatabaseLock.lock();
    return mDatabase;
  }

  public void releaseDatabase() {
    // TODO: fix. this won't work because it might be called from a different thread :(
    // mDatabaseLock.unlock();
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLauncher.getLaunchedUpdate();
  }

  public String getLaunchAssetFile() {
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

  private void runReaper() {
    UpdatesDatabase database = getDatabase();
    Reaper.reapUnusedUpdates(database, mUpdatesDirectory, mLauncher.getLaunchedUpdate());
    releaseDatabase();
  }

  private File getOrCreateUpdatesDirectory() {
    File updatesDirectory = new File(mContext.getFilesDir(), UPDATES_DIRECTORY_NAME);
    boolean exists = updatesDirectory.exists();
    boolean isFile = updatesDirectory.isFile();
    if (!exists || isFile) {
      if (isFile) {
        if (!updatesDirectory.delete()) {
          throw new AssertionError("Updates directory should not be a file");
        }
      }

      if (!updatesDirectory.mkdir()) {
        throw new AssertionError("Updates directory must exist or be able to be created");
      }
    }
    return updatesDirectory;
  }
}
