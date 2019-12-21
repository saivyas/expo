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
import java.util.ArrayList;
import java.util.Date;

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
  private int mAssetTotal = 0;
  private ArrayList<AssetEntity> mErroredAssetList = new ArrayList<>();
  private ArrayList<AssetEntity> mExistingAssetList = new ArrayList<>();
  private ArrayList<AssetEntity> mFinishedAssetList = new ArrayList<>();

  public interface LoaderCallback {
    void onFailure(Exception e);
    void onManifestDownloaded(Manifest manifest);
    void onSuccess(UpdateEntity update);
  }

  public RemoteLoader(Context context, UpdatesDatabase database, File updatesDirectory) {
    mContext = context;
    mDatabase = database;
    mUpdatesDirectory = updatesDirectory;
  }

  // lifecycle methods for class

  public void start(Uri url, LoaderCallback callback) {
    if (mCallback != null) {
      callback.onFailure(new Exception("RemoteLoader has already started. Create a new instance in order to load multiple URLs in parallel."));
      return;
    }

    mCallback = callback;

    downloadManifest(url, new ManifestDownloadCallback() {
      @Override
      public void onFailure(String message, Exception e) {
        finishWithError(message, e);
      }

      @Override
      public void onSuccess(Manifest manifest) {
        mCallback.onManifestDownloaded(manifest);
        processManifest(manifest);
      }
    });
  }

  private void reset() {
    mUpdateEntity = null;
    mCallback = null;
    mAssetTotal = 0;
    mErroredAssetList = new ArrayList<>();
    mExistingAssetList = new ArrayList<>();
    mFinishedAssetList = new ArrayList<>();
  }

  private void finishWithSuccess() {
    if (mCallback == null) {
      Log.e(TAG, "RemoteLoader tried to finish but it already finished or was never initialized.");
      return;
    }

    mCallback.onSuccess(mUpdateEntity);
    reset();
  }

  private void finishWithError(String message, Exception e) {
    Log.e(TAG, message, e);

    if (mCallback == null) {
      Log.e(TAG, "RemoteLoader tried to finish but it already finished or was never initialized.");
      return;
    }

    mCallback.onFailure(e);
    reset();
  }

  // public helper methods and interfaces for downloading individual files

  public interface ManifestDownloadCallback {
    void onFailure(String message, Exception e);
    void onSuccess(Manifest manifest);
  }

  public interface AssetDownloadCallback {
    void onFailure(Exception e, AssetEntity assetEntity);
    void onSuccess(AssetEntity assetEntity, boolean isNew);
  }

  public void downloadManifest(final Uri url, final ManifestDownloadCallback callback) {
    Network.downloadData(Network.addHeadersToManifestUrl(url, mContext), new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        callback.onFailure("Failed to download manifest from uri: " + url, e);
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          callback.onFailure("Failed to download manifest from uri: " + url, new Exception(response.body().string()));
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
                        callback.onFailure("Could not validate signed manifest", e);
                      }

                      @Override
                      public void onCompleted(boolean isValid) {
                        if (isValid) {
                          try {
                            Manifest manifest = Manifest.fromManagedManifestJson(new JSONObject(innerManifestString));
                            callback.onSuccess(manifest);
                          } catch (JSONException e) {
                            callback.onFailure("Failed to parse manifest data", e);
                          }
                        } else {
                          callback.onFailure("Manifest signature is invalid; aborting", new Exception("Manifest signature is invalid"));
                        }
                      }
                    }
            );
          } else {
            Manifest manifest = Manifest.fromManagedManifestJson(manifestJson);
            callback.onSuccess(manifest);
          }
        } catch (Exception e) {
          callback.onFailure("Failed to parse manifest data", e);
        }
      }
    });
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

  // private helper methods

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
        downloadAllAssets(manifest.getAssetEntityList());
      }
    } catch (Exception e) {
      finishWithError("Failed to parse manifest data", e);
    }
  }

  private void downloadAllAssets(ArrayList<AssetEntity> assetList) {
    mAssetTotal = assetList.size();
    for (AssetEntity assetEntity : assetList) {
      downloadAsset(assetEntity, new AssetDownloadCallback() {
        @Override
        public void onFailure(Exception e, AssetEntity assetEntity) {
          Log.e(TAG, "Failed to download asset from " + assetEntity.url, e);
          handleAssetDownloadCompleted(assetEntity, false, false);
        }

        @Override
        public void onSuccess(AssetEntity assetEntity, boolean isNew) {
          handleAssetDownloadCompleted(assetEntity, true, isNew);
        }
      });
    }
  }

  private synchronized void handleAssetDownloadCompleted(AssetEntity assetEntity, boolean success, boolean isNew) {
    if (success) {
      if (isNew) {
        mFinishedAssetList.add(assetEntity);
      } else {
        mExistingAssetList.add(assetEntity);
      }
    } else {
      mErroredAssetList.add(assetEntity);
    }

    if (mFinishedAssetList.size() + mErroredAssetList.size() == mAssetTotal) {
      mDatabase.assetDao().insertAssets(mFinishedAssetList, mUpdateEntity);
      for (AssetEntity asset : mExistingAssetList) {
        mDatabase.assetDao().addExistingAssetToUpdate(mUpdateEntity, asset.url, asset.isLaunchAsset);
      }
      if (mErroredAssetList.size() == 0) {
        mDatabase.updateDao().markUpdateReady(mUpdateEntity);
      }
      finishWithSuccess();
    }
  }
}
