package nz.org.cacophony.cacophonometerlite;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.luckycatlabs.SunriseSunsetCalculator;
import com.luckycatlabs.dto.Location;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.android.BasicLogcatConfigurator;

import static android.content.Context.ALARM_SERVICE;

//import android.util.Log;
//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.Logger;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import ch.qos.logback.classic.Logger;


class Util {
    private static final String TAG = Util.class.getName();

    private static final String DEFAULT_RECORDINGS_FOLDER = "recordings";
    private static final String DEFAULT_LOGS_FOLDER = "logs";

    private static boolean logbackConfigured = false;
//    private static Logger logger = null;

    static {
        BasicLogcatConfigurator.configureDefaultContext();
    }

    // For airplane mode
    private final static String COMMAND_FLIGHT_MODE_1 = "settings put global airplane_mode_on";
    private final static String COMMAND_FLIGHT_MODE_2 = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state";


    // --Commented out by Inspection (12-Jun-17 2:21 PM):private final static String COMMAND_FLIGHT_MODE_3 = "settings put global airplane_mode_on";

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean checkPermissionsForRecording(Context context) {
        boolean permissionForRecording = false;
        try{
            if (context == null) {
                Log.e(TAG, "Context was null when checking permissions");
//                logger.error("Context was null when checking permissions");
            }else{
                boolean storagePermission =
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                boolean microphonePermission =
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                boolean locationPermission =
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                permissionForRecording = (storagePermission && microphonePermission && locationPermission);
            }

        }catch (Exception ex){
            Log.e(TAG, "Error with checkPermissionsForRecording");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error with checkPermissionsForRecording");
//            logger.error("Error with checkPermissionsForRecording");
        }
      return permissionForRecording;

    }

    private static File getAppDataFolder(Context context) {
    //    logger.info("getAppDataFolder method called");
        // 15/8/16 Tim Hunt - Going to change file storage location to always use internal phone storage rather than rely on sdcard
        // This is because if sdcard is 'dodgy' can get inconsistent results.
        // Need context to get internal storage location, so need to pass this around the code.
        // If I could be sure the homeFile was set before any of the other directories are needed then I
        // wouldn't need to pass context around to the other methods :-(

//        String[] storageDirectories = getStorageDirectories(context);
//        for (String storageDirectory : storageDirectories) {
//            Log.e(TAG, storageDirectory);
//        }

        File appDataFolder = null;
        String sDCardAvailable = isRemovableSDCardAvailable( context);
        if (sDCardAvailable == null){
            Log.i(TAG, "No sd card detected");
        }else{
            Log.i(TAG, "sd card IS detected");
        }


        String canCreateFile = null;
        if (sDCardAvailable != null){
            canCreateFile = canCreateFile(sDCardAvailable);
        }




        //https://developer.android.com/reference/android/content/Context.html#getDir(java.lang.String, int)
        //File appDataFolder = null;
        try {
            String appName = context.getResources().getString(R.string.main_activity_name);

            if (canCreateFile != null){
                // Use the sdcard
                appDataFolder = new File(canCreateFile + "/" + appName);
                return appDataFolder;
            }

            String externalStorageState = Environment.getExternalStorageState();
            if (externalStorageState.equalsIgnoreCase("mounted")){
                appDataFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + appName);
            }else{
                appDataFolder = context.getFilesDir();

            }


            if (appDataFolder == null) {
//                Log.e(LOG_TAG, "HomeFile location problem");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "HomeFile location problem");

            } else {
                if (!appDataFolder.exists()){
                 boolean appDataFolderCreated =   appDataFolder.mkdirs();
                    if (!appDataFolderCreated){
                 //       logger.error("Could not create AppDataFolder");

                        return null;
                    }
                    // check it got created
                    if (!appDataFolder.exists()){

                 //       logger.error("Could not create AppDataFolder");
                        return null;
                    }
                }



//                if (!homeFile.exists() && !homeFile.isDirectory() && !homeFile.mkdirs()) {
////                    Log.e(LOG_TAG, "HomeFile location problem");
////                    Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "HomeFile location problem");
//                    logger.error("HomeFile location problem");
//                }
            }
        } catch (Exception ex) {
//            Log.e(LOG_TAG, "HomeFile location problem");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "HomeFile location problem");
          //  logger.error(ex.getLocalizedMessage());
          //  logger.error("AppDataFolder problem");
        }
        return appDataFolder;
    }


    static File getRecordingsFolder(Context context){
        return getLocalFolder(context, DEFAULT_RECORDINGS_FOLDER);
    }

    static File getLogFolder(Context context){
        return getLocalFolder(context, DEFAULT_LOGS_FOLDER);
    }

    static File getLocalFolder(Context context, String localFolderStr) {

        File localFolderFile = null;
        try {

            File appDataFolder = getAppDataFolder(context);
            if (appDataFolder == null){
            //    logger.error("appDataFolder is null");
                return null;
            }
          //  localFolderFile = new File(getAppDataFolder(context), localFolderStr);
            localFolderFile = new File(appDataFolder, localFolderStr);
            if (localFolderFile == null) {
//                Log.e(LOG_TAG, "Folder location is null");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Folder location is null");
            //    logger.error("Folder location is null");
            } else {
                    if (!localFolderFile.exists()){
                        localFolderFile.mkdirs();

                        // now check it exists
                         if (!localFolderFile.exists()){
                   //         logger.error("Folder location problem");
                            localFolderFile = null;
                        }
                    }

                    // now check it is there


                   // !localFolderFile.exists() && !localFolderFile.isDirectory() && !localFolderFile.mkdirs())
              //  {
//                Log.e(LOG_TAG, "Folder location problem");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Folder location problem");

              //  }
            }
            if (localFolderFile == null){
                Log.e(TAG, "There is a problem writing to the memory - please fix");
                getToast(context, "There is a problem writing to the memory - please fix", true).show();
            }

            return localFolderFile;
        } catch (Exception ex) {
            Log.e(TAG, ex.getLocalizedMessage());
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, ex.getLocalizedMessage());
        //    logger.error(ex.getLocalizedMessage());
            getToast(context, "There is a problem writing to the memory - please fix", true).show();
            return null;
        }
    }



