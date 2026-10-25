package com.andrerinas.openheadunit.utils

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog

object SystemUI {

    fun apply(window: Window, root: View, mode: Settings.FullscreenMode, onInsetsChanged: (() -> Unit)? = null) {
        // Always keep screen on for Headunit functionality
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle Display Cutout (Notch) for Android P+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)

        // Modern Edge-to-Edge for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Only force decor to NOT fit if we are in some kind of immersive mode.
            // If mode is NONE, we want the system to fit the window naturally.
            window.setDecorFitsSystemWindows(mode == Settings.FullscreenMode.NONE)
        }

        // Apply visibility using Compat API
        when (mode) {
            Settings.FullscreenMode.IMMERSIVE, Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH -> {
                controllerCompat.hide(WindowInsetsCompat.Type.systemBars())
                controllerCompat.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            Settings.FullscreenMode.STATUS_ONLY -> {
                controllerCompat.hide(WindowInsetsCompat.Type.statusBars())
                controllerCompat.show(WindowInsetsCompat.Type.navigationBars())
                controllerCompat.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            Settings.FullscreenMode.NONE -> {
                controllerCompat.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // Legacy Flags (Layout stability & Fallback for hiding bars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            @Suppress("DEPRECATION")
            // X管家 适配：NONE 模式（默认）不要 LAYOUT_STABLE —— 它会让内容
            // 顶到系统栏下面，车机上就变成"铺满整屏"盖住左侧气候栏。
            var flags = if (mode == Settings.FullscreenMode.NONE) 0 else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            
            when (mode) {
                Settings.FullscreenMode.IMMERSIVE, Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH -> {
                    flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or 
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or 
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    } else {
                        // Older than KitKat: use FLAG_FULLSCREEN for reliability
                        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        flags = flags or View.SYSTEM_UI_FLAG_LOW_PROFILE
                    }
                }
                Settings.FullscreenMode.STATUS_ONLY -> {
                    flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        flags = flags or View.SYSTEM_UI_FLAG_LOW_PROFILE
                    }
                }
                Settings.FullscreenMode.NONE -> {
                    // Reset flags for normal mode
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    }
                }
            }
            
            window.decorView.systemUiVisibility = flags
        }

        // Visual Polish: Background colors and Light/Dark icon appearance
        // xAuto（用户任务 2 + 夜间模式）：状态栏/导航栏不再写死 Color.BLACK，改用 X管家 token ——
        // 状态栏 = 页面顶部 xb_bg_top，导航栏 = 页面底部 xb_bg_bottom；token 会随 uiMode 换值
        // （日间浅蓝 / 夜间深靛蓝），图标的明暗由下方 isNightMode 动态决定。
        // Android 11 车机走 < 35 分支；API 35+ 系统强制 edge-to-edge，保留原逻辑（不写 bar 颜色）。
        val immersive = mode == Settings.FullscreenMode.IMMERSIVE ||
                mode == Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT < 35) {
                if (immersive) {
                    // 系统栏本来隐藏：透明即可，不画黑条；下滑临时唤出时叠在页面底色上，仍保持沉浸。
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                } else {
                    // NONE / STATUS_ONLY：与页面顶部/底部同色（日间浅蓝、夜间深靛蓝），衔接成一片。
                    window.statusBarColor = ContextCompat.getColor(root.context, R.color.xb_bg_top)
                    window.navigationBarColor = ContextCompat.getColor(root.context, R.color.xb_bg_bottom)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            if (mode == Settings.FullscreenMode.NONE || mode == Settings.FullscreenMode.STATUS_ONLY) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            }
        }
        // 浅色底 → 深色图标；夜间深色底 → 浅色图标。按当前 uiMode 动态判断，
        // 这样 `cmd uimode night yes/no` 切夜景时图标颜色与背景一致。
        val isNightMode = (root.context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        controllerCompat.isAppearanceLightStatusBars = !isNightMode
        controllerCompat.isAppearanceLightNavigationBars = !isNightMode

        val settings = Settings(root.context)

        // IMMEDIATE APPLICATION OF MANUAL INSETS
        val manualL = settings.insetLeft
        val manualT = settings.insetTop
        val manualR = settings.insetRight
        val manualB = settings.insetBottom

        root.setPadding(manualL, manualT, manualR, manualB)
        HeadUnitScreenConfig.updateInsets(manualL, manualT, manualR, manualB)

        // Android 4.x Legacy Fallback
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            root.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val rect = android.graphics.Rect()
                    window.decorView.getWindowVisibleDisplayFrame(rect)

                    @Suppress("DEPRECATION")
                    val display = window.windowManager.defaultDisplay
                    val size = android.graphics.Point()
                    display.getSize(size)

                    val insetT = rect.top
                    val insetB = size.y - rect.bottom
                    val insetL = rect.left
                    val insetR = size.x - rect.right

                    AppLog.d("[UI_DEBUG] Legacy SystemUI: Detected Insets L$insetL T$insetT R$insetR B$insetB")
                    HeadUnitScreenConfig.updateInsets(manualL + insetL, manualT + insetT, manualR + insetR, manualB + insetB)
                    onInsetsChanged?.invoke()
                    root.viewTreeObserver.removeGlobalOnLayoutListener(this)
                }
            })
        }

        // Set up listener for dynamic system bars (API 21+)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insetsCompat ->
            if (mode == Settings.FullscreenMode.NONE) {
                // In standard mode, let the system handle fitting and don't apply manual padding.
                // We still update HeadUnitScreenConfig with 0 insets because the window is already resized.
                HeadUnitScreenConfig.updateInsets(manualL, manualT, manualR, manualB)
                onInsetsChanged?.invoke()
                return@setOnApplyWindowInsetsListener insetsCompat
            }

            var typeMask = 0
            when (mode) {
                Settings.FullscreenMode.IMMERSIVE -> {
                    typeMask = 0 // Standard Immersive: Overlay everything (Notch included)
                }
                Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH -> {
                    if (Build.VERSION.SDK_INT >= 28) {
                        typeMask = WindowInsetsCompat.Type.displayCutout()
                    }
                }
                Settings.FullscreenMode.STATUS_ONLY -> {
                    typeMask = WindowInsetsCompat.Type.navigationBars()
                    if (Build.VERSION.SDK_INT >= 28) {
                        typeMask = typeMask or WindowInsetsCompat.Type.displayCutout()
                    }
                }
                else -> {
                    typeMask = WindowInsetsCompat.Type.systemBars()
                    if (Build.VERSION.SDK_INT >= 28) {
                        typeMask = typeMask or WindowInsetsCompat.Type.displayCutout()
                    }
                }
            }

            // Always pad for the soft keyboard so text fields are not covered and the
            // content shrinks while the IME is up (0 when the keyboard is hidden).
            typeMask = typeMask or WindowInsetsCompat.Type.ime()

            val bars = if (typeMask != 0) {
                insetsCompat.getInsets(typeMask)
            } else {
                androidx.core.graphics.Insets.NONE
            }

            v.setPadding(bars.left + manualL, bars.top + manualT, bars.right + manualR, bars.bottom + manualB)
            HeadUnitScreenConfig.updateInsets(bars.left + manualL, bars.top + manualT, bars.right + manualR, bars.bottom + manualB)

            onInsetsChanged?.invoke()
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(root)
        root.requestLayout()
    }
}