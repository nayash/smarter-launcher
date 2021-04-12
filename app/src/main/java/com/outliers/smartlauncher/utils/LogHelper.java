package com.outliers.smartlauncher.utils;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;

import com.outliers.smartlauncher.BuildConfig;
import com.outliers.smartlauncher.consts.Constants;
import com.outliers.smartlauncher.core.SmartLauncherRoot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LogHelper {

    public static final String LOG_FILE_NAME_FORMAT = "dd_MM_yyyy"; //-HH_mm_ss
    public static final String LOG_INFO_PREFIX_DATE_FORMAT = "HH:mm:ss"; //dd MM yyyy
    private final int LOG_WRITE_BATCH_SIZE = 20;
    private final int PAST_DAYS_LOGS_TO_KEEP = 4;
    private final String LOG_FILE_PREFIX = "SmartLauncherLog_";
    public static final String SHARED_PREF_KEY_PENDING_LOG = "pending_logs";
    //public static final String LOG_TEMP_DIR = Environment.getExternalStorageDirectory()+"/.logtemp";

    public enum LOG_LEVEL {INFO, WARNING, ERROR, CRITICAL}

    private static LogHelper logHelper;
    private File logFile;
    private LinkedList<String> llPendingLogs;
    private ExecutorService executorService;
    private Runnable writeLogAsyncRunnable;
    private boolean isLoggingAllowed = true;
    private Object lock;
    private Format logDateTimeFormat; //log text prefix
    private Date dummyDateObj;
    Handler shutdownHandler;
    Runnable shutdownRunnable;
    Application application;
    private Context context;
    private SharedPreferences logSP;

    public static LogHelper getLogHelper(Context context) {
        if(logHelper == null) {
            Log.e("test-logHelper", "constructor called");
            logHelper = new LogHelper(context);
        }

        return logHelper;
    }

    private LogHelper(Context context){
        this.context = context;
        application = (Application) context.getApplicationContext();
        logFile = new File(Utils.getAppFolderInternal(context),LOG_FILE_PREFIX +
                Utils.getDate(System.currentTimeMillis(), LOG_FILE_NAME_FORMAT)+".txt");
        Log.d("test-LogHelper",logFile.getAbsolutePath());

        if(!logFile.exists()) {
            try {
                logFile.createNewFile();
                //TODO write device details here
                deleteOldLogs(PAST_DAYS_LOGS_TO_KEEP);
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }

        init();
    }

    private void init(){
        lock = new Object();
        llPendingLogs = new LinkedList<>();
        executorService = Executors.newFixedThreadPool(5);
        writeLogAsyncRunnable = new Runnable() {
            @Override
            public void run() {
                writeLogs();
            }
        };
        logDateTimeFormat = new SimpleDateFormat(LOG_INFO_PREFIX_DATE_FORMAT);
        dummyDateObj = new Date();
        shutdownRunnable = new Runnable() {
            @Override
            public void run() {
                executorService.shutdown(); //shuts down service after finishing pending tasks
                try {
                    //Log.e("LogFWFinish", "Shutting Down...");
                    executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    //Log.e("LogFWF", "E", e);
                }
                logHelper = null;
            }
        };
        shutdownHandler = new Handler();
        flushPreviousSessionLogs();
        writeDeviceDetails();
        flush();
    }

    private void writeDeviceDetails(){
        try {
            addLogToQueue("App version : " + Utils.getLibVersion()+
                    ", Device details : "+ Utils.getDeviceDetails(), LOG_LEVEL.INFO, "LogHelper");
            // llPendingLogs.add("App version is : " + ProjectUtil.getAppVersion(context));
        }catch (Exception ex){
            Log.d("logging-exp", ex.getMessage());
        }
    }

    public SharedPreferences getLogSharedPref(){
        if(logSP == null)
            logSP = application.getSharedPreferences("LogHelper", Context.MODE_PRIVATE);

        return logSP;
    }

    private void flushPreviousSessionLogs(){
        String pendingLogs = getLogSharedPref().getString(SHARED_PREF_KEY_PENDING_LOG,"");
        //Log.e("init","pending:"+pendingLogs);
        if(!pendingLogs.isEmpty())
            llPendingLogs.addAll(Arrays.asList(pendingLogs.split("\n")));
        writePendingLogs();

        SharedPreferences pref = context.getSharedPreferences(Constants.Companion.getPREF_NAME(),
                Context.MODE_PRIVATE);
        boolean crashRestart = pref.getBoolean("crash_restart", false);
        Log.v("flushPrevLog", crashRestart+", "+pendingLogs);
        pref.edit().putString(SHARED_PREF_KEY_PENDING_LOG,"").commit();
        if (crashRestart) {
            pref.edit().putBoolean("crash_restart", false).apply();
            pref.edit().putString("crash_id", "").apply();
        }
    }

    public void addLogToQueue(String logText, LOG_LEVEL level, String callingComponentName){
        if(logText == null || level == null) {
            //Log.e("addLogToQueue", "Parameters can't be null. Log skipped");
            return;
        }
        if(callingComponentName == null)
            callingComponentName = "";

        addLogToQueue(formatLogText(logText,level,callingComponentName));
    }

    public void addLogToQueue(String logText, LOG_LEVEL level, Context callingComponent){
        if(logText == null || level == null || callingComponent == null) {
            //Log.e("addLogToQueue", "Parameters can't be null. Log skipped");
            return;
        }
        //Log.e("addLogToQueueCtx", "Called");
        addLogToQueue(logText,level,callingComponent.getClass().getSimpleName());
    }

    private synchronized void addLogToQueue(String logText){ //TODO skips events in quick succession.Fix it!
        if(!isLoggingAllowed)
            return;

        synchronized (lock) {
            if(BuildConfig.DEBUG)
                Log.e("test-addLogToQueueMain", logText);
            llPendingLogs.addLast(logText);
            //Log.e("addLogQ",llPendingLogs.size()+"");
            if (llPendingLogs.size() >= LOG_WRITE_BATCH_SIZE) {
                /*try { //TODO force system to wait adding logs and start writing to file
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
                /** if collection size exceeds max allowed queue size, call write method and dump written texts **/
                writePendingLogs();
            }
        }
    }

    private synchronized void writePendingLogs(){
        executorService.submit(writeLogAsyncRunnable);
    }

    public void disableLogging(){
        writePendingLogs();
        isLoggingAllowed = false;
    }

    public void enableLogging(){
        isLoggingAllowed = true;
    }

    private void writeLogs(){ /** Never call directly. Call writePendingLogs instead which executes this in separate thread **/
        synchronized (lock) {
            String s;
            StringBuilder sb = new StringBuilder();
            /*if(Looper.myLooper() == Looper.getMainLooper()){
                Log.e("writeLogs","Writing on main thread!!!");
            }*/
            while ((s = llPendingLogs.poll()) != null) {
                //Log.e("writeAppending",s);
                sb.append(s+"\n"); //append formatted texts
            }
            sb.deleteCharAt(sb.length()-1);
            writeLogs(logFile,sb.toString());
        }
    }


    //<editor-fold desc="Log File Delete Methods">
    public void getAllFilesInDir(){ /** Only for testing purpose **/
        File directory = new File(logFile.getParent());
        File[] files = directory.listFiles();
        //Log.e("getAllFilesInDir", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            //Log.e("getAllFilesInDir", "FileName:" + files[i].getName());
        }
    }

    public void printLogFileContent(){ /** Only for testing purpose **/
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(logFile.getAbsolutePath()));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            //Log.e("printLogFileContentLast",sb.toString().substring(sb.length()-300,sb.length()-1));
        } catch (FileNotFoundException e) {
            //Log.e("FileNoF","E",e);
        } catch (IOException e) {
            //Log.e("IOE","E",e);
        }catch(Exception ex){
            //Log.e("Error","printFileContent",ex);
        } finally {
            try {
                if(br != null)
                    br.close();
            } catch (IOException e) {
                //Log.e("IOE2","E",e);
            }
        }
    }

    public void deleteLogFile_NameLike(String fileNameLike){
        File directory = new File(logFile.getParent());
        File[] files = directory.listFiles();
        //Log.e("deleteLogFile_NameLike", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            //Log.e("deleteLogFile_NameLike", "FileName:" + files[i].getName());
            if(files[i].getName().contains(fileNameLike)){
                files[i].delete();
            }
        }
    }

    public void deleteLogFile_NameNotLike(String fileNameLike){
        File directory = new File(logFile.getParent());
        File[] files = directory.listFiles();
        //Log.e("deleteLog_NameNotLike", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            //Log.e("deleteLog_NameNotLike", "FileName:" + files[i].getName());
            if(!files[i].getName().contains(fileNameLike)){
                files[i].delete();
            }
        }
    }

    public void deleteLogFile_NameNotIn(String[] fileNameLike){
        File directory = new File(logFile.getParent());
        File[] files = directory.listFiles();
        List<String> namesToKeep = Arrays.asList(fileNameLike);
        int count = 0;
        for (int i = 0; i < files.length; i++)
        {
            if(!namesToKeep.contains(getLogFileDate(files[i].getName()))){
                //Log.e("deleteLogFile","deleting "+files[i].getName());
                files[i].delete();
                count++;
            }
        }
        //Log.e("deleteLogFile",count+" files deleted");
    }

    public String getLogFileDate(String fileName){
        return fileName.replace(LOG_FILE_PREFIX,"").replace(".txt","");
    }

    public void deleteAllLogFile(){
        File directory = new File(logFile.getParent());
        File[] files = directory.listFiles();
        //Log.e("deleteAllLogFile", "Size: "+ files.length);
        for (int i = 0; i < files.length; i++)
        {
            //Log.e("deleteAllLogFile", "FileName:" + files[i].getName());
            files[i].delete();
        }
    }

    public void deleteOldLogs(int pastDaysToKeep){
        if(pastDaysToKeep == -1)
            pastDaysToKeep = PAST_DAYS_LOGS_TO_KEEP;

        Calendar calendar = Calendar.getInstance();
        Calendar temp = Calendar.getInstance();
        String[] daysToKeep = new String[pastDaysToKeep];
        for(int i=0; i<pastDaysToKeep; i++){
            temp.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH) - i );
            daysToKeep[i] = Utils.getDate(temp.getTimeInMillis(),LOG_FILE_NAME_FORMAT);
        }
        deleteLogFile_NameNotIn(daysToKeep);
    }
    //</editor-fold>

    //<editor-fold desc="Log Write Formatting methods">
    private String convertToLogTime(long time) {
        try {
            dummyDateObj.setTime(time);
            return logDateTimeFormat.format(dummyDateObj);
        } catch (Exception ex) {
            return "";
        }
    }

    private String formatLogText(String logText, LOG_LEVEL logLevel, String componentName){
        return convertToLogTime(System.currentTimeMillis())+"/"+componentName+"/"+logLevel.name().charAt(0)+" "+logText;
    }

    private void writeLogs(File logFile, String logText) {
        try {
            if (logFile == null || !logFile.exists() || logText == null || logText.isEmpty()) {
                return;
            }
            synchronized (lock) {
                BufferedWriter buf = null;
                FileOutputStream fos = null;
                if (megabytesAvailable(logFile) > 5) {
                    try {
                        //BufferedWriter for performance, true to set append to file flag
                        //try printwriter with newline() //FileChannel filelock
                        //Log.e("writeLogs",logText);
                        buf = new BufferedWriter(new FileWriter(logFile, true)); //new BufferedWriter(new OutputStreamWriter(fos));//

                        buf.append(logText);
                        buf.append("\r\n");
                        //buf.newLine();
                    } catch (Exception e) {
                        //Log.e("writeLog", "e", e);
                    } finally {
                        buf.flush();
                        buf.close();
                    }
                }else{
                    //TODO write in log that memory is insufficient
                }
            }
            //printLogFileContent();
        } catch (Exception ex) {
            //Log.e("writeLogs1","E",ex);
        }

    }

    public static float megabytesAvailable(File f) {
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = 0;
        if (android.os.Build.VERSION.SDK_INT >= 18)
            bytesAvailable = (long) stat.getBlockSizeLong() * (long) stat.getAvailableBlocksLong();
        else
            bytesAvailable = (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        float availableMB = bytesAvailable / (1024.f * 1024.f);
        //Log.e("availableMB",availableMB+":"+f.getAbsolutePath());
        return availableMB;
    }
    //</editor-fold>

    public void logFWFinish(){
        /** do all resource clean up here **/
        //Log.e("LogFWFinish","Called");
        flush();
        //TODO use handler to wait for 3 seconds before shutting down. If getInstance called again cancel the handler.
        shutdownHandler.postDelayed(shutdownRunnable,5000);
        //logHelper = null;
    }

    public void flush(){
        if(llPendingLogs.size() > 0)
            writePendingLogs();
    }

    public void flushToSP(){
        StringBuilder sb = new StringBuilder();
        while(llPendingLogs.size()>0){
            sb.append(llPendingLogs.poll());
            sb.append("\n");
        }
        getLogSharedPref().edit().putString(SHARED_PREF_KEY_PENDING_LOG,sb.toString()).commit();
    }

    public void pause(){
        flush();
        shutdownHandler.postDelayed(shutdownRunnable,5000);
    }

    public void resume(){
        //flushPreviousSessionLogs();
        if(shutdownHandler != null)
            shutdownHandler.removeCallbacks(shutdownRunnable);
    }

    public String getTempDir(Context context){
        return context.getCacheDir()+"/.logtemp";
    }

    public void handleCrash(Context context, Throwable exception, String crashId){
        LogHelper.getLogHelper(context).flushToSP();
        String pendingLogs = LogHelper.getLogHelper(application).getLogSharedPref().getString(LogHelper.SHARED_PREF_KEY_PENDING_LOG,"");
        LogHelper.getLogHelper(application).getLogSharedPref().edit().putString(
                LogHelper.SHARED_PREF_KEY_PENDING_LOG,pendingLogs+"crashId->"+crashId+":\n"+
                        formatLogText(Log.getStackTraceString(exception),
                                LOG_LEVEL.CRITICAL,context.getClass().getSimpleName())).commit();
    }
}