//    static String getLocalLogStr(Context context){
////        Date date = new Date(); // your date
////        Calendar cal = Calendar.getInstance();
////        cal.setTime(date);
////        int year = cal.get(Calendar.YEAR);
////        int month = cal.get(Calendar.MONTH);
////        int day = cal.get(Calendar.DAY_OF_MONTH);
////        String todaysLogFileNameStr = Integer.toString(year) + "_" + Integer.toString(month + 1)+ "_" + Integer.toString(day);
//        try {
//            return getLogFolder(context).getAbsolutePath() + "/" + "log" + ".txt";
//        }catch (Exception ex){
//            return null;
//        }
//
//    }

//static Logger getAndConfigureLogger(Context context, String callingLogTag){
////    if (!logbackConfigured){
////        Util.configureLogbackDirectly(context);
////        logbackConfigured = true;
////        // And log that this happened!
////        //getAndConfigureLogger(context, LOG_TAG);
////        LoggerFactory.getLogger(LOG_TAG).info("Configured Logger");
////    }
//
//    Logger logger = (Logger)LoggerFactory.getLogger(callingLogTag);
//
//    Prefs prefs = new Prefs(context, logger); // needed this constructor to fix loop of Prefs calling Util calling Prefs
//    // https://stackoverflow.com/questions/3837801/how-to-change-root-logging-level-programmatically
//    if (prefs.getUseFullLogging()){
//        if (!logbackConfigured){
//           if (!Util.configureLogbackDirectly(context, true)){
//               return null;
//           }
//            LoggerFactory.getLogger(LOG_TAG).info("Configured Logger");
//            logbackConfigured = true;
//        }
//        logger.setLevel(Level.DEBUG);
//    }else {
//        if (!logbackConfigured){
//           if( !Util.configureLogbackDirectly(context, false)){
//               return null;
//           }
//            LoggerFactory.getLogger(LOG_TAG).info("Configured Logger");
//            logbackConfigured = true;
//        }
//        logger.setLevel(Level.ERROR);
//    }
//    setLogger(logger);
//    return logger;
//
//}

