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

  private File mUpdatesDirectory;
  private SelectionPolicy mSelectionPolicy;

  private UpdateEntity mLaunchedUpdate = null;
  private String mLaunchAssetFile = null;
  private Map<String, String> mLocalAssetFiles = null;

  public Launcher(File updatesDirectory, SelectionPolicy selectionPolicy) {
    mUpdatesDirectory = updatesDirectory;
    mSelectionPolicy = selectionPolicy;
  }

  public UpdateEntity getLaunchedUpdate() {
    return mLaunchedUpdate;
  }

  public String getLaunchAssetFile() {
    return mLaunchAssetFile;
  }

  public Map<String, String> getLocalAssetFiles() {
    return mLocalAssetFiles;
  }

  public UpdateEntity launch(UpdatesDatabase database, Context context) {
    mLaunchedUpdate = getLaunchableUpdate(database, context);

    // verify that we have the launch asset on disk
    // according to the database, we should, but something could have gone wrong on disk

    AssetEntity launchAsset = database.updateDao().loadLaunchAsset(mLaunchedUpdate.id);
    if (launchAsset.relativePath == null) {
      throw new AssertionError("Launch Asset relativePath should not be null");
    }

    File launchAssetFile = ensureAssetExistsSynchronously(launchAsset, database, context);
    if (launchAssetFile == null) {
      return null;
    }

    mLaunchAssetFile = launchAssetFile.toString();

    List<AssetEntity> assetEntities = database.assetDao().loadAssetsForUpdate(mLaunchedUpdate.id);
    if (assetEntities == null) {
      return null;
    }
    mLocalAssetFiles = new HashMap<>();
    for (AssetEntity asset : assetEntities) {
      String filename = asset.relativePath;
      if (filename != null) {
        File assetFile = ensureAssetExistsSynchronously(asset, database, context);
        if (assetFile != null) {
          mLocalAssetFiles.put(
              asset.url.toString(),
              assetFile.toString()
          );
        }
      }
    }

    return mLaunchedUpdate;
  }

  public UpdateEntity getLaunchableUpdate(UpdatesDatabase database, Context context) {
    List<UpdateEntity> launchableUpdates = database.updateDao().loadLaunchableUpdates();

    String versionName = UpdateUtils.getBinaryVersion(context);

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

    return mSelectionPolicy.selectUpdateToLaunch(launchableUpdates);
  }

  private File ensureAssetExistsSynchronously(AssetEntity asset, UpdatesDatabase database, Context context) {
    File assetFile = new File(mUpdatesDirectory, asset.relativePath);
    boolean assetFileExists = assetFile.exists();
    if (!assetFileExists) {
      // something has gone wrong, we're missing the launch asset
      // first we check to see if a copy is embedded in the binary
      Manifest embeddedManifest = EmbeddedLoader.readEmbeddedManifest(context);
      if (embeddedManifest != null) {
        ArrayList<AssetEntity> embeddedAssets = embeddedManifest.getAssetEntityList();
        AssetEntity matchingEmbeddedAsset = null;
        for (AssetEntity embeddedAsset : embeddedAssets) {
          if (embeddedAsset.url.equals(asset.url)) {
            matchingEmbeddedAsset = embeddedAsset;
            break;
          }
        }

        if (matchingEmbeddedAsset != null) {
          try {
            byte[] hash = EmbeddedLoader.copyAssetAndGetHash(matchingEmbeddedAsset, assetFile, context);
            if (hash != null && Arrays.equals(hash, asset.hash)) {
              assetFileExists = true;
            }
          } catch (Exception e) {
            // things are really not going our way...
          }
        }
      }
    }

    if (!assetFileExists) {
      // we still don't have the launch asset
      // try downloading it remotely
      try {
        asset = FileDownloader.downloadAssetSync(asset, mUpdatesDirectory, context);
        database.assetDao().updateAsset(asset);
        assetFile = new File(mUpdatesDirectory, asset.relativePath);
        assetFileExists = assetFile.exists();
      } catch (Exception e) {
        Log.e(TAG, "Could not launch; failed to load update from disk or network", e);
        return null;
      }
    }

    if (!assetFileExists) {
      Log.e(TAG, "Could not launch; failed to load update from disk or network");
      return null;
    } else {
      return assetFile;
    }
  }
}
