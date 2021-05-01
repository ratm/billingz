/*
 * Copyright 2021 rjsuzuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.zuko.billingz.lib.store

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent

/**
 * Implement androidx.lifecycle events
 */
interface ManagerLifecycle {
    fun init(context: Context?)

    /**
     * Initiate logic dependent on Android's onCreate() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun create()

    /**
     * Initiate logic dependent on Android's onStart() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start()

    /**
     * Initiate logic dependent on Android's onResume() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume()

    /**
     * Initiate logic dependent on Android's onPause() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pause()

    /**
     * Initiate logic dependent on Android's onStop() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop()

    /**
     * Initiate logic dependent on Android's onDestroy() Lifecycle event.
     * If you added the Manager class as a lifecycleObserver, you do
     * not need to add this class manually in your activity/fragment.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy()
}
