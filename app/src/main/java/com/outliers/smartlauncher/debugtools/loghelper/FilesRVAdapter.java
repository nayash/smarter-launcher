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

package com.outliers.smartlauncher.debugtools.loghelper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.outliers.smartlauncher.R;
import com.outliers.smartlauncher.utils.Utils;

import java.io.File;

public class FilesRVAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface FilesRVAdapterParent {
        void itemClicked(int position, String path);

        void share(int position, String path);

        Context getContext();
    }

    String[] paths;
    Context context;
    FilesRVAdapterParent parent;

    public FilesRVAdapter(String[] paths, FilesRVAdapterParent parent) {
        this.context = parent.getContext();
        this.paths = paths;
        this.parent = parent;
    }

    class MainViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        ImageView ivShare;
        TextView tvSize;

        public MainViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            ivShare = itemView.findViewById(R.id.iv_share);
            tvSize = itemView.findViewById(R.id.tv_file_size);

            itemView.setOnClickListener(v -> parent.itemClicked(getLayoutPosition(), paths[getLayoutPosition()]));

            ivShare.setOnClickListener(v -> {
                parent.share(getLayoutPosition(), paths[getLayoutPosition()]);
            });
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MainViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_file, null));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MainViewHolder mvh = (MainViewHolder) holder;
        File file = new File(paths[position]);
        mvh.tvFileName.setText(file.getName());
        mvh.tvSize.setText(Utils.bytesToKB(file.length()) + " KB");
    }

    @Override
    public int getItemCount() {
        return paths.length;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }
}
