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
import com.outliers.smartlauncher.utils.LogHelper;
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

    private void sendMail(){
        //TODO Write External Storage permission
        if(!checkWritePermission())
            return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"appsupport@happay.in"});
        intent.putExtra(Intent.EXTRA_CC, new String[] {"sandeep@happay.in","asutosh.nayak@happay.in"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "User Log - "+cid);
        intent.putExtra(Intent.EXTRA_TEXT, "User Logs");
        try {
            if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                Toast.makeText(this,"External memory not available. Please try again",Toast.LENGTH_SHORT).show();
                return;
            }
            ProgressDialog pd = ProgressDialog.show(this,"Gathering log files","shouldn't be more than a few seconds",true,true);
            File sourceDir = Utils.getAppFolderInternal(this);
            File destDir = new File(LogHelper.getLogHelper(this).getExternalTempDir());


            File[] files = destDir.listFiles();
            if(files != null && files.length > 0) { //if destination already has some old log files delete them. else proceed
                for (File file : files) {
                    file.delete();
                }
            }

            FileUtils.copyDirectory(sourceDir, destDir);
            pd.hide();

            files = destDir.listFiles();
            ArrayList<Uri> uris = new ArrayList<>();
            //Log.e("getAllFilesInDir", "Size: "+ files.length);
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            for(int i = 0; i < files.length; i++)
            {
                //Log.e("getAllFilesInDir", "FileName:" + files[i].getAbsolutePath());
                Uri uri = FileProvider.getUriForFile(getApplicationContext()
                        , getPackageName() + ".provider", files[i]);
                //Uri uri = Uri.parse("file://" + files[i]);
                //intent.putExtra(Intent.EXTRA_STREAM, uri);
                uris.add(uri);
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, "Choose application to share logs with our development team"),1);

        } catch (IOException e) {
            //Log.e("AttachError","E",e);
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();
        }
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
