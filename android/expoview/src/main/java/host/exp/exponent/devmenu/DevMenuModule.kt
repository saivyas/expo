package host.exp.exponent.devmenu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.facebook.common.logging.FLog
import com.facebook.react.R
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.ReactConstants
import com.facebook.react.devsupport.DevInternalSettings
import com.facebook.react.devsupport.DevSupportManagerImpl
import com.facebook.react.devsupport.HMRClient
import host.exp.exponent.ReactNativeStaticHelpers
import host.exp.exponent.di.NativeModuleDepsProvider
import host.exp.exponent.experience.ExperienceActivity
import host.exp.exponent.experience.ReactNativeActivity
import host.exp.exponent.kernel.KernelConstants
import host.exp.exponent.utils.JSONBundleConverter
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class DevMenuModule(reactContext: ReactApplicationContext, val experienceProperties: Map<String, Any>, val manifest: JSONObject?) : ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

  @Inject
  internal var devMenuManager: DevMenuManager? = null

  init {
    NativeModuleDepsProvider.getInstance().inject(DevMenuModule::class.java, this)
    reactContext.addLifecycleEventListener(this)
  }

  //region publics

  override fun getName(): String = "ExpoDevMenu"

  /**
   * Returns manifestUrl of the experience which can be used as its ID.
   */
  fun getManifestUrl(): String {
    val manifestUrl = experienceProperties[KernelConstants.MANIFEST_URL_KEY] as String?
    return manifestUrl ?: ""
  }

  /**
   * Returns a [Bundle] containing initialProps that will be used to render the dev menu for related experience.
   */
  fun getInitialProps(): Bundle {
    val bundle = Bundle()
    val taskBundle = Bundle()

    taskBundle.putString("manifestUrl", getManifestUrl())
    taskBundle.putBundle("manifest", JSONBundleConverter.JSONToBundle(manifest))

    bundle.putBundle("task", taskBundle)
    bundle.putString("uuid", UUID.randomUUID().toString())

    return bundle
  }

  /**
   * Returns a [WritableMap] with all available dev menu options for related experience.
   */
  fun getMenuItems(): WritableMap {
    val devSupportManager = getDevSupportManager()
    val devSettings = devSupportManager?.devSettings

    val items = Arguments.createMap()
    val inspectorMap = Arguments.createMap()
    val debuggerMap = Arguments.createMap()
    val hmrMap = Arguments.createMap()

    if (devSettings != null) {
      inspectorMap.putString("label", "Toggle Element Inspector")
      inspectorMap.putBoolean("isEnabled", devSupportManager.devSupportEnabled)
      items.putMap("dev-inspector", inspectorMap)
    }

    if (devSettings != null && devSupportManager.devSupportEnabled) {
      debuggerMap.putString("label", if (devSettings.isRemoteJSDebugEnabled) "Stop Remote Debugging" else "Debug Remote JS")
      debuggerMap.putBoolean("isEnabled", true)
    } else {
      debuggerMap.putString("label", "Remote Debugger Unavailable")
      debuggerMap.putBoolean("isEnabled", false)
    }
    items.putMap("dev-remote-debug", debuggerMap)

    if (devSettings != null && devSupportManager.devSupportEnabled && devSettings is DevInternalSettings) {
      hmrMap.putString("label", if (devSettings.isHotModuleReplacementEnabled) "Disable Fast Refresh" else "Enable Fast Refresh")
      hmrMap.putBoolean("isEnabled", true)
    } else {
      hmrMap.putString("label", "Fast Refresh Unavailable")
      hmrMap.putBoolean("isEnabled", false)
      hmrMap.putString("detail", "Use the Reload button above to reload when in production mode. Switch back to development mode to use Fast Refresh.")
    }
    items.putMap("dev-hmr", hmrMap)

    if (devSettings != null && devSupportManager.devSupportEnabled) {
      val perfMap = Arguments.createMap()
      perfMap.putString("label", if (devSettings.isFpsDebugEnabled) "Hide Performance Monitor" else "Show Performance Monitor")
      perfMap.putBoolean("isEnabled", true)
      items.putMap("dev-perf-monitor", perfMap)
    }

    return items
  }

  /**
   * Handles selecting dev menu options returned by [getMenuItems].
   */
  fun selectItemWithKey(itemKey: String) {
    val devSupportManager = getDevSupportManager()
    val devSettings = devSupportManager?.devSettings as DevInternalSettings?

    if (devSupportManager == null || devSettings == null) {
      return
    }

    when (itemKey) {
      "dev-reload" -> {
        if (!devSettings.isJSDevModeEnabled && devSettings.isHotModuleReplacementEnabled) {
          Toast.makeText(reactApplicationContext, reactApplicationContext.getString(R.string.reactandroid_catalyst_hot_reloading_auto_disable), Toast.LENGTH_LONG).show()
          devSettings.isHotModuleReplacementEnabled = false
        }
        reloadExpoApp()
      }
      "dev-remote-debug" -> {
        devSettings.isRemoteJSDebugEnabled = !devSettings.isRemoteJSDebugEnabled
        devSupportManager.handleReloadJS()
      }
      "dev-hmr" -> {
        val nextEnabled = !devSettings.isHotModuleReplacementEnabled
        devSettings.isHotModuleReplacementEnabled = nextEnabled

        if (reactApplicationContext != null) {
          if (nextEnabled) {
            reactApplicationContext.getJSModule(HMRClient::class.java).enable()
          } else {
            reactApplicationContext.getJSModule(HMRClient::class.java).disable()
          }
        }
      }
      "dev-inspector" -> devSupportManager.toggleElementInspector()
      "dev-perf-monitor" -> {
        if (!devSettings.isFpsDebugEnabled) {
          // Request overlay permission if needed when "Show Perf Monitor" option is selected
          requestOverlaysPermission()
        }
        devSupportManager.setFpsDebugEnabled(!devSettings.isFpsDebugEnabled)
      }
    }
  }

  //endregion publics
  //region LifecycleEventListener

  override fun onHostResume() {
    val activity = currentActivity

    if (activity is ExperienceActivity) {
      devMenuManager?.registerDevMenuModuleForActivity(this, activity)
    }
  }

  override fun onHostPause() {}

  override fun onHostDestroy() {}

  //endregion LifecycleEventListener
  //region internals

  /**
   * Returns versioned instance of [DevSupportManagerImpl],
   * or null if no activity is currently attached to react context.
   */
  private fun getDevSupportManager(): DevSupportManagerImpl? {
    val activity = currentActivity as ReactNativeActivity?
    return activity?.devSupportManager?.get() as DevSupportManagerImpl?
  }

  /**
   * Reloads Expo app with the manifest, falls back to reloading just JS bundle if reloading manifest fails.
   */
  private fun reloadExpoApp() {
    val devSupportManager = getDevSupportManager()
    val devSettings = devSupportManager?.devSettings

    try {
      if (devSettings is DevInternalSettings) {
        ReactNativeStaticHelpers.reloadFromManifest(devSettings.exponentActivityId)
      }
    } catch (expoHandleErrorException: Exception) {
      expoHandleErrorException.printStackTrace()
      // reloadExpoApp replaces handleReloadJS in some places
      // where in Expo we would like to reload from manifest.
      // If so, if anything goes wrong here, we can fall back
      // to plain JS reload.
      devSupportManager?.handleReloadJS()
    }
  }

  /**
   * Requests for the permission that allows the app to draw overlays on other apps.
   * Such permission is required for example to enable performance monitor.
   */
  private fun requestOverlaysPermission() {
    val context = currentActivity

    if (context == null) {
      FLog.e(ReactConstants.TAG, "Unable to get reference to react activity")
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // Get permission to show debug overlay in dev builds.
      if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        FLog.w(
            ReactConstants.TAG,
            "Overlay permissions needs to be granted in order for react native apps to run in dev mode")
        if (intent.resolveActivity(context.packageManager) != null) {
          context.startActivity(intent)
        }
      }
    }
  }

  //endregion internals
}