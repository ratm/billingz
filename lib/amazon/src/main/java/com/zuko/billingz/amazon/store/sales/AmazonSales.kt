/*
 *
 *  * Copyright 2021 rjsuzuki
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */
package com.zuko.billingz.amazon.store.sales

import android.app.Activity
import android.os.Bundle
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.RequestId
import com.zuko.billingz.amazon.store.inventory.AmazonInventory
import com.zuko.billingz.amazon.store.model.AmazonOrder
import com.zuko.billingz.amazon.store.model.AmazonOrderHistory
import com.zuko.billingz.amazon.store.model.AmazonOrdersHistoryQuery
import com.zuko.billingz.amazon.store.model.AmazonOrdersQuery
import com.zuko.billingz.amazon.store.model.AmazonReceipt
import com.zuko.billingz.core.LogUtilz
import com.zuko.billingz.core.misc.BillingzDispatcher
import com.zuko.billingz.core.misc.Dispatcherz
import com.zuko.billingz.core.store.client.Clientz
import com.zuko.billingz.core.store.model.OrderHistoryz
import com.zuko.billingz.core.store.model.Orderz
import com.zuko.billingz.core.store.model.Productz
import com.zuko.billingz.core.store.model.QueryResult
import com.zuko.billingz.core.store.sales.Salez
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [IAP Docs](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html#responsereceiver)
 */
class AmazonSales(
    private val inventory: AmazonInventory,
    private val dispatcher: Dispatcherz = BillingzDispatcher()
) : AmazonSalez {

    private val mainScope = MainScope()
    override val currentOrder: MutableLiveData<Orderz> = MutableLiveData<Orderz>()
    private var currentOrderId: RequestId? = null
    override var currentReceipt = MutableLiveData<AmazonReceipt>()

    private var currentOrdersQueryId: RequestId? = null
    private val queriedOrders: ArrayMap<String, AmazonOrder> by lazy { ArrayMap() }
    private var queriedOrdersLiveData: MutableLiveData<AmazonOrder?> = MutableLiveData()
    private val queriedOrdersStateFlow: MutableStateFlow<AmazonOrder?> by lazy { MutableStateFlow(null) }
    private val queriedOrdersState: StateFlow<AmazonOrder?> by lazy { queriedOrdersStateFlow.asStateFlow() }

    private var currentHistoryQueryId: RequestId? = null
    private val orderHistory: ArrayMap<String, AmazonReceipt> by lazy { ArrayMap() }
    override var orderHistoryLiveData: MutableLiveData<AmazonOrderHistory> = MutableLiveData()
    override val orderHistoryStateFlow: MutableStateFlow<AmazonOrderHistory?> by lazy { MutableStateFlow(null) }
    override val orderHistoryState: StateFlow<AmazonOrderHistory?> by lazy { orderHistoryStateFlow.asStateFlow() }

    private var isAlreadyQueried = false // prevents redundant queries
    override var orderUpdaterListener: Salez.OrderUpdaterListener? = null
    override var orderValidatorListener: Salez.OrderValidatorListener? = null

    // verify amazon receipt via developer's implementation
    private val validatorCallback: Salez.ValidatorCallback = object : Salez.ValidatorCallback {
        override fun validated(order: Orderz) {
            LogUtilz.log.d(TAG, "validated order: ${order.orderId}")
            processOrder(order)
        }

        override fun invalidated(order: Orderz) {
            LogUtilz.log.d(TAG, "invalidated order: ${order.orderId}")
            cancelOrder(order)
        }
    }

    // step 1
    override fun startOrder(
        activity: Activity?,
        product: Productz,
        client: Clientz,
        options: Bundle?
    ) {
        if (isProductValid(product)) {
            currentOrderId = PurchasingService.purchase(product.sku)
        } else {
            LogUtilz.log.e(TAG, "Order cannot start with invalid sku: ${product.sku}")
            val order = AmazonOrder(
                resultMessage = "Order cannot start with invalid sku: ${product.sku}",
                result = Orderz.Result.INVALID_PRODUCT,
                null,
                null,
                null,
                null,
                null
            )
            failedOrder(order)
        }
    }

    private fun isProductValid(product: Productz): Boolean {
        if (inventory.unavailableSkus?.contains(product.sku) == true) {
            return false
        }
        // TODO - add pre-purchase validation checks here
        return true
    }

    private fun convertPurchaseStatus(status: PurchaseResponse.RequestStatus): Orderz.Result {
        return when (status) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> Orderz.Result.SUCCESS
            PurchaseResponse.RequestStatus.FAILED -> Orderz.Result.ERROR
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> Orderz.Result.PRODUCT_ALREADY_OWNED
            PurchaseResponse.RequestStatus.INVALID_SKU -> Orderz.Result.INVALID_PRODUCT
            PurchaseResponse.RequestStatus.NOT_SUPPORTED -> Orderz.Result.NOT_SUPPORTED
        }
    }

    override fun processPurchase(response: PurchaseResponse?) {
        response ?: return
        val order = AmazonOrder(
            resultMessage = response.requestStatus.name,
            result = convertPurchaseStatus(response.requestStatus),
            requestStatus = response.requestStatus.name,
            requestId = response.requestId,
            userData = response.userData,
            receipt = response.receipt,
            json = response.toJSON()
        )

        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                LogUtilz.log.d(
                    TAG,
                    "Successful purchase request: ${response.requestId}"
                )
                validateOrder(order = order)
            }
            PurchaseResponse.RequestStatus.FAILED -> {
                LogUtilz.log.e(TAG, "Failed purchase request: ${response.requestId}")
                failedOrder(order = order)
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                LogUtilz.log.w(
                    TAG,
                    "Already purchased product for purchase request: ${response.requestId}"
                )
                failedOrder(order = order)
            }
            PurchaseResponse.RequestStatus.INVALID_SKU -> {
                LogUtilz.log.w(
                    TAG,
                    "Invalid sku id for purchase request: ${response.requestId}"
                )
                failedOrder(order = order)
            }
            PurchaseResponse.RequestStatus.NOT_SUPPORTED -> {
                LogUtilz.log.wtf(
                    TAG,
                    "Unsupported purchase request: ${response.requestId}"
                )
                failedOrder(order = order)
            }
            else -> {
                LogUtilz.log.w(
                    TAG,
                    "Unknown request status: ${response.requestId}"
                )
            }
        }
    }

    // step 2
    override fun validateOrder(order: Orderz) {
        order.state = Orderz.State.VALIDATING

        try {
            if (order is AmazonOrder) {
                if (order.receipt?.isCanceled == true) {
                    // revoke
                    LogUtilz.log.wtf(
                        TAG,
                        "AmazonOrder: " +
                            "\norderId: ${order.orderId}," +
                            "\nisCanceled: true"
                    )
                    cancelOrder(order)
                    return
                }
                // Verify the receipts from the purchase by having your back-end server
                // verify the receiptId with Amazon's Receipt Verification Service (RVS) before fulfilling the item
                orderValidatorListener?.validate(order, validatorCallback) ?: LogUtilz.log.e(
                    TAG,
                    "Null validator object. Cannot complete order."
                )
            }
        } catch (e: Exception) {
            order.state = Orderz.State.FAILED
            failedOrder(order)
            LogUtilz.log.e(TAG, e.localizedMessage ?: "error")
        }
    }

    // step 3
    override fun processOrder(order: Orderz) {
        completeOrder(order)
    }

    // step 4
    override fun completeOrder(order: Orderz) {
        try {
            if (order is AmazonOrder) {

                // we check if the order is canceled again before completing
                if (order.receipt?.isCanceled == true) {
                    // revoke
                    cancelOrder(order)
                    LogUtilz.log.wtf(TAG, "isCanceled")
                    return
                }

                when (order.product?.type) {
                    Productz.Type.CONSUMABLE -> completeConsumable(order.receipt)
                    Productz.Type.NON_CONSUMABLE -> completeNonConsumable(order.receipt)
                    Productz.Type.SUBSCRIPTION -> completeSubscription(order.receipt)
                    else -> {}
                }
                // successful
                order.receipt?.receiptId?.let { id ->
                    notifyFulfillment(id, true)
                }
                order.state = Orderz.State.COMPLETE
                currentOrder.postValue(order)
                // update history
                refreshQueries()
            }
        } catch (e: Exception) {
            order.state = Orderz.State.FAILED
            LogUtilz.log.e(TAG, e.localizedMessage ?: "error")
        }
    }

    /**
     * Must be called in onResume
     */
    override fun refreshQueries() {
        isAlreadyQueried = if (isAlreadyQueried) {
            LogUtilz.log.d(TAG, "Skipping purchase history refresh.")
            // skip - prevents double queries on initialization
            false
        } else {
            LogUtilz.log.d(TAG, "Refreshing purchase history.")
            queryOrders()
            true
        }
    }

    /**
     * Must be called in onResume
     */
    override fun queryOrders(): QueryResult<Orderz> {
        currentOrdersQueryId = getPurchaseUpdates(false)
        LogUtilz.log.d(TAG, "queryOrders \npurchaseUpdatesRequestId: $currentOrdersQueryId")
        return AmazonOrdersQuery(this)
    }

    /**
     * @param reset as defined by IAP sdk:
     * - TRUE - Retrieves a user's entire purchase history. You need to store the data somewhere,
     * such as in a server-side data cache or to hold everything in memory.
     * - FALSE - Returns a paginated response of purchase history since the last call
     * to getPurchaseUpdates(). Retrieves the receipts for the user's pending consumable,
     * entitlement, and subscription purchases. Amazon recommends using this approach in most cases.
     */
    private fun getPurchaseUpdates(reset: Boolean): RequestId {
        LogUtilz.log.d(TAG, "getPurchaseUpdates \nreset: $reset")
        return PurchasingService.getPurchaseUpdates(reset)
    }

    internal fun queryOrderHistoryLiveData(): LiveData<AmazonOrderHistory?> {
        return orderHistoryLiveData
    }

    internal fun queryOrderHistoryFlow(): StateFlow<AmazonOrderHistory?> {
        return orderHistoryState
    }

    internal fun queryOrdersLiveData(): LiveData<AmazonOrder?> {
        return queriedOrdersLiveData
    }

    internal fun queryOrdersStateFlow(): StateFlow<AmazonOrder?> {
        return queriedOrdersState
    }

    override fun queryReceipts(type: Productz.Type?): QueryResult<OrderHistoryz> {
        currentHistoryQueryId = getPurchaseUpdates(true)
        LogUtilz.log.d(TAG, "queryReceipts: \npurchaseUpdatesRequestId: $currentHistoryQueryId")
        return AmazonOrdersHistoryQuery(this)
    }

    override fun processPurchaseUpdates(response: PurchaseUpdatesResponse?) {
        response ?: return

        if (response.requestId == currentOrdersQueryId) {
            LogUtilz.log.i(TAG, "OrdersQuery request id found: $currentOrdersQueryId")
            processOrdersQueryResult(response)
        }

        if (response.requestId == currentHistoryQueryId) {
            LogUtilz.log.i(TAG, "OrderHistoryQuery request id found: $currentHistoryQueryId")
            processHistoryQueryResult(response)
        }
    }

    override fun processOrdersQueryResult(response: PurchaseUpdatesResponse) {
        LogUtilz.log.v(TAG, "Processing Orders query: ${response.requestId}")
        processQueryResult(response, false)
    }

    override fun processHistoryQueryResult(response: PurchaseUpdatesResponse) {
        LogUtilz.log.v(TAG, "Processing OrderHistory query: ${response.requestId}")
        processQueryResult(response, true)
    }

    private fun processQueryResult(response: PurchaseUpdatesResponse, isFullHistory: Boolean) {
        LogUtilz.log.v(TAG, "Processing query result: ${response.requestId}")
        when (response.requestStatus) {
            PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                LogUtilz.log.d(
                    TAG,
                    "Successful purchase updates request: ${response.requestId}"
                )
                mainScope.launch(dispatcher.io()) {
                    // Note: Amazon receipts from PurchaseUpdatesResponse do not imply that
                    // the order was successfully completed. Amazon receipts can hold data for
                    // incomplete or canceled orders as well. Handle accordingly.

                    for (r in response.receipts) {
                        when {
                            isOrderComplete(r) -> {
                                // create receipt
                                val receipt = AmazonReceipt(r)
                                receipt.userId = response.userData.userId
                                receipt.marketplace = response.userData.marketplace

                                orderHistory.putIfAbsent(r.receiptId, receipt)

                                if (r.isCanceled) {
                                    notifyFulfillment(r.receiptId, false)
                                }
                            }
                            else -> {
                                // create order
                                val order = AmazonOrder(
                                    resultMessage = response.requestStatus.name,
                                    result = convertPurchaseUpdatesStatus(response.requestStatus),
                                    requestStatus = response.requestStatus.name,
                                    requestId = response.requestId,
                                    userData = response.userData,
                                    receipt = r,
                                    json = response.toJSON()
                                )
                                queriedOrders.putIfAbsent(r.receiptId, order)
                                queriedOrdersStateFlow.emit(order)
                                queriedOrdersLiveData.postValue(order)
                            }
                        }
                    }

                    if (response.hasMore()) {
                        queryReceipts()
                    }

                    if (isFullHistory) {
                        orderHistoryStateFlow.emit(AmazonOrderHistory(orderHistory))
                        orderHistoryLiveData.postValue(AmazonOrderHistory(orderHistory))
                    }
                }
            }
            PurchaseUpdatesResponse.RequestStatus.FAILED -> {
                LogUtilz.log.e(
                    TAG,
                    "Failed purchase updates request: ${response.requestId}"
                )
            }
            PurchaseUpdatesResponse.RequestStatus.NOT_SUPPORTED -> {
                LogUtilz.log.wtf(
                    TAG,
                    "Unsupported purchase update request: ${response.requestId}"
                )
            }
            else -> {
                LogUtilz.log.w(
                    TAG,
                    "Unknown request status: ${response.requestId}"
                )
            }
        }
    }

    private fun convertPurchaseUpdatesStatus(status: PurchaseUpdatesResponse.RequestStatus): Orderz.Result {
        return when (status) {
            PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> Orderz.Result.SUCCESS
            PurchaseUpdatesResponse.RequestStatus.FAILED -> Orderz.Result.ERROR
            PurchaseUpdatesResponse.RequestStatus.NOT_SUPPORTED -> Orderz.Result.NOT_SUPPORTED
        }
    }

    private fun isOrderComplete(receipt: Receipt): Boolean {
        if (receipt.isCanceled) {
            return true
        }

        when (receipt.productType) {
            ProductType.SUBSCRIPTION -> {
                // You will always receive a receipt for subscription and entitlement purchases.
                return true
            }
            ProductType.ENTITLED -> {
                // You will always receive a receipt for subscription and entitlement purchases.
                return true
            }
            ProductType.CONSUMABLE -> {
                // unfulfilled or canceled
                // If a consumable transaction is successful, and you record fulfillment information in
                // Amazon’s systems (e.g. you call notifyFullfilment), you will not receive a receipt.
                // The method only returns fulfilled consumable purchases in rare cases,
                // such as if an app crashes after fulfillment but before Amazon is notified,
                // or if an issue occurs on Amazon's end after fulfillment. In these cases,
                // you would need to remove the duplicate receipts so as not to over fulfill the item.
                // When you deliver an item, record somewhere that you have done so, and do not deliver
                // again even if you receive a second receipt.
                return false
            }
            else -> {}
        }
        return true
    }

    override fun setObfuscatedIdentifiers(accountId: String?, profileId: String?) {
        LogUtilz.log.w(TAG, "setObfuscatedIdentifiers: is not supported by Amazon IAP.")
    }

    private fun completeConsumable(receipt: Receipt?) {
        LogUtilz.log.v(TAG, "completeConsumable")
        receipt ?: return
        val amazonReceipt = AmazonReceipt(receipt)
        currentReceipt.postValue(amazonReceipt)
        orderUpdaterListener?.onComplete(amazonReceipt)
    }

    private fun completeNonConsumable(receipt: Receipt?) {
        LogUtilz.log.v(TAG, "completeNonConsumable")
        receipt ?: return
        val amazonReceipt = AmazonReceipt(receipt)
        currentReceipt.postValue(amazonReceipt)
        orderUpdaterListener?.onComplete(amazonReceipt)
    }

    /**
     * If the subscription is continuous and has never been canceled at any point, the app will only receive one receipt for that subscription/customer.
     * If the subscription was not continuous, for example, the customer did not auto-renew, let the subscription lapse, and then subscribed again a month later,
     * the app will receive multiple receipts.
     */
    private fun completeSubscription(receipt: Receipt?) {
        LogUtilz.log.v(TAG, "completeSubscription")
        receipt ?: return
        val amazonReceipt = AmazonReceipt(receipt)
        currentReceipt.postValue(amazonReceipt)
        orderUpdaterListener?.onComplete(amazonReceipt)
    }

    override fun cancelOrder(order: Orderz) {
        LogUtilz.log.v(TAG, "cancelOrder")
        if (order is AmazonOrder) {
            order.receipt?.receiptId?.let { id ->
                notifyFulfillment(id, false)
            }
        }
        order.state = Orderz.State.CANCELED
        orderUpdaterListener?.onFailure(order)
        currentOrder.postValue(order)
    }

    override fun failedOrder(order: Orderz) {
        LogUtilz.log.v(TAG, "failedOrder")
        if (order is AmazonOrder) {
            order.receipt?.receiptId?.let { id ->
                notifyFulfillment(id, false)
            }
        }
        order.state = Orderz.State.FAILED
        orderUpdaterListener?.onFailure(order)
        currentOrder.postValue(order)
    }

    private fun notifyFulfillment(receiptId: String, acknowledge: Boolean) {
        LogUtilz.log.v(
            TAG,
            "notifyFulfillment:" +
                "\nreceiptId: $receiptId," +
                "\nacknowledge: $acknowledge"
        )
        try {
            val result = if (acknowledge) {
                FulfillmentResult.FULFILLED
            } else {
                FulfillmentResult.UNAVAILABLE
            }
            PurchasingService.notifyFulfillment(
                receiptId,
                result
            )
        } catch (e: Exception) {
            LogUtilz.log.e(TAG, "Failed to acknowledge order: $e")
        }
    }

    override fun destroy() {
        LogUtilz.log.v(TAG, "destroy")
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "AmazonSales"
    }
}
