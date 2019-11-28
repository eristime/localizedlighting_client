package com.oulunyliopisto.localizedlighting

import android.content.DialogInterface
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*


class EntryActivity : AppCompatActivity() {


    private var nfcAdapter: NfcAdapter? = null

    private var nfcDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry)

        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this)?.let { it }


        // creating the dialog only once prevents launching multiple dialogs when toggling onResume multiple times
        nfcDialog = MaterialAlertDialogBuilder(this@EntryActivity)
            .setTitle(R.string.error_dialog_no_NFC_title)
            .setMessage(R.string.error_dialog_no_NFC_message)
            .setCancelable(false)

            .setPositiveButton(R.string.error_dialog_no_NFC_btn
            ) { dialog, which ->
                dialog.dismiss()
                val enableNFCIntent = Intent(Settings.ACTION_NFC_SETTINGS)
                startActivityForResult(enableNFCIntent, 0)
                Toast.makeText(
                    this,
                    R.string.error_dialog_no_NFC_message,
                    Toast.LENGTH_LONG
                ).show()

            }
            .create()

    }


    override fun onResume() {
        super.onResume()

        // if back button pressed instantly when got back, the dialog is not yet dismissed
        Handler().postDelayed({
            toggleNFC()
            print("handler")
        }, 1000)

    }

    /*
    *
    * Toggles NFC alert dialog. Dialog make Cannot be dismissed.
    * */
    private fun toggleNFC(){


        // direct user to settings if NFC not enabled
        if (!nfcAdapter!!.isEnabled) {
            nfcDialog?.show()
        }
    }
}
