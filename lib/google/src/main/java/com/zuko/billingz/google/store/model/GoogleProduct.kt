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

package com.zuko.billingz.google.store.model

import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.zuko.billingz.core.store.model.PricingInfo
import com.zuko.billingz.core.store.model.Productz
import java.util.Currency

/**
 * https://developer.android.com/reference/com/android/billingclient/api/SkuDetails
 */
data class GoogleProduct(
    val skuDetails: SkuDetails,
    override val type: Productz.Type
) : Productz {

    var id: Int = -1
    override val sku: String = skuDetails.sku
    override val title: String = skuDetails.title
    override val price: String = skuDetails.price
    override val description: String = skuDetails.description
    override val iconUrl: String = skuDetails.iconUrl

    override val promotion: Productz.Promotion = when {
        skuDetails.freeTrialPeriod.isNotBlank() -> Productz.Promotion.FREE
        skuDetails.introductoryPrice.isNotBlank() -> Productz.Promotion.PROMO
        skuDetails.introductoryPricePeriod.isNotBlank() -> Productz.Promotion.PROMO
        else -> Productz.Promotion.NONE
    }
    override val pricingInfo: PricingInfo = PricingInfo(
        introPrice = skuDetails.introductoryPrice,
        introPricePeriod = skuDetails.introductoryPricePeriod,
        billingPeriod = skuDetails.subscriptionPeriod,
        trialPeriod = skuDetails.freeTrialPeriod
    )

    override fun getCurrency(): Currency {
        BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION
        return Currency.getInstance(skuDetails.priceCurrencyCode)
    }

    override fun isAmazon(): Boolean {
        return false
    }

    override fun isGoogle(): Boolean {
        return true
    }

    /**
     * Specifies the purchase token of the SKU that the user is upgrading or downgrading from.
     */
    private var oldSkuPurchaseToken: String? = null

    /**
     * Specifies the mode of proration during subscription upgrade/downgrade.
     */
    private var replaceSkusProrationMode: Int = BillingFlowParams.ProrationMode.DEFERRED

    enum class SubscriptionLifecycle(val summary: String) {
        ACTIVE("User is in good standing and has access to the subscription"),
        CANCELLED(" User has cancelled but still has access until expiration"),
        IN_GRACE_PERIOD("User experienced a payment issue, but still has access while Google is retrying the payment method"),
        ON_HOLD("User experienced a payment issue, and no longer has access while Google is retrying the payment method"),
        PAUSED("User paused their access, and does not have access until they resume"),
        EXPIRED("User has cancelled and lost access to the subscription. The user is considered churned at expiration")
    }
}
