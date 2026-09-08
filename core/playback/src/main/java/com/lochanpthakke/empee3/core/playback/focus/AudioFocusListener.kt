package com.lochanpthakke.empee3.core.playback.focus

interface AudioFocusListener {
    fun onFocusLost()

    fun onFocusLostTransient()

    fun onFocusLostTransientCanDuck()

    fun onFocusGained()
}
