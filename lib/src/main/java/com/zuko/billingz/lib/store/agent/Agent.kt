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
package com.zuko.billingz.lib.store.agent

import android.app.Activity
import androidx.annotation.UiThread
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryResponseListener
import com.android.billingclient.api.SkuDetails
import com.zuko.billingz.lib.store.products.Product
import com.zuko.billingz.lib.store.sales.Order
import com.zuko.billingz.lib.store.sales.Sales

/**
 * Facade pattern - a simple interface for interacting with the main features of
 * the Google Billing Library
 * @author rjsuzuki
 */
interface Agent {

    /**
     * Observe changes to the BillingClient's connection to GooglePlay
     * from the UI thread (in an activity/fragment class).
     * @return [LiveData<Boolean>]
     */
    @UiThread
    fun isBillingClientReady(): LiveData<Boolean>

    /**
     * Initiate the purchase flow from the perspective of a user interaction.
     * e.g. a Customer opens your app and selects a product for purchase.
     * @return [LiveData<Order>]
     * @param activity - the currently active android Activity class
     * @param productId - the product id that can be found on the GooglePlayConsole
     * @param listener - @see [Sales.OrderValidatorListener] a callback function to enable customized
     * validation of a customer's purchase order - this allows you to do such things as verifying
     * a purchase with your backend before completing the purchase flow.
     */
    @UiThread
    fun startOrder(activity: Activity?, productId: String?, listener: Sales.OrderValidatorListener?): LiveData<Order>

    /**
     * Handle purchases still remaining from recent history. Observe the liveData object
     * that will emit [Order] objects that require your attention. These orders/purchases
     * could be purchases made on another device, or when the app is resuming, etc.
     * @return [LiveData<Order>]
     * @param listener - @see [Sales.OrderValidatorListener]
     */
    fun queriedOrders(listener: Sales.OrderValidatorListener): LiveData<Order>

    /**
     * Get all available products,
     * set productType to ALL to query all products.
     * @param skuList: MutableList<String>
     * @param productType: Product.ProductType
     * @return [LiveData<Map<String, SkuDetails>]
     */
    fun getAvailableProducts(
        skuList: MutableList<String>,
        productType: Product.ProductType
    ): LiveData<Map<String, SkuDetails>>

    /**
     * Get the details for a specified product.
     * @return [SkuDetails] - Android Billing Library object
     * @param productId - the product id that can be found on the GooglePlayConsole
     */
    fun getProductDetails(productId: String): SkuDetails?

    /**
     * Returns the most recent purchase made
     * by the user for each SKU, even if that purchase is expired, canceled, or consumed.
     * @param skuType - INAPP or SUB
     * @param listener - @see [PurchaseHistoryResponseListener]
     */
    fun getReceipts(skuType: String, listener: PurchaseHistoryResponseListener)

    /**
     * @return
     */
    fun getPendingOrders(): ArrayMap<String, Purchase>
}
