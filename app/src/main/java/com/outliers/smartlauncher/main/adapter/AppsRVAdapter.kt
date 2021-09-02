/*
 *     Copyright (C) 2021.  Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
                if (layoutPosition < appModels.size) {
                    parent.onItemClick(
                        layoutPosition,
                        appModels[layoutPosition],
                        null
                    )
                }
            }
            view.setOnLongClickListener {
                if (layoutPosition < appModels.size) {
                    parent.onItemLongPress(it, appModels[layoutPosition], null)
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
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