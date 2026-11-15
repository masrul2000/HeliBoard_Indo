// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Transparent activity: a keyboard cannot ask for runtime permissions by itself. */
class VoicePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VoiceInputController.hasMicPermission(this) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finish()
            return
        }
        if (savedInstanceState == null)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mikrofon diizinkan. Ketuk tombol mic lagi untuk bicara.", Toast.LENGTH_LONG).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                // permanently denied: open app settings
                Toast.makeText(this, "Izinkan Mikrofon di pengaturan aplikasi agar ketik suara bisa dipakai.", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { }
            } else {
                Toast.makeText(this, "Ketik suara butuh izin mikrofon.", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 4242

        fun start(context: Context) {
            try {
                context.startActivity(Intent(context, VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            } catch (_: Exception) {
                Toast.makeText(context, "Izinkan Mikrofon untuk HeliBoard Indo di pengaturan aplikasi.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