//    static void writeLocalLogEntryUsingLogback(Context context, Logger callingLogger, String message){
//        if (logger == null){
//            if (!logbackConfigured){
//                if (context != null){ // can only configure if context not null
//                    Util.configureLogbackDirectly(context);
//                    logbackConfigured = true;
//                    logger = LoggerFactory.getLogger(LOG_TAG);
//                    logger.info("Configured Logback");
//                }
//            }
//
//        }else{
//            logger.info(message);
//        }
//
//
//
//
//        if (context != null){
//            if (!logbackConfigured){
//                Util.configureLogbackDirectly(context);
//                logbackConfigured = true;
//
//
//            }
//        }else{
//            logger = LoggerFactory.getLogger(callingLogTag);
//        }
//
//
//
//
//    }

//    static void writeLocalLogEntryUsingLogback(Context context, String LOG_TAG, String message){
//        Prefs prefs = new Prefs(context);
//        if (!prefs.isLocalLog()){
//            return;
//        }
//        // https://stackoverflow.com/questions/8210616/printwriter-append-method-not-appending
//        String todaysLocalLogStr = getLocalLogStr(context);
//        PrintWriter out = null;
//        try {
//            out = new PrintWriter(new BufferedWriter(new FileWriter(todaysLocalLogStr, true)));
//            out.println(LOG_TAG);
//            out.println(" ");
//            out.println(message);
//            out.println(" ");
//        }catch (IOException e) {
////            Log.e(LOG_TAG, e.getLocalizedMessage());
//            logger.error(e.getLocalizedMessage());
//        }finally{
//            if(out != null){
//                out.close();
//            }
//        }
//    }



    static String getDeviceID(Context context, String webToken) throws Exception {
        if (webToken == null){
            return "";
        }

        String webTokenBody = Util.decoded(context, webToken);
        JSONObject jObject = new JSONObject(webTokenBody);
        return jObject.getString("id");
    }

    private static String decoded(Context context, String JWTEncoded) {
        // http://stackoverflow.com/questions/37695877/how-can-i-decode-jwt-token-in-android#38751017
        String webTokenBody = null;
        try {
            String[] split = JWTEncoded.split("\\.");
          //  Log.d("JWT_DECODED", "Header: " + getJson(split[0]));

            // Log.d("JWT_DECODED", "Body: " + getJson(split[1]));
            webTokenBody = getJson(split[1]);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error decoding JWT");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error decoding JWT");
//            logger.error("Error decoding JWT");
        }
        return webTokenBody;
    }

    private static String getJson(String strEncoded) throws UnsupportedEncodingException {
        byte[] decodedBytes = Base64.decode(strEncoded, Base64.URL_SAFE);
        return new String(decodedBytes, "UTF-8");
    }

    static double getBatteryLevel(Context context) {
        double batteryLevel;
        batteryLevel = getBatteryLevelUsingSystemFile(context);
        if (batteryLevel == -1) {
            batteryLevel = getBatteryLevelByIntent(context);

        }
        return batteryLevel;
    }

    static double getBatteryLevelUsingSystemFile(Context context) {

        // https://longtrieuquang.wordpress.com/2013/04/08/android-battery-information-from-file-system/
        // found the file volt that stores battery voltage
        String batteryLevelStr = null;
        double batteryLevel = -1;
        String voltFilePathName = "/sys/class/power_supply/battery/volt";
        File voltFile = new File(voltFilePathName);
        if (voltFile.exists()) {
            try {
                batteryLevelStr = getStringFromFile(voltFilePathName);
            } catch (Exception ex) {
                ex.printStackTrace();
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, ex.getLocalizedMessage());
//                logger.error( ex.getLocalizedMessage());
                Log.e(TAG, ex.getLocalizedMessage());
            }

        }

        if (batteryLevelStr != null) {
            try {
                batteryLevel = Double.parseDouble(batteryLevelStr);
            } catch (Exception ex) {
                Log.e(TAG, "converting double");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "converting double");
//                logger.error("converting double");
            }
        }
        return batteryLevel;
    }

    static double getBatteryLevelByIntent(Context context) {

        //Will use the method of checking battery level that may only give an update if phone charging status changes
        // this will return a percentage
        try {
            double batteryLevel;
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.getApplicationContext().registerReceiver(null, ifilter);
            batteryLevel = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            return batteryLevel;
        } catch (Exception ex) {
            Log.e(TAG, "Error with getBatteryLevelByIntent");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error with getBatteryLevelByIntent");
//            logger.error("Error with getBatteryLevelByIntent");
            return -1;
        }

    }

    private static String getStringFromFile(String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);

        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    private static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        //  StringBuilder sb = new StringBuilder();
        String line;

        line = reader.readLine();
        reader.close();
        return line;
    }

    static String getBatteryStatus(Context context) {
        // https://developer.android.com/training/monitoring-device-state/battery-monitoring.html
        // http://stackoverflow.com/questions/24934260/intentreceiver-components-are-not-allowed-to-register-to-receive-intents-when

        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.getApplicationContext().registerReceiver(null, ifilter);
            // Are we charging / charged?
            int status = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;

            String batteryStatusToReturn;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    batteryStatusToReturn = "CHARGING";
                    break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    batteryStatusToReturn = "DISCHARGING";
                    break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    batteryStatusToReturn = "NOT_CHARGING";
                    break;
                case BatteryManager.BATTERY_STATUS_FULL:
                    batteryStatusToReturn = "FULL";
                    break;
                case BatteryManager.BATTERY_STATUS_UNKNOWN:
                    batteryStatusToReturn = "UNKNOWN";
                    break;
                default:
                    batteryStatusToReturn = Integer.toString(status);
            }
            return batteryStatusToReturn;
        } catch (Exception ex) {
            Log.e(TAG, "getBatteryStatus");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "getBatteryStatus");
