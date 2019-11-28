package com.oulunyliopisto.localizedlighting

import android.app.ActivityManager
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.os.IBinder
import android.widget.*

import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener

import java.util.concurrent.TimeUnit
import org.json.JSONObject


class MainActivity : AppCompatActivity(){

    private var lightingSeekBar: SeekBar? = null

    private var leave_button: Button? = null

    private var lightingLevel: Int? = null

    private var switchId: String? = null

    var myService: SwitchOccupiedService? = null

    var isBound = false

    val mDebounce = Debounce(50)

    val NORMAL_CLOSURE_STATUS: Int = 1000

    private var client: OkHttpClient? = null

    private var ws: WebSocket? = null

    private var serverClosedConnection = false


    private inner class SwitchWebSocketListener: WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            println("onOpen : $response")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("Receiving : $text")

            serverClosedConnection = true
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            println("WS closed $code $reason")

            killService()

            if(serverClosedConnection){
                val entryIntent = Intent(this@MainActivity, EntryActivity::class.java)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(entryIntent)
            }

            //super.onClosed(webSocket, code, reason)

        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            // when connection lost due to network failure, go to entry screen
            println("Connection failure $t $response")
            //super.onFailure(webSocket, t, response)
            killService()
                val entryIntent = Intent(this@MainActivity, EntryActivity::class.java)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(entryIntent)
            finish()

        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
            println("Closing : $code / $reason")

        }
    }


    private val myConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                        service: IBinder) {
            val binder = service as SwitchOccupiedService.MyLocalBinder
            myService = binder.getService()
            isBound = true

            //getBackgroundNotification(applicationContext, myService).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }

        override fun onServiceDisconnected(name: ComponentName) {

            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent: Intent = getIntent()

        switchId = intent.getStringExtra("switchId")

        val sharedPref = applicationContext.getSharedPreferences(
            applicationContext.getString(R.string.app_shared_preference_file_key), Context.MODE_PRIVATE
        )
        val token = sharedPref.getString(applicationContext.getString(R.string.saved_token_key), "")


        // http stuffs
        val queue = Volley.newRequestQueue(this)
        client = OkHttpClient.Builder()
            //.connectTimeout(20, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()

        val url = "$wsDomain/ws/websocket/$switchId/"


        val request = okhttp3.Request.Builder().url(url)
            .addHeader("Authorization","Token $token")
            .build()
        val listener = SwitchWebSocketListener()
        ws = client?.newWebSocket(request, listener)
        client?.dispatcher()?.executorService()?.shutdown()


        lightingSeekBar = findViewById(R.id.lighting_seekbar)
        leave_button = findViewById(R.id.leave_button)


        lightingSeekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                lightingLevel = i

                mDebounce.attempt(Runnable {
                    println("${SystemClock.uptimeMillis()}, $switchId")

                    // backend doesn't support setting level with websockets yet
                    //val msg = JSONObject()
                    //msg.put("level", lightingLevel.toString())
                    //println("${msg.toString()} sent")
                    //ws?.send(msg.toString())

                    val postAddress = "$httpDomain/localizedlighting/switches/$switchId/set_level/"
                    var postparams = JSONObject()
                    postparams.put("level", lightingLevel.toString())


                    val jsonObjReq = object : JsonObjectRequest(
                        Request.Method.POST,
                        postAddress, postparams,
                        Response.Listener {
                                response ->
                            println("response: $response")
                        },
                        Response.ErrorListener {
                                error ->
                            handleSetLevelErrors(error)

                        }
                    ){

                        @Throws(AuthFailureError::class)
                        override fun getHeaders(): Map<String, String> {
                            val headers = java.util.HashMap<String, String>()
                            headers["Authorization"] = "Token $token"
                            headers["Content-Type"] = "application/json"
                            return headers
                        }
                    }
                    queue.add(jsonObjReq)

                })
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        leave_button!!.setOnClickListener {

            ws?.close(NORMAL_CLOSURE_STATUS, "")
            val entryIntent = Intent(this@MainActivity, EntryActivity::class.java)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            entryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(entryIntent)
            finish()

        }

        val serviceClass = SwitchOccupiedService::class.java
        val serviceIntent = Intent(applicationContext, serviceClass)
        serviceIntent.putExtra(SERVICE_INTENT_SWITCH_ID, switchId)

        // If the service is not running then start it
        if (!isServiceRunning(serviceClass)) {
            println("Service started.")
            startService(serviceIntent)
            bindService(serviceIntent, myConnection, Context.BIND_AUTO_CREATE)
        } else {
            println("Service already running.")
            bindService(serviceIntent, myConnection, Context.BIND_AUTO_CREATE)
        }


    }

    override fun onResume() {
        super.onResume()
        val intent: Intent = getIntent()

        switchId = intent.getStringExtra("switchId")
    }

    override fun onDestroy() {
        ws?.close(NORMAL_CLOSURE_STATUS, "")
        println("Mainactivity onDestroy")

        super.onDestroy()
    }

    override fun onBackPressed() {
        // disable pressing back
        //    super.onBackPressed()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Loop through the running services
        //activityManager.getRunningServiceControlPanel()
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                // If the service is running then return true
                return true
            }
        }
        return false
    }


    private fun killService(){
        val serviceClass = SwitchOccupiedService::class.java
        val serviceIntent = Intent(applicationContext, serviceClass)
        try {
            unbindService(myConnection)
        } catch (e: IllegalArgumentException) {
            println("Error Unbinding Service.")
        }
        if (isServiceRunning(SwitchOccupiedService::class.java)) {
            stopService(serviceIntent)
        }

    }
    
    
    private fun handleSetLevelErrors(error: VolleyError){
        println("error: $error")
        println("error.networkResponse: ${error.networkResponse}")
        println("error.networkResponse.statusCode: ${error.networkResponse?.statusCode}")

        if (error.networkResponse?.statusCode == 409){
            println(409)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_dialog_too_many_users_at_a_time_title)
                .setMessage(R.string.error_dialog_too_many_users_at_a_time_message)
                .setPositiveButton(R.string.error_dialog_too_many_users_at_a_time_btn, null)
                .show()
        } else if (error.networkResponse?.statusCode == 429){
            println(429)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_dialog_throttle_limit_title)
                .setMessage(R.string.error_dialog_throttle_limit_message)
                .setPositiveButton(R.string.error_dialog_throttle_limit_btn, null)
                .show()
        }
    }

}

