package expo.modules.updates.loader;

import android.net.Uri;
import android.util.Log;

import expo.modules.updates.db.entity.AssetEntity;
import expo.modules.updates.db.entity.UpdateEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Manifest {

  private static String TAG = Manifest.class.getSimpleName();

  private static String BUNDLE_FILENAME = "shell-app.bundle";

  private UUID mId;
  private Date mCommitTime;
  private String mBinaryVersions;
  private JSONObject mMetadata;
  private Uri mBundleUrl;
  private JSONArray mAssets;

  private Manifest(UUID id, Date commitTime, String binaryVersions, JSONObject metadata, Uri bundleUrl, JSONArray assets) {
    mId = id;
    mCommitTime = commitTime;
    mBinaryVersions = binaryVersions;
    mMetadata = metadata;
    mBundleUrl = bundleUrl;
    mAssets = assets;
  }

  public static Manifest fromBareManifestJson(JSONObject manifestJson) throws JSONException {
    UUID id = UUID.fromString(manifestJson.getString("id"));
    Date commitTime = new Date(manifestJson.getLong("commitTime"));
    String binaryVersions = manifestJson.getString("binaryVersions");
    JSONObject metadata = manifestJson.optJSONObject("metadata");
    Uri bundleUrl = Uri.parse(manifestJson.getString("bundleUrl"));
    JSONArray assets = manifestJson.optJSONArray("assets");

    return new Manifest(id, commitTime, binaryVersions, metadata, bundleUrl, assets);
  }

  public static Manifest fromManagedManifestJson(JSONObject manifestJson) throws JSONException {
    UUID id = UUID.fromString(manifestJson.getString("releaseId"));
    String commitTimeString = manifestJson.getString("commitTime");
    String sdkVersion = manifestJson.getString("sdkVersion");
    Uri bundleUrl = Uri.parse(manifestJson.getString("bundleUrl"));

    Date commitTime;
    try {
      DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      commitTime = formatter.parse(commitTimeString);
    } catch (ParseException e) {
      Log.e(TAG, "Could not parse commitTime", e);
      commitTime = new Date();
    }

    // TODO: look at bundledAssets field in manifest?

    return new Manifest(id, commitTime, sdkVersion, manifestJson, bundleUrl, null);
  }

  public UpdateEntity getUpdateEntity() {
    UpdateEntity updateEntity = new UpdateEntity(mId, mCommitTime, mBinaryVersions);
    if (mMetadata != null) {
      updateEntity.metadata = mMetadata;
    }

    return updateEntity;
  }

  public ConcurrentLinkedQueue<AssetEntity> getAssetEntityQueue() {
    ConcurrentLinkedQueue<AssetEntity> assetQueue = new ConcurrentLinkedQueue<>();

    AssetEntity bundleAssetEntity = new AssetEntity(mBundleUrl, "js");
    bundleAssetEntity.isLaunchAsset = true;
    bundleAssetEntity.assetsFilename = BUNDLE_FILENAME;
    assetQueue.add(bundleAssetEntity);

    if (mAssets != null && mAssets.length() > 0) {
      for (int i = 0; i < mAssets.length(); i++) {
        try {
          JSONObject assetObject = mAssets.getJSONObject(i);
          AssetEntity assetEntity = new AssetEntity(
                  Uri.parse(assetObject.getString("url")),
                  assetObject.getString("type")
          );
          assetEntity.assetsFilename = assetObject.optString("assetsFilename");
          assetQueue.add(assetEntity);
        } catch (JSONException e) {
          Log.e(TAG, "Could not read asset from manifest", e);
        }
      }
    }

    return assetQueue;
  }
}
