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

package com.outliers.smartlauncher.debugtools.backup;

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
import java.util.ArrayList;

public class FilesRVAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface FilesRVAdapterParent {
        void itemClicked(int position, String path);

        void save(int position, String path);

        void replace(int position, String path);

        Context getContext();
    }

    ArrayList<String> paths;
    Context context;
    FilesRVAdapterParent parent;

    public FilesRVAdapter(ArrayList<String> paths, FilesRVAdapterParent parent) {
        this.context = parent.getContext();
        this.paths = paths;
        this.parent = parent;
    }

    class MainViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        ImageView ivSave, ivReplace;
        TextView tvSize;

        public MainViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            ivSave = itemView.findViewById(R.id.iv_save);
            ivReplace = itemView.findViewById(R.id.iv_replace);
            tvSize = itemView.findViewById(R.id.tv_file_size);

            itemView.setOnClickListener(v -> parent.itemClicked(getLayoutPosition(), paths.get(getLayoutPosition())));

            ivSave.setOnClickListener(v -> {
                parent.save(getLayoutPosition(), paths.get(getLayoutPosition()));
            });

            ivReplace.setOnClickListener(v -> {
                parent.replace(getLayoutPosition(), paths.get(getLayoutPosition()));
            });
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MainViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_data_file, null));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MainViewHolder mvh = (MainViewHolder) holder;
        File file = new File(paths.get(position));
        mvh.tvFileName.setText(file.getName());
        mvh.tvSize.setText(Utils.bytesToKB(file.length()) + " KB");
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }
}
