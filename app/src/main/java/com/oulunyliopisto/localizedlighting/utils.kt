package com.oulunyliopisto.localizedlighting

import android.os.Handler


const val ip = "192.168.1.150"
//const val ip = "192.168.43.2"
const val port = "8000"
const val httpDomain = "http://$ip:$port"
const val wsDomain = "ws://$ip:$port"



// debounce runnable for mInterval (milliseconds)
class Debounce(private val mInterval: Long) {

    private val mHandler = Handler()

    fun attempt(runnable: Runnable) {
        mHandler.removeCallbacksAndMessages(null)
        mHandler.postDelayed(runnable, mInterval)
    }

}
