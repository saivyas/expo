package host.exp.exponent.devmenu

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.ReactRootView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.ShakeDetector
import host.exp.exponent.di.NativeModuleDepsProvider
import host.exp.exponent.experience.ExperienceActivity
import host.exp.exponent.kernel.Kernel
import versioned.host.exp.exponent.ReactUnthemedRootView
import java.util.*
import javax.inject.Inject

private const val DEV_MENU_JS_MODULE_NAME = "HomeMenu"
private const val DEV_MENU_ANIMATION_DELAY = 100L
private const val DEV_MENU_ANIMATION_DURATION = 200L
private const val DEV_MENU_ANIMATION_INITIAL_ALPHA = 0.0f
private const val DEV_MENU_ANIMATION_INITIAL_SCALE = 1.1f
private const val DEV_MENU_ANIMATION_TARGET_ALPHA = 1.0f
private const val DEV_MENU_ANIMATION_TARGET_SCALE = 1.0f

/**
 * DevMenuManager is like a singleton that manages the dev menu in the whole application
 * and delegates calls from [ExponentKernelModule] to the specific [DevMenuModule]
 * that is linked with a react context for which the dev menu is going to be rendered.
 * Its instance can be injected as a dependency of other classes by [NativeModuleDepsProvider]
 */
class DevMenuManager {
  private var shakeDetector: ShakeDetector? = null
  private var reactRootView: ReactRootView? = null
  private val devMenuModulesRegistry = WeakHashMap<ExperienceActivity, DevMenuModule>()

  @Inject
  internal val kernel: Kernel? = null

  init {
    NativeModuleDepsProvider.getInstance().inject(DevMenuManager::class.java, this)
  }

  //region publics

