package com.vaultt

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultTApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureComponentsEnabled()
    }

    private fun ensureComponentsEnabled() {
        try {
            val pm = packageManager
            // Ensure MainActivity is ALWAYS enabled so Android never rejects launches
            val mainActivity = ComponentName(this, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Ensure DefaultAlias is ALWAYS enabled so existing home screen shortcuts never break
            val defaultAlias = ComponentName(this, "com.vaultt.DefaultAlias")
            pm.setComponentEnabledSetting(
                defaultAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
