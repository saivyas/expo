package expo.modules.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
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

public class UpdatesController {

  private static final String TAG = UpdatesController.class.getSimpleName();

  private static String UPDATES_DIRECTORY_NAME = ".expo";
  private static String URL_PLACEHOLDER = "EXPO_APP_URL";

  private static UpdatesController sInstance;

  private Context mContext;
  private Uri mManifestUrl;
  private File mUpdatesDirectory;
  private UpdatesDatabase mDatabase;
  private RemoteLoader mRemoteLoader;
  private Launcher mLauncher;

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
      // TODO: wait for database lock
      mLauncher = new Launcher(mContext, mDatabase, mUpdatesDirectory);
      mLauncher.launch();
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
    new EmbeddedLoader(mContext, mDatabase, mUpdatesDirectory).loadEmbeddedUpdate();
    mLauncher = new Launcher(mContext, mDatabase, mUpdatesDirectory);
    mLauncher.launch();
    if (mRemoteLoader == null && mManifestUrl != null) {
      // TODO: run in an async task, wait until after launched
      mRemoteLoader = new RemoteLoader(mContext, mDatabase, mUpdatesDirectory);
      mRemoteLoader.start(mManifestUrl, new RemoteLoader.LoaderCallback() {
        @Override
        public void onFailure(Exception e) {
          Log.e("erictest", "failure", e);
        }

        @Override
        public void onManifestDownloaded(Manifest manifest) {
          Log.d("erictest", "new manifest downloaded");
        }

        @Override
        public void onSuccess(UpdateEntity update) {
          Log.d("erictest", "success");
          Reaper.reapUnusedUpdates(mDatabase, mUpdatesDirectory, mLauncher.getLaunchedUpdate());
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
    // TODO: use lock
    return mDatabase;
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

  private File getOrCreateUpdatesDirectory() {
    File updatesDirectory = new File(mContext.getFilesDir(), UPDATES_DIRECTORY_NAME);
    boolean exists = updatesDirectory.exists();
    boolean isFile = updatesDirectory.isFile();
    if (!exists || isFile) {
      if (isFile) {
        if (!updatesDirectory.delete()) {
          // TODO: throw error
        }
      }

      if (!updatesDirectory.mkdir()) {
        // TODO: throw error
      }
    }
    return updatesDirectory;
  }
}
