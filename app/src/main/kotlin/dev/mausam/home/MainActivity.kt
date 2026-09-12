package dev.mausam.home

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.mausam.home.ui.AppRoot
import dev.mausam.home.work.Notifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = System.currentTimeMillis()
        // Hold the splash for at most 800 ms in total; never on a stalled network.
        splash.setKeepOnScreenCondition { System.currentTimeMillis() - start < 350 }
        splash.setOnExitAnimationListener { provider ->
            // Logo scales to 0.8 and fades while the home hero scales up from 0.95.
            val icon = provider.iconView
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 0.8f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 0.8f),
                    ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f),
                )
                duration = 300L
                interpolator = AccelerateInterpolator()
                doOnEnd { provider.remove() }
                doOnCancel { provider.remove() }
            }.start()
        }
        handleOpenExtra(intent)
        val graph = MausamApp.graph(this)
        setContent { AppRoot(graph) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenExtra(intent)
    }

    private fun handleOpenExtra(intent: Intent?) {
        val open = intent?.getStringExtra(Notifier.EXTRA_OPEN) ?: return
        MausamApp.graph(this).pendingOpen = open
    }
}
