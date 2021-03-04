package com.zuko.billingz.lib.sales

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase

/**
 * @author rjsuzuki
 */
data class Order(val billingResult: BillingResult? = null,
                 val purchase: Purchase? = null,
                 val msg: String = "")

