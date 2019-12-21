package expo.modules.updates;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.unimodules.core.ExportedModule;
import org.unimodules.core.ModuleRegistry;
import org.unimodules.core.Promise;
import org.unimodules.core.interfaces.ExpoMethod;
import org.unimodules.core.interfaces.services.EventEmitter;

import expo.modules.updates.db.UpdatesDatabase;
import expo.modules.updates.db.entity.UpdateEntity;
import expo.modules.updates.loader.Manifest;
import expo.modules.updates.loader.RemoteLoader;

public class UpdatesModule extends ExportedModule {
  private static final String NAME = "ExpoUpdates";
  private static final String TAG = UpdatesModule.class.getSimpleName();

  private static final String UPDATES_EVENT_NAME = "Expo.nativeUpdatesEvent";
  private static final String UPDATE_DOWNLOAD_START_EVENT = "downloadStart";
  private static final String UPDATE_DOWNLOAD_PROGRESS_EVENT = "downloadProgress";
  private static final String UPDATE_DOWNLOAD_FINISHED_EVENT = "downloadFinished";
  private static final String UPDATE_NO_UPDATE_AVAILABLE_EVENT = "noUpdateAvailable";
  private static final String UPDATE_ERROR_EVENT = "error";

  private ModuleRegistry mModuleRegistry;
  private Context mContext;

  public UpdatesModule(Context context) {
    super(context);
    mContext = context;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void onCreate(ModuleRegistry moduleRegistry) {
    mModuleRegistry = moduleRegistry;
  }

  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put("localAssets", UpdatesController.getInstance().getLocalAssetFiles());

    return constants;
  }

  @ExpoMethod
  public void reload(final Promise promise) {
    if (UpdatesController.getInstance().reloadReactApplication()) {
      promise.resolve(null);
    } else {
      promise.reject(
          "ERR_UPDATES_RELOAD",
          "Could not reload application. Ensure you have passed an instance of ReactApplication into UpdatesController.initialize()."
      );
    }
  }

  @ExpoMethod
  public void checkForUpdateAsync(final Promise promise) {
    final UpdatesController controller = UpdatesController.getInstance();

    if (controller == null) {
      promise.reject(
          "ERR_UPDATES_CHECK",
          "The updates module controller has not been properly initialized. If you're in development mode, you cannot check for updates. Otherwise, make sure you have called UpdatesController.initialize()."
      );
      return;
    }

    UpdatesDatabase database = controller.getDatabase(); // TODO: use lock
    new RemoteLoader(mContext, database, controller.getUpdatesDirectory())
        .downloadManifest(
            controller.getManifestUrl(),
            new RemoteLoader.ManifestDownloadCallback() {
              @Override
              public void onFailure(String message, Exception e) {
                promise.reject("ERR_UPDATES_CHECK", message, e);
                Log.e(TAG, message, e);
              }

              @Override
              public void onSuccess(Manifest manifest) {
                UpdateEntity launchedUpdate = controller.getLaunchedUpdate();
                if (launchedUpdate == null) {
                  // this shouldn't ever happen, but if we don't have anything to compare
                  // the new manifest to, let the user know an update is available
                  promise.resolve(manifest.getRawManifestJson().toString());
                  return;
                }

                // compare launchedUpdate to newly downloaded manifest
                UpdateEntity newerUpdate = new SelectionPolicyNewest().selectUpdateToLaunch(
                    Arrays.asList(launchedUpdate, manifest.getUpdateEntity())
                );
                if (newerUpdate == launchedUpdate) {
                  promise.resolve(false);
                } else {
                  promise.resolve(manifest.getRawManifestJson());
                }
              }
            }
        );
  }

  @ExpoMethod
  public void fetchUpdateAsync(final Promise promise) {
    final UpdatesController controller = UpdatesController.getInstance();

    if (controller == null) {
      promise.reject(
          "ERR_UPDATES_FETCH",
          "The updates module controller has not been properly initialized. If you're in development mode, you cannot fetch updates. Otherwise, make sure you have called UpdatesController.initialize()."
      );
      return;
    }

    UpdatesDatabase database = controller.getDatabase(); // TODO: use lock
    new RemoteLoader(mContext, database, controller.getUpdatesDirectory())
        .start(
            controller.getManifestUrl(),
            new RemoteLoader.LoaderCallback() {
              @Override
              public void onFailure(Exception e) {
                promise.reject("ERR_UPDATES_FETCH", "Failed to download new update", e);
              }

              @Override
              public void onManifestDownloaded(Manifest manifest) {
                // TODO: maybe return a boolean?
                UpdateEntity launchedUpdate = controller.getLaunchedUpdate();
                if (launchedUpdate != null && launchedUpdate.id.equals(manifest.getUpdateEntity().id)) {
                  sendEvent(UPDATE_NO_UPDATE_AVAILABLE_EVENT);
                  promise.resolve(false);
                } else {
                  sendEvent(UPDATE_DOWNLOAD_START_EVENT);
                }
              }

              @Override
              public void onSuccess(UpdateEntity update) {
                // TODO: send manifest
                sendEvent(UPDATE_DOWNLOAD_FINISHED_EVENT);
                promise.resolve(update.id.toString());
              }
            }
        );
  }

  private void sendEvent(String eventName) {
    EventEmitter eventEmitter = mModuleRegistry.getModule(EventEmitter.class);
    if (eventEmitter != null) {
      Bundle eventBundle = new Bundle();
      eventBundle.putString("type", eventName);
      eventEmitter.emit(UPDATES_EVENT_NAME, eventBundle);
    } else {
      Log.e(TAG, "Could not emit " + eventName + " event, no event emitter present.");
    }
  }
}
