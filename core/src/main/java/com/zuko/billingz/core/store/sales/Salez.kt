package com.zuko.billingz.core.store.sales

import android.app.Activity
import androidx.lifecycle.MutableLiveData
import com.zuko.billingz.core.misc.CleanUpz
import com.zuko.billingz.core.store.client.Clientz
import com.zuko.billingz.core.store.model.Orderz
import com.zuko.billingz.core.store.model.Productz
import com.zuko.billingz.core.store.model.Receiptz

interface Salez : CleanUpz {
    /**
     * Provides a liveData [Orderz] object for
     * developers to observe and react to on
     * the UI/Main thread.
     * Objects can be passed from the normal purchase flow
     * or when the app is verifying a list of queried purchases.
     */
    var currentReceipt: MutableLiveData<Receiptz>

    /**
     *
     */
    var orderHistory: MutableLiveData<List<Receiptz>>

    /**
     *
     */
    var orderUpdaterListener: OrderUpdaterListener?

    /**
     *
     */
    var orderValidatorListener: OrderValidatorListener?

    /**
     *
     */
    fun startOrder(activity: Activity?, product: Productz, client: Clientz)

    /**
     *
     */
    fun validateOrder(order: Orderz)

    /**
     *
     */
    fun processOrder(order: Orderz)

    /**
     *
     */
    fun completeOrder(order: Orderz)

    /**
     *
     */
    fun cancelOrder(order: Orderz)

    /**
     *
     */
    fun failedOrder(order: Orderz)

    /**
     *
     */
    fun refreshQueries()

    /**
     *
     */
    fun queryOrders()

    /**
     *
     */
    fun queryReceipts(type: Productz.Type? = null)

    /**
     * For developers to implement.
     * Enables developer to provide another verification step before finalizing an order. Also,
     * Purchases can be made outside of app, or finish while app is in background, and may not have
     * completed in a regular ui-flow and requires attention again.
     * show in-app popup, or deliver msg to an inbox, or use an OS notification.
     */
    interface OrderUpdaterListener {

        /**
         * @param order
         * @param productType
         * @param callback
         */
        fun onResume(order: Orderz, callback: UpdaterCallback)

        /**
         *
         */
        fun onComplete(receipt: Receiptz)

        fun onError(order: Orderz)
    }

    /**
     *
     */
    interface UpdaterCallback {

        /**
         * Final step in completing an order. Developers should implement a way to persist their
         * Receipts prior to calling this method.
         */
        fun complete(order: Orderz)

        /**
         *
         */
        fun cancel(order: Orderz)
    }

    /**
     * For developers to implement.
     * Enables the ability to verify purchases with your own logic,
     * ensure entitlement was not already granted for this purchaseToken,
     * and grant entitlement to the user.
     */
    interface OrderValidatorListener {

        /**
         * @param order
         * @param callback
         */
        fun validate(order: Orderz, callback: ValidatorCallback)
    }

    /**
     * Respond to the events triggered by the developer's validator.
     * Developers will need to implement this interface if custom validation checks
     * need to be provided before finalizing an order.
     * If the purchase is properly verified, call onSuccess,
     * otherwise call onFailure so the library can appropriately continue the
     * lifecycle of a customer's order.
     */
    interface ValidatorCallback {

        /**
         * Developers should verify the order with their own backend records of a users purchase
         * history prior to calling this method.
         * @param order
         */
        fun validated(order: Orderz)

        /**
         * Call if order is deemed invalid due to the nature of the purchase. i.e. the order was
         * fulfilled already or the sku is no longer available, etc.
         * @param order
         */
        fun invalidate(order: Orderz)
    }
}