//            logger.error("getBatteryStatus");
            return "Error";
        }

    }

    /**
     * Gets the state of Airplane Mode.
     * http://stackoverflow.com/questions/4319212/how-can-one-detect-airplane-mode-on-android
     *
     * @return true if enabled.
     */
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    static boolean isAirplaneModeOn(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
    }


    /**
     * Returns the sunrise time for the current device location
     *
     * @param context - for getting the location
     * @return Calendar time of the sunrise
     */
    private static Calendar getSunrise(Context context, Calendar todayOrTomorrow) {

        Location location = getLocation(context);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, "Pacific/Auckland");
        //Log.d("DEBUG: ", "Sunrise time is: " + officialSunrise);
        return calculator.getOfficialSunriseCalendarForDate(todayOrTomorrow);
    }

    static Calendar getDawn(Context context, Calendar todayOrTomorrow) {
        Prefs prefs = new Prefs(context);
        Calendar sunRise = getSunrise(context, todayOrTomorrow);
        Calendar dawn = (Calendar) sunRise.clone();
        int lengthOfTwilight = (int) prefs.getLengthOfTwilightSeconds();
        dawn.add(Calendar.SECOND, -lengthOfTwilight);
        return dawn;
    }

    /**
     * Returns the sunset time for the current device location
     *
     * @param context - for getting the location
     * @return Calendar time of the sunset
     */
    private static Calendar getSunset(Context context, Calendar todayOrTomorrow) {

        Location location = getLocation(context);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, "Pacific/Auckland");

        // Log.d("DEBUG: ", "Sunset time is: " + officialSunset);
        return calculator.getOfficialSunsetCalendarForDate(todayOrTomorrow);
    }

    static Calendar getDusk(Context context, Calendar todayOrTomorrow) {
        Prefs prefs = new Prefs(context);
        Calendar sunSet = getSunset(context, todayOrTomorrow);
        Calendar dusk = (Calendar) sunSet.clone();
        int lengthOfTwilight = (int) prefs.getLengthOfTwilightSeconds();
        dusk.add(Calendar.SECOND, lengthOfTwilight);
        return dusk;
    }

    private static Location getLocation(Context context) {
        Prefs prefs = new Prefs(context);
        String lat;
        String lon;

        if (prefs.getLatitude() == 0.0 && prefs.getLongitude() == 0.0) {
            // gps not yet set, so to avoid errors/too complex code to check, just use coordinates for Hamilton NZ
            lat = Double.toString(-37.805294);
            lon = Double.toString(175.306775);

        } else {
            lat = Double.toString(prefs.getLatitude());
            lon = Double.toString(prefs.getLongitude());
        }


        return new Location(lat, lon);
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean waitForNetworkConnection(Context context, boolean networkConnectionRequired) {
        int numberOfLoops = 0;

        while (isNetworkConnected(context) != networkConnectionRequired) {

            try {
                Thread.sleep(1000); // give time for airplane mode to turn off
            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, ex.getLocalizedMessage());
//                logger.error( ex.getLocalizedMessage());
                Log.e(TAG,ex.getLocalizedMessage() );
            }

            numberOfLoops += 1;
            if (numberOfLoops > 60) {
                Log.e(TAG, "Number of loops > 60");
//                logger.error("Number of loops > 60");
                break;
            }
        }
        //noinspection RedundantIfStatement
        if (numberOfLoops > 60) {
            return false;
        }
        return true;
    }


    static boolean isNetworkConnected(Context context) {
//        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//        boolean isConnected = activeNetwork != null &&
//                activeNetwork.isConnectedOrConnecting();
//        return cm.getActiveNetworkInfo() != null;

        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        return isConnected;
    }



    static String disableFlightMode(Context context) {
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                // API 17 onwards.
                // Log.d(LOG_TAG, "Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN");
                // Must be a rooted device
                Prefs prefs = new Prefs(context);


              //  if (!prefs.getHasRootAccess() && !prefs.getOffLineMode()) {
                if (!prefs.getHasRootAccess()) {  // don't try to disable flight mode if phone has been rooted.
                    return null;
                }

                if (prefs.getOffLineMode()) {  // Don't try to turn on aerial if set to be offline
                    return null;
                }

////                    logger.info("Do NOT have required ROOT access");
//                    Log.w(TAG, "Do NOT have required ROOT access");
//                 //   Util.getToast(context, "Root access required to change airplane mode", true).show();
//                //    Util.getToast(context, "To save power the Cacophonometer is designed to automatically switch airplane mode on/off but the version of Android on this phone prevents this unless the phone has been ‘rooted’.  You can disregard this message if the phone is plugged into the mains power – or see the website for more details. ", false).show();
//                        Util.broadcastAMessage(context,  "can_not_toggle_airplane_mode");
//                        return null;

            //    }

                // Set Airplane / Flight mode using su commands.
                String command = COMMAND_FLIGHT_MODE_1 + " " + "0";
               // executeCommandWithoutWait("-c", command);
                executeCommandTim(context, command);
                command = COMMAND_FLIGHT_MODE_2 + " " + "false";
              //  executeCommandWithoutWait("-c", command);
                executeCommandTim(context, command);

            } else {
                // API 16 and earlier.
                // Log.d(LOG_TAG, "API 16 and earlier.");
                //  boolean enabled = isFlightModeEnabled(context);
                //noinspection deprecation
                Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", false);
                context.sendBroadcast(intent);
            }

        }catch (Exception ex){
//            logger.error(ex.getLocalizedMessage());
            Log.e(TAG, ex.getLocalizedMessage());

        }
        return null;
    }




    static void enableFlightMode(Context context) {
        Prefs prefs = new Prefs(context);

        boolean onlineMode = prefs.getOnLineMode();
        String mode = prefs.getMode();

        switch(mode) { // mode determined earlier
            case "off":
                // don't change offline mode
                break;
            case "normal":
                // don't change offline mode
                break;

            case "normalOnline":
                onlineMode = true;
                break;

            case "walking":
                // don't change offline mode
                break;
        }

        if (onlineMode){
            return; // don't try to enable airplane mode
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) { // Jelly bean is 4.1
//            Log.d(TAG, "enableFlightMode 2");
            // API 17 onwards.

            // Must be a rooted device

            if (!prefs.getHasRootAccess()) {

                Log.e(TAG, "Do NOT have required ROOT access");

                return;
            }


            // Set Airplane / Flight mode using su commands.
            String command = COMMAND_FLIGHT_MODE_1 + " " + "1";
         //   executeCommandWithoutWait("-c", command);
            executeCommandTim(context, command);
            command = COMMAND_FLIGHT_MODE_2 + " " + "true";
        //    executeCommandWithoutWait("-c", command);
            executeCommandTim(context, command);

        } else {

            //noinspection deprecation
            Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 1);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", true);
            context.sendBroadcast(intent);
        }
    }



