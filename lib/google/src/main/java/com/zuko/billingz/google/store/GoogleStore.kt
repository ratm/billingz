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
package com.zuko.billingz.google.store

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.zuko.billingz.core.LogUtilz
import com.zuko.billingz.core.store.Storez
import com.zuko.billingz.core.store.agent.Agentz
import com.zuko.billingz.core.store.client.Clientz
import com.zuko.billingz.core.store.inventory.Inventoryz
import com.zuko.billingz.core.store.model.Orderz
import com.zuko.billingz.core.store.model.Productz
import com.zuko.billingz.core.store.model.Receiptz
import com.zuko.billingz.core.store.sales.Salez
import com.zuko.billingz.core.store.security.Securityz
import com.zuko.billingz.google.store.client.GoogleClient
import com.zuko.billingz.google.store.inventory.GoogleInventory
import com.zuko.billingz.google.store.model.GoogleOrder
import com.zuko.billingz.google.store.sales.GoogleSales
import kotlinx.coroutines.MainScope

/**
 * @author rjsuzuki
 */
class GoogleStore internal constructor() : Storez {

    private val mainScope = MainScope()
    private val purchasesUpdatedListener: PurchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            (sales as GoogleSales).processUpdatedPurchases(
                billingResult,
                purchases
            )
        }
    private val connectionListener = object : Clientz.ConnectionListener {
        override fun connected() {
            sales.refreshQueries()
        }
    }
    private var context: Context? = null
    private val client: Clientz = GoogleClient(purchasesUpdatedListener)
    private val inventory: Inventoryz = GoogleInventory(client as GoogleClient)
    private val sales: Salez = GoogleSales(inventory as GoogleInventory, client as GoogleClient)

    init {
        LogUtilz.log.v(TAG, "instantiating...")
    }

    override fun init(context: Context?) {
        LogUtilz.log.v(TAG, "initializing...")
        this.context = context
    }

    override fun create() {
        LogUtilz.log.v(TAG, "creating...")
        if (!client.initialized()) {
            client.init(context, connectionListener)
            client.connect()
        }
    }

    override fun start() {
        LogUtilz.log.v(TAG, "starting...")
    }

    override fun resume() {
        LogUtilz.log.v(TAG, "resuming...")
        client.checkConnection()
        if (client.isReady())
            sales.refreshQueries()
        else if (!client.initialized()) {
            client.init(context, connectionListener)
            client.connect()
        }
    }

    override fun pause() {
        LogUtilz.log.v(TAG, "pausing...")
    }

    override fun stop() {
        LogUtilz.log.v(TAG, "stopping...")
    }

    override fun destroy() {
        LogUtilz.log.v(TAG, "destroying...")
        client.destroy()
        sales.destroy()
        inventory.destroy()
    }

    private val storeAgent = object : Agentz {

        override fun isInventoryReady(): LiveData<Boolean> {
            val data = MutableLiveData<Boolean>()
            data.value =
                client.isReady() && inventory.requestedProducts.value?.isNullOrEmpty() == false
            return data
        }

        override fun isBillingClientReady(): LiveData<Boolean> {
            LogUtilz.log.v(TAG, "isBillingClientReady: ${client.isReady()}")
            client.checkConnection()
            return client.isClientReady
        }

        override fun startOrder(
            activity: Activity?,
            productId: String?,
            options: Bundle?,
            listener: Salez.OrderValidatorListener?
        ): LiveData<Orderz> {
            LogUtilz.log.v(TAG, "Starting order: $productId")
            sales.orderValidatorListener = listener

            val data = MutableLiveData<Orderz>()
            val product = inventory.getProduct(productId)
            product?.let {
                sales.startOrder(activity, product, client)
                val order = GoogleOrder(
                    billingResult = null,
                    msg = "Processing..."
                )
                data.postValue(order)
            } ?: data.postValue(
                GoogleOrder(
                    billingResult = BillingResult.newBuilder()
                        .setDebugMessage("Product: $productId not found.")
                        .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                        .build(),
                    msg = "Product: $productId not found."
                )
            )
            return data
        }

        override fun queryOrders(): LiveData<Orderz> {
            LogUtilz.log.v(TAG, "queryOrders")
            return sales.queryOrders()
        }

        override fun queryReceipts(type: Productz.Type?): LiveData<ArrayMap<String, Receiptz>> {
            LogUtilz.log.v(TAG, "getReceipts: $type")
            if (client is GoogleClient) {
                sales.queryReceipts(type)
            }
            return sales.orderHistory
        }

        override fun updateInventory(products: Map<String, Productz.Type>): LiveData<Map<String, Productz>> {
            LogUtilz.log.v(TAG, "updateInventory: ${products.size}")
            return inventory.queryInventory(products = products)
        }

        override fun getProducts(
            type: Productz.Type?,
            promo: Productz.Promotion?
        ): Map<String, Productz> {
            LogUtilz.log.v(TAG, "getProducts: $type : $promo")
            return inventory.getProducts(type = type, promo = promo)
        }

        override fun getProduct(sku: String?): Productz? {
            LogUtilz.log.v(TAG, "getProduct: $sku")
            return inventory.getProduct(sku = sku)
        }
    }

    override fun getAgent(): Agentz {
        return storeAgent
    }

    /**
     * Builder Pattern - create an instance of GoogleStore
     */
    class Builder {
        private lateinit var instance: GoogleStore
        private lateinit var updaterListener: Salez.OrderUpdaterListener
        private lateinit var validatorListener: Salez.OrderValidatorListener
        private var obfuscatedAccountId: String? = null
        private var obfuscatedProfileId: String? = null
        private lateinit var products: ArrayMap<String, Productz.Type>

        /**
         * @param listener - Required to be set for proper functionality
         */
        fun setOrderUpdater(listener: Salez.OrderUpdaterListener): Builder {
            updaterListener = listener
            return this
        }

        /**
         * @param listener - Required to be set for proper functionality
         */
        fun setOrderValidator(listener: Salez.OrderValidatorListener): Builder {
            validatorListener = listener
            return this
        }

        /**
         * Google Play can use it to detect irregular activity, such as many devices
         * making purchases on the same account in a short period of time.
         * @param - unique identifier for the user's account (64 character limit)
         * The account ID is obfuscated via AES-256 encryption before being cached and used.
         */
        fun setAccountId(id: String?): Builder {
            if (!id.isNullOrBlank()) {
                obfuscatedAccountId = Securityz.encrypt(id).toString()
            }
            return this
        }

        /**
         * Some applications allow users to have multiple profiles within a single account.
         * Use this method to send the user's profile identifier to Google.
         * @param - unique identifier for the user's profile (64 character limit).
         * The profile ID is obfuscated via AES-256 encryption before being cached and used.
         */
        fun setProfileId(id: String?): Builder {
            if (!id.isNullOrBlank()) {
                obfuscatedProfileId = Securityz.encrypt(id).toString()
            }
            return this
        }

        fun setProducts(products: ArrayMap<String, Productz.Type>): Builder {
            this.products = products
            return this
        }

        /**
         * Return an instance of the GoogleStore
         */
        fun build(context: Context?): GoogleStore {
            instance = GoogleStore()
            instance.sales.apply {
                orderUpdaterListener = updaterListener
                orderValidatorListener = validatorListener
            }
            instance.sales.setObfuscatedIdentifiers(
                accountId = obfuscatedAccountId,
                profileId = obfuscatedProfileId
            )

            instance.init(context = context)
            instance.client.connect()
            if (::products.isInitialized) {
                instance.inventory.queryInventory(products = this.products)
            }
            return instance
        }

    }

    companion object {
        private const val TAG = "BillingzGoogleStore"
    }
}