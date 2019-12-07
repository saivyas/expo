package expo.modules.updates.loader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import expo.modules.updates.UpdateStatus;
import expo.modules.updates.UpdateUtils;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EmbeddedLoader {

  private static String TAG = EmbeddedLoader.class.getSimpleName();

  private static String MANIFEST_FILENAME = "shell-app-manifest.json";

  private Context mContext;
  private UpdatesDatabase mDatabase;
  private File mUpdatesDirectory;

  private UpdateEntity mUpdateEntity;
  private ConcurrentLinkedQueue<AssetEntity> mAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mErroredAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mExistingAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mFinishedAssetQueue = new ConcurrentLinkedQueue<>();

  public EmbeddedLoader(Context context, UpdatesDatabase database, File updatesDirectory) {
    mContext = context;
    mDatabase = database;
    mUpdatesDirectory = updatesDirectory;
  }

  public boolean loadEmbeddedUpdate() {
    boolean success = false;
    try (InputStream stream = mContext.getAssets().open(MANIFEST_FILENAME)) {
      String manifestString = IOUtils.toString(stream, "UTF-8");
      Manifest manifest = Manifest.fromManagedManifestJson(new JSONObject(manifestString));

      UpdateEntity newUpdateEntity = manifest.getUpdateEntity();
      UpdateEntity existingUpdateEntity = mDatabase.updateDao().loadUpdateWithId(newUpdateEntity.id);
      if (existingUpdateEntity != null && existingUpdateEntity.status == UpdateStatus.READY) {
        // hooray, we already have this update downloaded and ready to go!
        mUpdateEntity = existingUpdateEntity;
        success = true;
      } else {
        if (existingUpdateEntity == null) {
          // no update already exists with this ID, so we need to insert it and download everything.
          mUpdateEntity = newUpdateEntity;
          mDatabase.updateDao().insertUpdate(mUpdateEntity);
        } else {
          // we've already partially downloaded the update, so we should use the existing entity.
          // however, it's not ready, so we should try to download all the assets again.
          mUpdateEntity = existingUpdateEntity;
        }
        mAssetQueue = manifest.getAssetEntityQueue();
        copyAssetsFromQueue();
        success = true;
      }
    } catch (Exception e) {
      Log.e(TAG, "Could not load embedded update", e);
    }
    reset();
    return success;
  }

  public void reset() {
    mUpdateEntity = null;
    mAssetQueue = new ConcurrentLinkedQueue<>();
    mErroredAssetQueue = new ConcurrentLinkedQueue<>();
    mFinishedAssetQueue = new ConcurrentLinkedQueue<>();
  }

  private void copyAssetsFromQueue() {
    while (mAssetQueue.size() > 0) {
      AssetEntity asset = mAssetQueue.poll();

      String filename = UpdateUtils.sha1(asset.url.toString()) + "." + asset.type;
      File destination = new File(mUpdatesDirectory, filename);

      if (destination.exists()) {
        mExistingAssetQueue.add(asset);
      } else {
        try (
          InputStream inputStream = mContext.getAssets().open(asset.assetsFilename);
          DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance("SHA-1"))
        ) {
          FileUtils.copyInputStreamToFile(digestInputStream, destination);
          MessageDigest md = digestInputStream.getMessageDigest();
          byte[] hash = md.digest();

          asset.downloadTime = new Date();
          asset.relativePath = filename;
          asset.hash = hash;
          mFinishedAssetQueue.add(asset);
        } catch (Exception e) {
          // TODO: try downloading failed asset from remote url
          Log.e(TAG, "Failed to copy asset " + asset.assetsFilename, e);
          mErroredAssetQueue.add(asset);
        }
      }
    }

    mDatabase.assetDao().insertAssets(Arrays.asList(mFinishedAssetQueue.toArray(new AssetEntity[0])), mUpdateEntity);
    for (AssetEntity asset : mExistingAssetQueue) {
      mDatabase.assetDao().addExistingAssetToUpdate(mUpdateEntity, asset.url, asset.isLaunchAsset);
    }
    if (mErroredAssetQueue.size() == 0) {
      mDatabase.updateDao().markUpdateReady(mUpdateEntity);
    }
  }
}