private static void executeCommandTim(Context context, String command){
    try {
        ExecuteAsRootBaseTim executeAsRootBaseTim = new ExecuteAsRootBaseTim();
        executeAsRootBaseTim.addCommand(command);
        executeAsRootBaseTim.execute(context);
    }catch (Exception ex){
        Log.e(TAG, ex.getLocalizedMessage());
        Util.broadcastAMessage(context, "error_do_not_have_root");
    }
}


//    private static void executeCommandWithoutWait(@SuppressWarnings("SameParameterValue") String option, String command) {
//
//        // http://muzso.hu/2014/04/02/how-to-programmatically-enable-and-disable-airplane-flight-mode-on-android-4.2
//        // http://stackoverflow.com/questions/23537467/enable-airplane-mode-on-all-api-levels-programmatically-android
//        boolean success = false;
//        String su = "su";
//        for (int i = 0; i < 3; i++) {
////            Log.d(TAG, "executeCommandWithoutWait 2");
//            // "su" command executed successfully.
//            if (success) {
//                // Stop executing alternative su commands below.
////                Log.d(TAG, "executeCommandWithoutWait 3");
//                break;
//            }
//            if (i == 1) {
////                Log.d(TAG, "executeCommandWithoutWait 4");
//                su = "/system/xbin/su";
//            } else if (i == 2) {
////                Log.d(TAG, "executeCommandWithoutWait 5");
//                su = "/system/bin/su";
//            }
//            try {
////                Log.d(TAG, "executeCommandWithoutWait 6");
//                // execute command
//                Runtime.getRuntime().exec(new String[]{su, option, command});
//                success = true;
//
//            } catch (IOException e) {
////                Log.d(TAG, "executeCommandWithoutWait 7");
//
//                success = false;
//            }
//        }
//    }

    static String getSimStateAsString(int simState) {
        String simStateStr;
        switch (simState) {
            case 0:
                simStateStr = "SIM_STATE_UNKNOWN";
                break;
            case 1:
                simStateStr = "SIM_STATE_ABSENT";
                break;
            case 2:
                simStateStr = "SIM_STATE_PIN_REQUIRED";
                break;
            case 3:
                simStateStr = "SIM_STATE_PUK_REQUIRED";
                break;
            case 4:
                simStateStr = "SIM_STATE_NETWORK_LOCKED";
                break;
            case 5:
                simStateStr = "SIM_STATE_READY";
                break;
            case 6:
                simStateStr = "SIM_STATE_NOT_READY";
                break;
            case 7:
                simStateStr = "SIM_STATE_PERM_DISABLED";
                break;
            case 8:
                simStateStr = "SIM_STATE_CARD_IO_ERROR";
                break;
            case 9:
                simStateStr = "SIM_STATE_CARD_RESTRICTED";
                break;
            case 10:
                simStateStr = "October";
                break;
            case 11:
                simStateStr = "November";
                break;
            case 12:
                simStateStr = "December";
                break;
            default:
                simStateStr = "noMatch";
                break;
        }
        return simStateStr;
    }


    static Toast getToast(Context context, String message, boolean standOut){

        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        if (standOut){
            toast.getView().setBackgroundColor(context.getResources().getColor(R.color.colorAccent));
        }else{
            toast.getView().setBackgroundColor(context.getResources().getColor(R.color.colorGreen));
        }

        return toast;
    }



    static void broadcastAMessage(Context context, String message){
        // https://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager

        Intent intent = new Intent("event");
        // You can also include some extra data.
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

    }



