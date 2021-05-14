/*
package com.outliers.smartlauncher.ui;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.os.FileUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.outliers.smartlauncher.R;
import com.outliers.smartlauncher.consts.Constants;
import com.outliers.smartlauncher.debugtools.loghelper.LogHelper;
import com.outliers.smartlauncher.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class CrashHandlerActivity extends AppCompatActivity {

    private static final int WRITE_PERMISSION_REQUEST = 1;
    String crashId;
    SharedPreferences pref;

    @Override
    public void onCreate(Bundle onSavedInstanceState){
        super.onCreate(onSavedInstanceState);

        setContentView(R.layout.layout_activity_crash_handle);
        //Log.e("OnCreate","CrashHandler");
        findViewById(R.id.btn_send_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMail();
            }
        });
        pref = getSharedPreferences(Constants.Companion.getPREF_NAME(), Context.MODE_PRIVATE);

        crashId = getIntent().getStringExtra("crash_id");
        //String cause = pref.getString("cause", "");
        ((TextView)findViewById(R.id.tv_crash_id)).setText(crashId);
        ((TextView)findViewById(R.id.tv_time)).setText(Utils.getDate(System.currentTimeMillis(), "dd/mm/yyyy"));
    }

    @Override
    public void onStart(){
        super.onStart();
        pref.edit().putBoolean("crash_restart", false).apply();
        pref.edit().putString("crash_id", "").apply();
        // pref.edit().putString("cause", "").apply();
        // LogHelper.getLogHelper(this).printLogFileContent();
    }

    private boolean checkWritePermission() {
        boolean permissionGranted = false;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                permissionGranted = true;
            } else {
                permissionGranted = false;
                Toast.makeText(this, "Please enable storage permission and try again", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, WRITE_PERMISSION_REQUEST);
            }
        } catch (Exception ex) {

        }
        return permissionGranted;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        //Log.e("onActivityRes",requestCode+","+resultCode+",RESULT_OK="+RESULT_OK);
        if(resultCode == RESULT_OK)
            Toast.makeText(this,"Thanks for sharing",Toast.LENGTH_SHORT).show();
File temp = new File(LogHelper.getLogHelper(this).getExternalTempDir());
        if(temp.exists() && temp.delete()){
            Log.e("onActivityRes","Temp logs deleted");
        }

        super.onActivityResult(requestCode, resultCode, intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == WRITE_PERMISSION_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults.length > 0 && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        sendMail();
                    }
                } else {
                    Toast.makeText(this,
                            "Please provide storage permission to share logs with mail app.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
*/
