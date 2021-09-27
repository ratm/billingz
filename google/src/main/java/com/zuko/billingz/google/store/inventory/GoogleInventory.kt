package com.zuko.billingz.google.store.inventory

import android.util.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.SkuDetailsResult
import com.android.billingclient.api.querySkuDetails
import com.zuko.billingz.core.LogUtilz
import com.zuko.billingz.core.store.inventory.Inventoryz
import com.zuko.billingz.core.store.model.Productz
import com.zuko.billingz.google.store.client.GoogleClient
import com.zuko.billingz.google.store.model.GoogleProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleInventory(private val client: GoogleClient): Inventoryz {

    override var allProducts: Map<String, Productz.Type> = ArrayMap() // todo remove

    override var consumables: Map<String, Productz> = ArrayMap()
    override var nonConsumables: Map<String, Productz> = ArrayMap()
    override var subscriptions: Map<String, Productz> = ArrayMap()
    override var requestedProducts: MutableLiveData<Map<String, Productz>> = MutableLiveData()

    private val mainScope = MainScope()

    private suspend fun queryProducts(skus: List<String>, type: Productz.Type): SkuDetailsResult? {
        val skuType = when(type) {
            Productz.Type.CONSUMABLE -> BillingClient.SkuType.INAPP
            Productz.Type.NON_CONSUMABLE -> BillingClient.SkuType.INAPP
            Productz.Type.SUBSCRIPTION -> BillingClient.SkuType.SUBS
            else -> {
                BillingClient.SkuType.INAPP
            }
        }
        val builder = SkuDetailsParams.newBuilder()
        val params = builder
            .setSkusList(skus)
            .setType(skuType)
            .build()
        return client.getBillingClient()?.querySkuDetails(params)
    }

    private fun handleQueryResult(result: SkuDetailsResult?, type: Productz.Type) {
        LogUtilz.log.d(TAG, "Processing inventory query result ->" +
                "\n type: $type," +
                "\n billingResult: ${result?.billingResult}," +
                "\n products: ${result?.skuDetailsList}" +
                "\n -----------------------------------")
        if(result?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK
            && !result.skuDetailsList.isNullOrEmpty()) {
                val availableProducts = mutableListOf<Productz>()
                result.skuDetailsList?.let { skus ->
                    for(s in skus) {
                        val product = GoogleProduct(skuDetails = s, type = type)
                        availableProducts.add(product)
                    }
                }
            updateInventory(products = availableProducts, type = type)
        }
    }

    override fun queryInventory(products: Map<String, Productz.Type>): LiveData<Map<String, Productz>> {
        LogUtilz.log.i(TAG, "queryInventory(" +
                "\n products: ${products.size}," +
                "\n )")
        allProducts = products

        if(products.isNotEmpty()) {
            mainScope.launch {
                LogUtilz.log.d(TAG, "inventory coroutines starting")

                val consumables = mutableListOf<String>()
                val nonConsumables = mutableListOf<String>()
                val subscriptions = mutableListOf<String>()

                withContext(Dispatchers.IO) {
                    products.forEach { entry ->
                        when (entry.value) {
                            Productz.Type.CONSUMABLE -> consumables.add(entry.key)
                            Productz.Type.NON_CONSUMABLE -> nonConsumables.add(entry.key)
                            Productz.Type.SUBSCRIPTION -> subscriptions.add(entry.key)
                            else -> {
                                LogUtilz.log.w(TAG, "Unknown Type: ${entry.key}")
                            }
                        }
                    }
                }
                launch(Dispatchers.IO) {
                    LogUtilz.log.d(TAG, "inventory coroutines consumables queried")
                    val result = queryProducts(skus = consumables, type = Productz.Type.CONSUMABLE)
                    handleQueryResult(result = result, type = Productz.Type.CONSUMABLE)
                }
                launch(Dispatchers.IO) {
                    LogUtilz.log.d(TAG, "inventory coroutines nonConsumables queried")
                    val result = queryProducts(skus = nonConsumables, type = Productz.Type.NON_CONSUMABLE)
                    handleQueryResult(result = result, type = Productz.Type.NON_CONSUMABLE)
                }
                launch(Dispatchers.IO) {
                    LogUtilz.log.d(TAG, "inventory coroutines subscriptions queried")
                    val result = queryProducts(skus = subscriptions, type = Productz.Type.SUBSCRIPTION)
                    handleQueryResult(result = result, type = Productz.Type.SUBSCRIPTION)
                }
            }
        }
        return requestedProducts
    }

    override fun updateInventory(products: List<Productz>?, type: Productz.Type) {
        LogUtilz.log.i(TAG, "updateInventory(" +
                "\n products: ${products?.size ?: 0}," +
                "\n type: $type," +
                "\n )")
        if (!products.isNullOrEmpty()) {
            when (type) {
                Productz.Type.CONSUMABLE -> {
                    consumables = consumables + products.associateBy { it.sku.toString() }
                    requestedProducts.postValue(consumables)
                }
                Productz.Type.NON_CONSUMABLE -> {
                    nonConsumables = nonConsumables + products.associateBy { it.sku.toString() }
                    requestedProducts.postValue(nonConsumables)
                }
                Productz.Type.SUBSCRIPTION -> {
                    subscriptions = subscriptions + products.associateBy { it.sku.toString() }
                    requestedProducts.postValue(subscriptions)
                }
                else -> {
                    LogUtilz.log.w(TAG, "Unhandled product type: $type")
                }
            }
        }
    }

    override fun getProduct(sku: String?): Productz? {
        LogUtilz.log.v(TAG, "getProduct: $sku")
        if(consumables.containsKey(sku))
            return consumables[sku]
        if(nonConsumables.containsKey(sku))
            return nonConsumables[sku]
        if(subscriptions.containsKey(sku))
            return subscriptions[sku]
        return null
    }

    override fun getProducts(type: Productz.Type?, promo: Productz.Promotion?): Map<String, Productz> {
        when (type) {
            Productz.Type.CONSUMABLE -> {
                if(promo != null) {
                    consumables.forEach { entry ->
                        val promos = ArrayMap<String, Productz>()
                        if(entry.value.promotion == promo) {
                            promos[entry.key] = entry.value
                        }
                        return promos
                    }
                }
                return consumables
            }
            Productz.Type.NON_CONSUMABLE -> {
                if(promo != null) {
                    nonConsumables.forEach { entry ->
                        val promos = ArrayMap<String, Productz>()
                        if(entry.value.promotion == promo) {
                            promos[entry.key] = entry.value
                        }
                        return promos
                    }
                }
                return nonConsumables
            }
            Productz.Type.SUBSCRIPTION -> {
                if(promo != null ) {
                    subscriptions.forEach { entry ->
                        val promos = ArrayMap<String, Productz>()
                        if(entry.value.promotion == promo) {
                            promos[entry.key] = entry.value
                        }
                        return promos
                    }
                }
                return subscriptions
            }
            else -> {
                val all = ArrayMap<String, Productz>()
                all.putAll(consumables)
                all.putAll(nonConsumables)
                all.putAll(subscriptions)
                return all
            }
        }
    }

    override fun destroy() {
        LogUtilz.log.v(TAG,"destroy")
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "GoogleInventory"
    }
}