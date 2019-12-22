package expo.modules.updates;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.EmbeddedLoader;
import expo.modules.updates.loader.FileDownloader;
import expo.modules.updates.loader.Manifest;

public class Launcher {

  private static final String TAG = Launcher.class.getSimpleName();

  private Context mContext;
  private UpdatesDatabase mDatabase;
  private File mUpdatesDirectory;

  private UpdateEntity mLaunchedUpdate = null;
  private String mLaunchAssetFile = null;

  public Launcher(Context context, UpdatesDatabase database, File updatesDirectory) {
    mContext = context;
    mDatabase = database;
    mUpdatesDirectory = updatesDirectory;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLaunchedUpdate;
  }

  public String getLaunchAssetFile() {
    return mLaunchAssetFile;
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

    mLaunchedUpdate = new SelectionPolicyNewest().selectUpdateToLaunch(launchableUpdates);

    // before returning, verify that we have the launch asset on disk
    // according to the database, we should, but something could have gone wrong on disk

    AssetEntity launchAsset = mDatabase.updateDao().loadLaunchAsset(mLaunchedUpdate.id);
    if (launchAsset.relativePath == null) {
      throw new AssertionError("Launch Asset relativePath should not be null");
    }

    File launchAssetFile = new File(mUpdatesDirectory, launchAsset.relativePath);
    boolean launchAssetFileExists = launchAssetFile.exists();
    if (!launchAssetFileExists) {
      // something has gone wrong, we're missing the launch asset
      // first we check to see if a copy is embedded in the binary
      Manifest embeddedManifest = EmbeddedLoader.readEmbeddedManifest(mContext);
      if (embeddedManifest != null) {
        ArrayList<AssetEntity> embeddedAssets = embeddedManifest.getAssetEntityList();
        AssetEntity matchingEmbeddedAsset = null;
        for (AssetEntity embeddedAsset : embeddedAssets) {
          if (embeddedAsset.url.equals(launchAsset.url)) {
            matchingEmbeddedAsset = embeddedAsset;
            break;
          }
        }

        if (matchingEmbeddedAsset != null) {
          try {
            byte[] hash = EmbeddedLoader.copyAssetAndGetHash(matchingEmbeddedAsset, launchAssetFile, mContext);
            if (hash != null && Arrays.equals(hash, launchAsset.hash)) {
              launchAssetFileExists = true;
            }
          } catch (Exception e) {
            // things are really not going our way...
          }
        }
      }
    }

    if (!launchAssetFileExists) {
      // we still don't have the launch asset
      // try downloading it remotely
      try {
        launchAsset = FileDownloader.downloadAssetSync(launchAsset, mUpdatesDirectory, mContext);
        mDatabase.assetDao().updateAsset(launchAsset);
        launchAssetFile = new File(mUpdatesDirectory, launchAsset.relativePath);
      } catch (Exception e) {
        Log.e(TAG, "Could not launch; failed to load update from disk or network", e);
        return null;
      }
    }

    mLaunchAssetFile = launchAssetFile.toString();
    return mLaunchedUpdate;
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
