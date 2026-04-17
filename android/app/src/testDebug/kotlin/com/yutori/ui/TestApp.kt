package com.yutori.ui

import android.app.Application

/**
 * Stand-in Application used by Robolectric-backed Compose tests. We
 * can't use [com.yutori.YutoriApp] because its onCreate()
 * touches the Room database and enqueues WorkManager jobs, which
 * require more infra than these pure-render regression tests need.
 */
class TestApp : Application()