//https://stackoverflow.com/questions/5694933/find-an-external-sd-card-location/29107397#29107397
   static  public String isRemovableSDCardAvailable(Context context) {
        final String FLAG = "mnt";
        final String SECONDARY_STORAGE = System.getenv("SECONDARY_STORAGE");
        final String EXTERNAL_STORAGE_DOCOMO = System.getenv("EXTERNAL_STORAGE_DOCOMO");
        final String EXTERNAL_SDCARD_STORAGE = System.getenv("EXTERNAL_SDCARD_STORAGE");
        final String EXTERNAL_SD_STORAGE = System.getenv("EXTERNAL_SD_STORAGE");
        final String EXTERNAL_STORAGE = System.getenv("EXTERNAL_STORAGE");

        Map<Integer, String> listEnvironmentVariableStoreSDCardRootDirectory = new HashMap<Integer, String>();
        listEnvironmentVariableStoreSDCardRootDirectory.put(0, SECONDARY_STORAGE);
        listEnvironmentVariableStoreSDCardRootDirectory.put(1, EXTERNAL_STORAGE_DOCOMO);
        listEnvironmentVariableStoreSDCardRootDirectory.put(2, EXTERNAL_SDCARD_STORAGE);
        listEnvironmentVariableStoreSDCardRootDirectory.put(3, EXTERNAL_SD_STORAGE);
        listEnvironmentVariableStoreSDCardRootDirectory.put(4, EXTERNAL_STORAGE);

        File externalStorageList[] = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            externalStorageList = context.getExternalFilesDirs(null);
        }
        String directory = null;
        int size = listEnvironmentVariableStoreSDCardRootDirectory.size();
        for (int i = 0; i < size; i++) {
            if (externalStorageList != null && externalStorageList.length > 1 && externalStorageList[1] != null)
                directory = externalStorageList[1].getAbsolutePath();
            else
                directory = listEnvironmentVariableStoreSDCardRootDirectory.get(i);

            directory = canCreateFile(directory);
            if (directory != null && directory.length() != 0) {
                if (i == size - 1) {
                    if (directory.contains(FLAG)) {
//                        Log.i(TAG, "SD Card's directory: " + directory);
                        return directory;
                    } else {
                        return null;
                    }
                }
//                Log.i(TAG, "SD Card's directory: " + directory);
                return directory;
            }
        }
        return null;
    }


    static public String canCreateFile(String directory) {
        final String FILE_DIR = directory + File.separator + "hoang.txt";
        File tempFlie = null;
        try {
            tempFlie = new File(FILE_DIR);
            FileOutputStream fos = new FileOutputStream(tempFlie);
            fos.write(new byte[1024]);
            fos.flush();
            fos.close();
//            Log.i(TAG, "Can write file on this directory: " + FILE_DIR);
        } catch (Exception e) {
//            Log.i(TAG, "Write file error: " + e.getMessage());
            return null;
        } finally {
            if (tempFlie != null && tempFlie.exists() && tempFlie.isFile()) {
                // tempFlie.delete();
                tempFlie = null;
            }
        }
        return directory;
    }

    static boolean isWebTokenCurrent(Prefs prefs){
       if (prefs.getToken() == null){
           return false;
       }
       long currentTimeMilliSeconds = new Date().getTime();
       long tokenLastRefreshedMilliSeconds = prefs.getTokenLastRefreshed();
       long tokenTimeOutSeconds = Prefs.getTokenTimeoutSeconds();
       long tokenTimeOutMilliSeconds = tokenTimeOutSeconds * 1000;
       if ((currentTimeMilliSeconds-tokenLastRefreshedMilliSeconds) > tokenTimeOutMilliSeconds){
           prefs.setToken(null);
           Log.d(TAG, "Web token out of date and so set to null");
          // Server.loggedIn = false;
           return false;
       }else{
           return true;
       }
    }

    public static void createCreateAlarms(Context context){ // Because each alarm now creates the next one, need to have this failsafe to get them going again (it doesn't rely of a previous alarm)
       // Prefs prefs = new Prefs(context);
        Intent myIntent = new Intent(context, StartRecordingReceiver.class);
        try {
            myIntent.putExtra("type","repeating");
            Uri timeUri; // // this will hopefully allow matching of intents so when adding a new one with new time it will replace this one
            timeUri = Uri.parse("createCreateAlarms"); // cf dawn dusk offsets created in DawnDuskAlarms
            myIntent.setData(timeUri);

        }catch (Exception e){
            Log.e(TAG, e.getLocalizedMessage());

        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, myIntent,0);

        AlarmManager alarmManager = (AlarmManager)context.getSystemService(ALARM_SERVICE);


        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_DAY,pendingIntent);

    }

