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
import java.util.ArrayList;

public class FilesRVAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface FilesRVAdapterParent{
        void itemClicked(int position, String path);
        void share(int position, String path);
        Context getContext();
    }

    String[] paths;
    Context context;
    FilesRVAdapterParent parent;

    public FilesRVAdapter(String[] paths, FilesRVAdapterParent parent){
        this.context = parent.getContext();
        this.paths = paths;
        this.parent = parent;
    }

    class MainViewHolder extends RecyclerView.ViewHolder{
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
        mvh.tvSize.setText(Utils.bytesToKB(file.length())+" KB");
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
