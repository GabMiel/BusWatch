package com.example.buswatch

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment

class ParentMainActivity : AppCompatActivity() {

    private lateinit var ivNavAccount: ImageView
    private lateinit var tvNavAccount: TextView
    private lateinit var indicatorAccount: View

    private lateinit var ivNavHome: ImageView
    private lateinit var tvNavHome: TextView
    private lateinit var indicatorHome: View

    private lateinit var ivNavSettings: ImageView
    private lateinit var tvNavSettings: TextView
    private lateinit var indicatorSettings: View

    private val activeColor = "#FEBE1E".toColorInt()
    private val inactiveColor = "#A99E9E".toColorInt()
    
    private var currentTabTag: String = "HOME"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_main)

        ivNavAccount = findViewById(R.id.ivNavAccount)
        tvNavAccount = findViewById(R.id.tvNavAccount)
        indicatorAccount = findViewById(R.id.indicatorAccount)

        ivNavHome = findViewById(R.id.ivNavHome)
        tvNavHome = findViewById(R.id.tvNavHome)
        indicatorHome = findViewById(R.id.indicatorHome)

        ivNavSettings = findViewById(R.id.ivNavSettings)
        tvNavSettings = findViewById(R.id.tvNavSettings)
        indicatorSettings = findViewById(R.id.indicatorSettings)

        findViewById<View>(R.id.navAccount).setOnClickListener {
            switchToAccount()
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            switchToHome()
        }

        findViewById<View>(R.id.navSettings).setOnClickListener {
            switchToSettings()
        }

        // Handle system back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabTag != "HOME") {
                    switchToHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Restore state or set default
        if (savedInstanceState != null) {
            currentTabTag = savedInstanceState.getString("selected_tab", "HOME")
            updateNavUI(currentTabTag)
        } else {
            switchToHome()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_tab", currentTabTag)
    }

    fun switchToAccount() {
        replaceFragment(ParentDetailsFragment(), "ACCOUNT")
    }

    fun switchToHome() {
        replaceFragment(HomeFragment(), "HOME")
    }

    fun switchToSettings() {
        replaceFragment(SettingsFragment(), "SETTINGS")
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val currentFragment = supportFragmentManager.findFragmentByTag(tag)
        if (currentFragment?.isVisible == true) return

        currentTabTag = tag
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.parentFragmentContainer, fragment, tag)
            .commit()
        updateNavUI(tag)
    }

    private fun updateNavUI(tag: String) {
        // Reset all
        val inactiveList = ColorStateList.valueOf(inactiveColor)
        ivNavAccount.imageTintList = inactiveList
        tvNavAccount.setTextColor(inactiveColor)
        indicatorAccount.visibility = View.GONE

        ivNavHome.imageTintList = inactiveList
        tvNavHome.setTextColor(inactiveColor)
        indicatorHome.visibility = View.GONE

        ivNavSettings.imageTintList = inactiveList
        tvNavSettings.setTextColor(inactiveColor)
        indicatorSettings.visibility = View.GONE

        // Set active
        val activeList = ColorStateList.valueOf(activeColor)
        when (tag) {
            "ACCOUNT" -> {
                ivNavAccount.imageTintList = activeList
                tvNavAccount.setTextColor(activeColor)
                indicatorAccount.visibility = View.VISIBLE
            }
            "HOME" -> {
                ivNavHome.imageTintList = activeList
                tvNavHome.setTextColor(activeColor)
                indicatorHome.visibility = View.VISIBLE
            }
            "SETTINGS" -> {
                ivNavSettings.imageTintList = activeList
                tvNavSettings.setTextColor(activeColor)
                indicatorSettings.visibility = View.VISIBLE
            }
        }
    }
}
