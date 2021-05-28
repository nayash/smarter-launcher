/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.main.adapter

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.main.adapter.AppsRVAdapter.AppsViewHolder
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.utils.Utils
import java.util.*

class AppsRVAdapter(
    var appModels: ArrayList<AppModel>,
    var context: Context,
    private val parent: IAppsRVAdapter
) : RecyclerView.Adapter<AppsViewHolder>() {
    interface IAppsRVAdapter {
        fun onItemClick(position: Int, appModel: AppModel, extras: Bundle?)
        fun onItemLongPress(view: View, appModel: AppModel, extras: Bundle?)
    }

    inner class AppsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var appIcon: ImageView
        var tvAppName: TextView

        init {
            appIcon = view.findViewById(R.id.iv_app)
            tvAppName = view.findViewById(R.id.tv_app_name)
            view.setOnClickListener {
                parent.onItemClick(
                    layoutPosition,
                    appModels[layoutPosition],
                    null
                )
            }
            view.setOnLongClickListener {
                parent.onItemLongPress(it, appModels[layoutPosition], null)
                return@setOnLongClickListener true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsViewHolder {
        return AppsViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, null))
    }

    override fun onBindViewHolder(holder: AppsViewHolder, position: Int) {
        val model = appModels[position]
        holder.appIcon.setImageDrawable(model.appIcon)
        var name = if (Utils.isValidString(model.appName)) model.appName else "Unknown"
        /*if (name.length > 10) {
            name = name.substring(0, 8) + ".."
        }*/
        holder.tvAppName.text = name
    }

    override fun getItemCount(): Int {
        return appModels.size
    }
}