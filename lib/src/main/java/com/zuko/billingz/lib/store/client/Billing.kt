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
package com.zuko.billingz.lib.store.client

import android.content.Context
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.zuko.billingz.lib.misc.CleanUpListener

/**
 * Blueprint of the core logic of the library.
 */
interface Billing : CleanUpListener {

    fun getBillingClient(): BillingClient?
    var isBillingClientReady: MutableLiveData<Boolean>

    /**
     * @return Boolean
     * Checks if the client has been initialized yet
     */
    @UiThread
    fun initialized(): Boolean

    /**
     * @return Boolean
     * Checks if the client properly connected to the android billing api,
     * so that requests to other methods will succeed.
     */
    @UiThread
    fun isReady(): Boolean

    /**
     * Initialize the Android Billing Library
     * INTERNAL USE ONLY
     * @param context
     * @param purchasesUpdatedListener
     * @param googlePlayConnectListener
     */
    @UiThread
    fun initClient(
        context: Context?,
        purchasesUpdatedListener: PurchasesUpdatedListener,
        googlePlayConnectListener: GooglePlayConnectListener
    )

    /**
     * Starts connection to GooglePlay
     * INTERNAL USE ONLY
     */
    @UiThread
    fun connect()

    /**
     * Stops connection to GooglePlay
     * INTERNAL USE ONLY
     */
    @UiThread
    fun disconnect()

    /**
     * Verifies connection to GooglePlay
     */
    fun checkConnection()

    /**
     * Callback used to respond to a successful connection.
     * INTERNAL USE ONLY
     */
    interface GooglePlayConnectListener {
        fun connected()
    }

    /**
     * Interface for reconnection logic to Google Play
     * INTERNAL USE ONLY
     */
    interface GooglePlayReconnectListener {

        /**
         *
         */
        @UiThread
        fun retry()

        /**
         *
         */
        fun cancel()
    }
}