//    public static void createAlarms(Context context){
public static void createAlarms(Context context, String type, String timeUriParameter){
//    public static void createAlarms(Context context, String type, String timeUriParameter, String callingMethod){ // added extra parameter to debug what was calling the createalams method
        Prefs prefs = new Prefs(context);
        Intent myIntent = new Intent(context, StartRecordingReceiver.class);
    myIntent.putExtra("callingCode", "tim"); // for debugging

        try {
            myIntent.putExtra("type","repeating");
            Uri timeUri; // // this will hopefully allow matching of intents so when adding a new one with new time it will replace this one
            timeUri = Uri.parse("normal"); // cf dawn dusk offsets created in DawnDuskAlarms
            myIntent.setData(timeUri);

        }catch (Exception e){
            Log.e(TAG, e.getLocalizedMessage());

        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, myIntent,0);

        AlarmManager alarmManager = (AlarmManager)context.getSystemService(ALARM_SERVICE);

        long timeBetweenRecordingsSeconds  = (long)prefs.getAdjustedTimeBetweenRecordingsSeconds();

    String mode = prefs.getMode();
    switch(mode) {
        case "off":
            // don't change
            break;
        case "normal":
            timeBetweenRecordingsSeconds  =  (long)prefs.getNormalTimeBetweenRecordingsSeconds();
            break;
        case "normalOnline":
            timeBetweenRecordingsSeconds  =  (long)prefs.getNormalTimeBetweenRecordingsSeconds();
            break;

        case "walking":
            timeBetweenRecordingsSeconds  =  (long)prefs.getTimeBetweenFrequentRecordingsSeconds();
            break;
    }

        long delay = 1000 * timeBetweenRecordingsSeconds ;

    long currentElapsedRealTime = SystemClock.elapsedRealtime();
    long startWindowTime = currentElapsedRealTime + delay;

//     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // KitKat is 19
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) { // KitKat is 19
         // https://developer.android.com/reference/android/app/AlarmManager.html
//        long windowInMilliseconds = delay  /10 ;
     //    long windowInMilliseconds = 1000 * 20; // 20 seconds
//        long currentElapsedRealTime = SystemClock.elapsedRealtime();
//         long startWindowTime = currentElapsedRealTime + delay;
        //alarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, windowInMilliseconds, pendingIntent ); // this still allowed batching of alarms
//         alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, pendingIntent);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, pendingIntent);
    }else{
//        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
//                SystemClock.elapsedRealtime() ,
//                delay, pendingIntent);
//         alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, pendingIntent);
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, pendingIntent);
    }
