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
package com.zuko.billingz.ui

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.android.billingclient.api.PurchaseHistoryRecord
import com.zuko.billingz.R
import com.zuko.billingz.databinding.ListItemProductBinding
import com.zuko.billingz.lib.store.products.Product

/**
 * RecyclerView Adapter for a mutable list of [Product] objects.
 * @constructor
 * @param list
 * @param listener
 */
class ProductAdapter(private val list: MutableList<Product>, private var listener: ProductSelectedListener?) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val binding: ListItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ListItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val item = list[position]
        holder.binding.productTitle.text = item.sku ?: ""
        holder.binding.productDescription.text = item.description ?: ""
        holder.binding.productBuyBtn.setOnClickListener {
            listener?.onPurchaseRequested(item)
            (it as LottieAnimationView).playAnimation()
        }
        holder.binding.productEditIb.setOnClickListener {
            listener?.onEditRequested(item)
        }

        holder.binding.productRemoveIb.setOnClickListener {
            val dialog = AlertDialog.Builder(it.context, R.style.StudioDialog).create()
            dialog.setTitle(it.resources.getString(R.string.confirm_deletion))
            dialog.setMessage("${item.sku}?")
            dialog.setIcon(ResourcesCompat.getDrawable(it.context.resources, R.drawable.ic_baseline_delete_24, it.context.theme))
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, it.resources.getString(R.string.no)) { d, _ -> d?.cancel() }
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, it.resources.getString(R.string.yes)) { d, _ ->
                removeProduct(item)
                d.dismiss()
            }
            dialog.show()
        }

        holder.binding.root.animation = AnimationUtils.loadAnimation(holder.binding.root.context, R.anim.product_item_anim)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    /**
     * Add [Product] to list
     */
    @Suppress("unused")
    fun addProduct(product: Product) {
        val previousSize = list.size
        list.add(product)
        notifyItemInserted(previousSize)
    }

    /**
     * Remove [Product] from list
     */
    @Suppress("unused")
    fun removeProduct(product: Product) {
        val position = list.indexOf(product)
        if (position > -1) {
            listener?.onProductDeleted(product)
            list.remove(product)
            notifyItemRemoved(position)
        }
    }

    /**
     * Update Product list with diffUtil.
     */
    @Suppress("unused")
    fun updateList(newList: MutableList<Product>) {
        val oldList = mutableListOf<Product>()
        oldList.addAll(list)
        list.clear()
        list.addAll(newList)
        val diffResult = DiffUtil.calculateDiff(ProductDiffCallback(oldList, list))
        diffResult.dispatchUpdatesTo(this)
    }

    companion object {
        private const val TAG = "ProductAdapter"
    }
}
