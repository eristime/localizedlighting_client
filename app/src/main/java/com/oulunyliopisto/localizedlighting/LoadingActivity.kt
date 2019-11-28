package com.oulunyliopisto.localizedlighting


import androidx.appcompat.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.Toast
import com.android.volley.*

import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject



class LoadingActivity : AppCompatActivity(){


    private var nfcAdapter: NfcAdapter? = null

    private var spinner: ProgressBar? = null

    private var httpQueue: RequestQueue? = null

    private var switchId: String? = null

    private var onGoingServerConnectionInit: Boolean = false

    private var wifiDialog: AlertDialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        spinner = findViewById(R.id.progressBar)


        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this)?.let { it }


        httpQueue = Volley.newRequestQueue(this)

        wifiDialog = MaterialAlertDialogBuilder(this@LoadingActivity)
            .setTitle(R.string.error_dialog_no_network_title)
            .setMessage(R.string.error_dialog_no_network_message)
            .setCancelable(false)
            .setPositiveButton(R.string.error_dialog_no_network_btn
            ) { dialog, which ->
                val enableNFCIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                startActivityForResult(enableNFCIntent, 0)
                dialog.dismiss()
                Toast.makeText(
                    this,
                    R.string.error_dialog_no_network_message,
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()
    }


    override fun onNewIntent(intent: Intent) {
        // also reading NFC message from here in case this activity is already started in order
        // not to start another instance of this activity
        super.onNewIntent(intent)

        println("Loading activity onNewIntent")
        receiveMessageFromTag(intent)


    }

    override fun onResume() {
        super.onResume()

        // foreground dispatch should be enabled here, as onResume is the guaranteed place where app
        // is in the foreground
        // foreground dispatch enables invoking other NFC-intents when app is in foreground
        // for example when scanning another switch when already occupying one
        enableForegroundDispatch(this, this.nfcAdapter)
        receiveMessageFromTag(intent)
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch(this, this.nfcAdapter)
    }



    private fun receiveMessageFromTag(intent: Intent) {
        val action = intent.action
        println("loading activity intent action: $action")
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val parcelables = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            with(parcelables) {
                val inNdefMessage = this[0] as NdefMessage
                val inNdefRecords = inNdefMessage.records
                val ndefRecord_0 = inNdefRecords[0]

                val inMessage = String(ndefRecord_0.payload)

                switchId = inMessage.substring(3)  // remove "en"-prefix


                println("NFC read: $inMessage")
                //println("inNdefRecords: $inNdefRecords")

                // prevent multiple NFC-dispatches
                if (!onGoingServerConnectionInit){
                    initServerConnection(applicationContext)
                }
            }
        }
    }


    private fun enableForegroundDispatch(activity: AppCompatActivity, adapter: NfcAdapter?) {

        // here we are setting up receiving activity for a foreground dispatch
        // thus if activity is already started it will take precedence over any other activity or app
        // with the same intent filters

        val intent = Intent(activity.applicationContext, activity.javaClass)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(activity.applicationContext, 0, intent, 0)

        val filters = arrayOfNulls<IntentFilter>(1)
        val techList = arrayOf<Array<String>>()

        filters[0] = IntentFilter()
        with(filters[0]) {
            this?.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
            this?.addCategory(Intent.CATEGORY_DEFAULT)
            try {
                this?.addDataType(MIME_TEXT_PLAIN)
            } catch (ex: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Check your MIME type")
            }
        }

        adapter?.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    private fun disableForegroundDispatch(activity: AppCompatActivity, adapter: NfcAdapter?) {
        adapter?.disableForegroundDispatch(activity)
    }

    private fun initServerConnection(appContext: Context) {
        //TODO: escape from the callback hell
        println("initServerConnection")
        onGoingServerConnectionInit = true


        // ping server and if it's upp proceed
        val r = JsonObjectRequest(
            Request.Method.GET, "$httpDomain/authentication/ping/", null,
            Response.Listener { response ->
                println("Server up")
                authenticateClient(appContext)

            },
            Response.ErrorListener { error ->
                println("$error")
                println("${error.message}")
                handleNetworkTimeout(error)

            })
        println("DEFAULT_MAX_RETRIES${DefaultRetryPolicy.DEFAULT_MAX_RETRIES}")
        println("DEFAULT_TIMEOUT_MS${DefaultRetryPolicy.DEFAULT_TIMEOUT_MS}")
        println("DEFAULT_BACKOFF_MULT${DefaultRetryPolicy.DEFAULT_BACKOFF_MULT}")
        // make response from the server faster
        // backoffMultiplier 1.0 -> 0.0
        // initialTimeoutMs 2500ms -> 1000ms
        r.setRetryPolicy(
            DefaultRetryPolicy(
                1000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                0.00f
            ))
        httpQueue?.add(r)


    }

    private fun authenticateClient(appContext: Context){

        //TODO: make singleton
        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.app_shared_preference_file_key), Context.MODE_PRIVATE
        )

        // check if credentials or token
        val token = sharedPref.getString(appContext.getString(R.string.saved_token_key), "")
        val username = sharedPref.getString(appContext.getString(R.string.saved_username_key), "")
        val password = sharedPref.getString(appContext.getString(R.string.saved_password_key), "")
        println("localstorage token: $token")
        println("localstorage username: $username")
        println("localstorage password: $password")

        if (token.isEmpty() && (username.isEmpty() && password.isEmpty())) {
            // No previous credentials, create new user, login and proceed to the MainActivity
            createUser(appContext)

        } else if (token.isEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
            // No token, but credentials. Login and proceed to the MainActivity.
            login(username, password, appContext)

        } else if (token.isNotEmpty()) {
            // Try to see if token works, if not get new one
            checkIfTokenWorks(token)
        }

    }

    private fun createUser(appContext: Context){
        var postparams = JSONObject()
        postparams.put("key", clientToken)

        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.app_shared_preference_file_key), Context.MODE_PRIVATE
        )
        val r = JsonObjectRequest(
            Request.Method.POST, "$httpDomain/authentication/create_user/", postparams,
            Response.Listener { response ->
                println("$response")

                val rUsername = response.getString("username")
                val rPassword = response.getString("password")

                with (sharedPref.edit()) {
                    putString(getString(R.string.saved_username_key), rUsername)
                    putString(getString(R.string.saved_password_key), rPassword)
                    commit()
                }

                login(rUsername, rPassword, appContext)
            },
            Response.ErrorListener { error ->
                println("$error")
                println("${error.message}")
                handleNetworkTimeout(error)

            })
        httpQueue?.add(r)
    }

    private fun login(username: String?, password:String?, appContext: Context){
        val postparams = JSONObject()
        postparams.put("username", username)
        postparams.put("password", password)

        val sharedPref = appContext.getSharedPreferences(
            appContext.getString(R.string.app_shared_preference_file_key), Context.MODE_PRIVATE
        )

        val r = JsonObjectRequest(
            Request.Method.POST, "$httpDomain/authentication/login/", postparams,
            Response.Listener { response ->

                // functioning token, go to the next activity
                println("$response")
                val rToken = response.getString("token")

                with (sharedPref.edit()) {
                    putString(getString(R.string.saved_token_key), rToken)
                    commit()
                }

                // we got the token, let's go to the mainactivity
                println("$response")
                val entryIntent = Intent(applicationContext, MainActivity::class.java).putExtra("switchId", switchId )
                startActivity(entryIntent)

                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(entryIntent)
                finish()
            },
            Response.ErrorListener { error ->
                println("$error")
                println("${error.message}")
                handleNetworkTimeout(error)
            })
        httpQueue?.add(r)

    }

    private fun checkIfTokenWorks(token: String?){

        val r = object : JsonArrayRequest(
            Request.Method.GET, "$httpDomain/localizedlighting/switches/?format=json", null,
            Response.Listener { response ->

                // we got a functioning token, go to the next activity
                println("$response")
                val entryIntent = Intent(applicationContext, MainActivity::class.java).putExtra("switchId", switchId )

                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(entryIntent)
                finish()
            },
            Response.ErrorListener { error ->
                println("$error")
                println("${error.message}")
                handleNetworkTimeout(error)

            }) {

            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val headers = java.util.HashMap<String, String>()
                headers["Authorization"] = "Token $token"
                headers["Content-Type"] = "application/json"
                return headers
            }
        }
        println("r.headers${r.headers}")
        httpQueue?.add(r)

    }
    /**
     * If [error] is Timeout error, show Alert Dialog and transfer user to EntryActivity when clicking ok.
     */
    private fun handleNetworkTimeout(error: VolleyError){
        println("handleNetworkTimeout: $error")
        if (error::class == TimeoutError()::class || error::class == NoConnectionError()::class){
            println("Timeout or NoConnectionError")
            wifiDialog?.show()
            onGoingServerConnectionInit = false

        }
    }
}