//    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startWindowTime, pendingIntent);
    }




    public static void updateGPSLocation(Context context){
        ////        Log.i(LOG_TAG, "Update location button");
//     //   logger.debug("Update location button");
        Util.getToast(context,"Getting new Location...", false ).show();
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        //https://stackoverflow.com/questions/36123431/gps-service-check-to-check-if-the-gps-is-enabled-or-disabled-on-device
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Util.broadcastAMessage(context, "turn_on_gps_and_try_again");
            return;

            // take here - trying to take user to OS GPS location screen crashes app if phone not rooted (at least on OS 5.1 (22)
            // According to


//            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
////            context.startActivity(intent);
//            context.getApplicationContext().startActivity(intent);


        }


        GPSLocationListener gpsLocationListener = new GPSLocationListener(context);
        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, gpsLocationListener, context.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to get GPS location. Don't have required permissions.");
//            Util.writeLocalLogEntryUsingLogback(getApplicationContext(), LOG_TAG, "Unable to get GPS location. Don't have required permissions.");
         //   logger.error("Unable to get GPS location. Don't have required permissions.");
        }

    }

//    public static void playWarningBeeps(Context context, int durationInSeconds){
//
//            Util.getToast(context,"Prepare to start recording", false ).show();
//            ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
//            toneGen1.startTone(ToneGenerator.TONE_CDMA_NETWORK_BUSY,durationInSeconds * 1000);
//            try{
//                Thread.sleep(durationInSeconds * 1000);
//            }catch (Exception ex){
//                Log.e(TAG, ex.getLocalizedMessage());
//            }
//        }

}