  /**
   * Starts [ShakeDetector] if it's not running yet.
   */
  fun maybeStartDetectingShakes(context: Context) {
    if (shakeDetector != null) {
      return
    }
    shakeDetector = ShakeDetector { this.onShakeGesture() }
    shakeDetector?.start(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
  }

  /**
   * Links given [DevMenuModule] with given [ExperienceActivity]. [DevMenuManager] needs to know this to
   * get appropriate data or pass requests down to the correct [DevMenuModule] that handles
   * all these stuff for a specific experience (DevMenuManager only delegates those calls).
   */
  fun registerDevMenuModuleForActivity(devMenuModule: DevMenuModule, activity: ExperienceActivity) {
    // Start shake detector once the first DevMenuModule registers in the manager.
    maybeStartDetectingShakes(activity.applicationContext)
    devMenuModulesRegistry[activity] = devMenuModule
  }

  /**
   * Shows dev menu in given experience activity. Ensures it happens on the UI thread.
   */
  fun showInActivity(activity: ExperienceActivity) {
    UiThreadUtil.runOnUiThread {
      val devMenuModule = devMenuModulesRegistry[activity] ?: return@runOnUiThread
      val devMenuView = prepareRootView(devMenuModule.getInitialProps())

      activity.addView(devMenuView)
      kernel?.reactInstanceManager?.onHostResume(activity)

      devMenuView.animate().apply {
        startDelay = DEV_MENU_ANIMATION_DELAY
        duration = DEV_MENU_ANIMATION_DURATION

        alpha(DEV_MENU_ANIMATION_TARGET_ALPHA)
        scaleX(DEV_MENU_ANIMATION_TARGET_SCALE)
        scaleY(DEV_MENU_ANIMATION_TARGET_SCALE)
      }
    }
  }

  /**
   * Hides dev menu in given experience activity. Ensures it happens on the UI thread.
   */
  fun hideInActivity(activity: ExperienceActivity) {
    UiThreadUtil.runOnUiThread {
      reactRootView?.let {
        it.animate().apply {
          duration = DEV_MENU_ANIMATION_DURATION

          alpha(DEV_MENU_ANIMATION_INITIAL_ALPHA)
          withEndAction {
            val parentView = it.parent as FrameLayout?
            it.visibility = View.GONE
            parentView?.removeView(it)
            kernel?.reactInstanceManager?.onHostPause(activity)
          }
        }
      }
    }
  }

  /**
   * Hides dev menu in the currently shown experience activity.
   * Does nothing if the current activity is not of type [ExperienceActivity].
   */
  fun hideInCurrentActivity() {
    val currentActivity = ExperienceActivity.getCurrentActivity()

    if (currentActivity != null) {
      hideInActivity(currentActivity)
    }
  }

  /**
   * Toggles dev menu visibility in given experience activity.
   */
  fun toggleInActivity(activity: ExperienceActivity) {
    if (isDevMenuVisible() && activity.hasView(reactRootView)) {
      hideInActivity(activity)
    } else {
      showInActivity(activity)
    }
  }

  /**
   * Gets a map of dev menu options available in the currently shown [ExperienceActivity].
   */
  fun getMenuItems(): WritableMap {
    val devMenuModule = getCurrentDevMenuModule()
    return devMenuModule?.getMenuItems() ?: Arguments.createMap()
  }

  /**
   * Function called every time the dev menu option is selected. It passes this request down
   * to the specific [DevMenuModule] that is linked with the currently shown [ExperienceActivity].
   */
  fun selectItemWithKey(itemKey: String) {
    getCurrentDevMenuModule()?.selectItemWithKey(itemKey)
  }

  /**
   * Checks whether the dev menu is shown over given experience activity.
   */
  fun isShownInActivity(activity: ExperienceActivity): Boolean {
    return reactRootView != null && activity.hasView(reactRootView)
  }

  /**
   * In case the user switches from [HomeActivity] to [ExperienceActivity] which has a visible dev menu,
   * we need to call onHostResume on the kernel's react instance manager to change its current activity.
   */
  fun maybeResumeHostWithActivity(activity: ExperienceActivity) {
    if (isShownInActivity(activity)) {
      kernel?.reactInstanceManager?.onHostResume(activity)
    }
  }

  //endregion publics
  //region internals

  /**
   * If this is the first time when we're going to show the dev menu, it creates a new react root view
   * that will render the other endpoint of home app whose name is described by [DEV_MENU_JS_MODULE_NAME] constant.
   * Also sets initialProps, layout settings and initial animation values.
   */
  private fun prepareRootView(initialProps: Bundle): ReactRootView {
    if (reactRootView == null) {
      reactRootView = ReactUnthemedRootView(kernel?.activityContext)
      reactRootView?.startReactApplication(kernel?.reactInstanceManager, DEV_MENU_JS_MODULE_NAME, initialProps)
    } else {
      reactRootView?.appProperties = initialProps
    }

    val rootView = reactRootView!!

    rootView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    rootView.visibility = View.VISIBLE
    rootView.alpha = DEV_MENU_ANIMATION_INITIAL_ALPHA
    rootView.scaleX = DEV_MENU_ANIMATION_INITIAL_SCALE
    rootView.scaleY = DEV_MENU_ANIMATION_INITIAL_SCALE

    return rootView
  }

  /**
   * Returns [DevMenuModule] instance linked to the current [ExperienceActivity], or null if the current
   * activity is not of [ExperienceActivity] type or there is no module registered for that activity.
   */
  private fun getCurrentDevMenuModule(): DevMenuModule? {
    val currentActivity = getCurrentExperienceActivity()
    return if (currentActivity != null) devMenuModulesRegistry[currentActivity] else null
  }

  /**
   * Returns current activity if it's of type [ExperienceActivity], or null otherwise.
   */
  private fun getCurrentExperienceActivity(): ExperienceActivity? {
    return ExperienceActivity.getCurrentActivity()
  }

  /**
   * Checks whether the dev menu is visible anywhere.
   */
  private fun isDevMenuVisible(): Boolean {
    return reactRootView?.parent != null
  }

  /**
   * Handles shake gesture which simply toggles the dev menu.
   */
  private fun onShakeGesture() {
    val currentActivity = ExperienceActivity.getCurrentActivity()

    if (currentActivity != null) {
      toggleInActivity(currentActivity)
    }
  }

  //endregion internals
}