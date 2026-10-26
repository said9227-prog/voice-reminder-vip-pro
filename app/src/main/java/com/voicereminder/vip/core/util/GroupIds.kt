package com.voicereminder.vip.core.util

import java.util.concurrent.atomic.AtomicLong

object GroupIds {
    private val last = AtomicLong(System.currentTimeMillis())

    fun next(): Long = last.incrementAndGet()
}
