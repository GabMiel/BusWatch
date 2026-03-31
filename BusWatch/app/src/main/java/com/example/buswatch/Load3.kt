package com.example.buswatch

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Load3 : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // After user interacts with the permission popup, navigate to Login
        navigateToLogin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load3)

        val allowButton = findViewById<Button>(R.id.btnLoad3Allow)
        val cancelButton = findViewById<Button>(R.id.btnLoad3Cancel)

        allowButton.setOnClickListener {
            // Request location permissions
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        cancelButton.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, Login::class.java)
        startActivity(intent)
        overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
        finish()
    }
}
