package expo.modules.updates.loader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import expo.modules.updates.UpdateStatus;
import expo.modules.updates.UpdateUtils;
import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RemoteLoader {

  private static String TAG = RemoteLoader.class.getSimpleName();

  private Context mContext;
  private UpdatesDatabase mDatabase;
  private File mUpdatesDirectory;

  private UpdateEntity mUpdateEntity;
  private LoaderCallback mCallback;
  private ConcurrentLinkedQueue<AssetEntity> mAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mRetryAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mErroredAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mExistingAssetQueue = new ConcurrentLinkedQueue<>();
  private ConcurrentLinkedQueue<AssetEntity> mFinishedAssetQueue = new ConcurrentLinkedQueue<>();

  public interface LoaderCallback {
    void onFailure(Exception e);
    void onSuccess(UpdateEntity update);
  }

  public RemoteLoader(Context context, UpdatesDatabase database, File updatesDirectory) {
    mContext = context;
    mDatabase = database;
    mUpdatesDirectory = updatesDirectory;
  }

  public void reset() {
    mUpdateEntity = null;
    mCallback = null;
    mAssetQueue = new ConcurrentLinkedQueue<>();
    mRetryAssetQueue = new ConcurrentLinkedQueue<>();
    mErroredAssetQueue = new ConcurrentLinkedQueue<>();
    mExistingAssetQueue = new ConcurrentLinkedQueue<>();
    mFinishedAssetQueue = new ConcurrentLinkedQueue<>();
  }

  public void start(Uri url, LoaderCallback callback) {
    if (mCallback != null) {
      callback.onFailure(new Exception("RemoteLoader has already started. Create a new instance in order to load multiple URLs in parallel."));
      return;
    }

    mCallback = callback;

    downloadManifest(url);
  }

  private void downloadManifest(final Uri url) {
    Network.downloadData(Network.addHeadersToManifestUrl(url, mContext), new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        Log.e(TAG, e.getMessage(), e);
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          finishWithError("Failed to download manifest from uri: " + url, new Exception(response.body().string()));
          return;
        }

        try {
          String manifestString = response.body().string();
          JSONObject manifestJson = new JSONObject(manifestString);
          if (manifestJson.has("manifestString") && manifestJson.has("signature")) {
            final String innerManifestString = manifestJson.getString("manifestString");
            Crypto.verifyPublicRSASignature(
                    innerManifestString,
                    manifestJson.getString("signature"),
                    new Crypto.RSASignatureListener() {
                      @Override
                      public void onError(Exception e, boolean isNetworkError) {
                        finishWithError("Could not validate signed manifest", e);
                      }

                      @Override
                      public void onCompleted(boolean isValid) {
                        if (isValid) {
                          try {
                            Manifest manifest = Manifest.fromManagedManifestJson(new JSONObject(innerManifestString));
                            processManifest(manifest);
                          } catch (JSONException e) {
                            finishWithError("Failed to parse manifest data", e);
                          }
                        } else {
                          finishWithError("Manifest signature is invalid; aborting", new Exception("Manifest signature is invalid"));
                        }
                      }
                    }
            );
          } else {
            Manifest manifest = Manifest.fromManagedManifestJson(manifestJson);
            processManifest(manifest);
          }
        } catch (Exception e) {
          finishWithError("Failed to parse manifest data", e);
        }
      }
    });
  }

  private void processManifest(Manifest manifest) {
    try {
      UpdateEntity newUpdateEntity = manifest.getUpdateEntity();
      UpdateEntity existingUpdateEntity = mDatabase.updateDao().loadUpdateWithId(newUpdateEntity.id);
      if (existingUpdateEntity != null && existingUpdateEntity.status == UpdateStatus.READY) {
        // hooray, we already have this update downloaded and ready to go!
        mUpdateEntity = existingUpdateEntity;
        finishWithSuccess();
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
        assetDownloadLoop();
      }
    } catch (Exception e) {
      finishWithError("Failed to parse manifest data", e);
    }
  }

  private void finishWithSuccess() {
    mCallback.onSuccess(mUpdateEntity);
    reset();
  }

  private void finishWithError(String message, Exception e) {
    Log.e(TAG, message, e);
    mCallback.onFailure(e);
    reset();
  }

  // asset download loop

  private void assetDownloadLoop() {
    if (mAssetQueue.size() > 0) {
      downloadAsset(mAssetQueue.poll(), new AssetDownloadCallback() {
        @Override
        public void onFailure(Exception e, AssetEntity assetEntity) {
          Log.e(TAG, "Failed to download asset, retrying from " + assetEntity.url, e);
          mRetryAssetQueue.add(assetEntity);
          assetDownloadLoop();
        }

        @Override
        public void onSuccess(AssetEntity assetEntity, boolean isNew) {
          if (isNew) {
            mFinishedAssetQueue.add(assetEntity);
          } else {
            mExistingAssetQueue.add(assetEntity);
          }
          assetDownloadLoop();
        }
      });
    } else if (mRetryAssetQueue.size() > 0) {
      downloadAsset(mRetryAssetQueue.poll(), new AssetDownloadCallback() {
        @Override
        public void onFailure(Exception e, AssetEntity assetEntity) {
          Log.e(TAG, "Failed to download asset from " + assetEntity.url, e);
          mErroredAssetQueue.add(assetEntity);
          assetDownloadLoop();
        }

        @Override
        public void onSuccess(AssetEntity assetEntity, boolean isNew) {
          if (isNew) {
            mFinishedAssetQueue.add(assetEntity);
          } else {
            mExistingAssetQueue.add(assetEntity);
          }
          assetDownloadLoop();
        }
      });
    } else {
      mDatabase.assetDao().insertAssets(Arrays.asList(mFinishedAssetQueue.toArray(new AssetEntity[0])), mUpdateEntity);
      for (AssetEntity asset : mExistingAssetQueue) {
        mDatabase.assetDao().addExistingAssetToUpdate(mUpdateEntity, asset.url, asset.isLaunchAsset);
      }
      if (mErroredAssetQueue.size() == 0) {
        mDatabase.updateDao().markUpdateReady(mUpdateEntity);
      }
      finishWithSuccess();
    }
  }

  public interface AssetDownloadCallback {
    void onFailure(Exception e, AssetEntity assetEntity);
    void onSuccess(AssetEntity assetEntity, boolean isNew);
  }

  public void downloadAsset(final AssetEntity asset, final AssetDownloadCallback callback) {
    final String filename = UpdateUtils.sha1(asset.url.toString()) + "." + asset.type;
    File path = new File(mUpdatesDirectory, filename);

    if (path.exists()) {
      callback.onSuccess(asset, false);
    } else {
      Network.downloadFileToPath(Network.addHeadersToUrl(asset.url, mContext), path, new Network.FileDownloadCallback() {
        @Override
        public void onFailure(Exception e) {
          callback.onFailure(e, asset);
        }

        @Override
        public void onSuccess(File file, byte[] hash) {
          asset.downloadTime = new Date();
          asset.relativePath = filename;
          asset.hash = hash;
          callback.onSuccess(asset, true);
        }
      });
    }
  }
}
