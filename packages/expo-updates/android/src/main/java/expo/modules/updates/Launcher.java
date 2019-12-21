package expo.modules.updates;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.FileDownloader;

public class Launcher {

  private static String TAG = Launcher.class.getSimpleName();

  private Context mContext;
  private UpdatesDatabase mDatabase;
  private File mUpdatesDirectory;

  private UpdateEntity mLaunchedUpdate;

  public Launcher(Context context, UpdatesDatabase database, File updatesDirectory) {
    mContext = context;
    mDatabase = database;
    mUpdatesDirectory = updatesDirectory;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLaunchedUpdate;
  }

  public UpdateEntity launch() {
    List<UpdateEntity> launchableUpdates = mDatabase.updateDao().loadLaunchableUpdates();

    String versionName = UpdateUtils.getBinaryVersion(mContext);

    if (versionName != null) {
      List<UpdateEntity> launchableUpdatesCopy = new ArrayList<>(launchableUpdates);
      for (UpdateEntity update : launchableUpdatesCopy) {
        String[] binaryVersions = update.binaryVersions.split(",");
        boolean matches = false;
        for (String version : binaryVersions) {
          if (version.equals(versionName)) {
            matches = true;
            break;
          }
        }
        if (!matches) {
          launchableUpdates.remove(update);
        }
      }
    }

    return new SelectionPolicyNewest().selectUpdateToLaunch(launchableUpdates);
  }

  public String getLaunchAssetFile() {
    if (mLaunchedUpdate == null) {
      Log.d("erictest", "Could not find an update to launch");
      return null;
    }

    AssetEntity launchAsset = mDatabase.updateDao().loadLaunchAsset(mLaunchedUpdate.id);
    if (launchAsset.relativePath != null) {
      File launchAssetFile = new File(mUpdatesDirectory, launchAsset.relativePath);
      if (launchAssetFile.exists()) {
        return launchAssetFile.toString();
      }
    }

    // something has gone wrong, we're missing the launch asset
    // try to redownload
    // TODO: check embedded assets for this first!

    // TODO: urg. should use okhttp sync method for this :(
    final ArrayBlockingQueue<AssetEntity> blockingQueue = new ArrayBlockingQueue<>(1);
    FileDownloader.downloadAsset(launchAsset, mUpdatesDirectory, mContext, new FileDownloader.AssetDownloadCallback() {
      @Override
      public void onFailure(Exception e, AssetEntity assetEntity) {
        Log.d("erictest", "Failed to load update from disk or network");
      }

      @Override
      public void onSuccess(AssetEntity assetEntity, boolean isNew) {
        blockingQueue.add(assetEntity);
      }
    });

    try {
      AssetEntity downloadedLaunchAsset = blockingQueue.take();
      mDatabase.assetDao().updateAsset(downloadedLaunchAsset);
      return new File(mUpdatesDirectory, downloadedLaunchAsset.relativePath).toString();
    } catch (Exception e) {
      Log.d("erictest", "exception while waiting for blocking queue", e);
      return null;
    }
  }

  public Map<String, String> getLocalAssetFiles() {
    if (mLaunchedUpdate == null) {
      return null;
    }

    List<AssetEntity> assetEntities = mDatabase.assetDao().loadAssetsForUpdate(mLaunchedUpdate.id);
    if (assetEntities == null) {
      return null;
    }
    Map<String, String> localAssetFiles = new HashMap<>();
    for (int i = 0; i < assetEntities.size(); i++) {
      String filename = assetEntities.get(i).relativePath;
      if (filename != null) {
        localAssetFiles.put(
            assetEntities.get(i).url.toString(),
            new File(mUpdatesDirectory, filename).toString()
        );
      }
    }
    return localAssetFiles;
  }
}
