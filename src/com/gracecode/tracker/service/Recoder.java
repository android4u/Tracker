package com.gracecode.tracker.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import com.gracecode.tracker.R;
import com.gracecode.tracker.activity.Preference;
import com.gracecode.tracker.dao.Archive;
import com.gracecode.tracker.dao.ArchiveMeta;
import com.gracecode.tracker.util.Logger;
import com.gracecode.tracker.util.UIHelper;

import java.io.File;
import java.util.Date;

/**
 *
 */
interface Binder {
    public static final int STATUS_RUNNING = 0x0000;
    public static final int STATUS_STOPPED = 0x1111;

    public void startRecord();

    public void stopRecord();

    public int getStatus();

    public ArchiveMeta getArchiveMeta();

    public Archive getArchive();

    public Location getLastRecord();
}

public class Recoder extends Service {
    protected Recoder.ServiceBinder serviceBinder;
    private SharedPreferences sharedPreferences;
    private Archive geoArchive;

    private Listener listener;
    private LocationManager locationManager;

    private ArchiveNameHelper archiveFileNameHelper;
    private String archiveFileName;
    private UIHelper uiHelper;
    private Context context;

    public class ServiceBinder extends android.os.Binder implements Binder {
        private int status = ServiceBinder.STATUS_STOPPED;
        private ArchiveMeta meta = null;

        ServiceBinder() {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            geoArchive = new Archive(getApplicationContext());
            listener = new Listener(geoArchive);
        }

        @Override
        public void startRecord() {
            if (status != ServiceBinder.STATUS_RUNNING) {
                // 从配置文件获取距离和精度选项
                long minTime = Long.parseLong(sharedPreferences.getString(Preference.GPS_MINTIME,
                    Preference.DEFAULT_GPS_MINTIME));
                float minDistance = Float.parseFloat(sharedPreferences.getString(Preference.GPS_MINDISTANCE,
                    Preference.DEFAULT_GPS_MINDISTANCE));

                if (archiveFileNameHelper.hasResumeArchiveFile()) {
                    archiveFileName = archiveFileNameHelper.getResumeArchiveFileName();
                    uiHelper.showLongToast(
                        String.format(
                            getString(R.string.use_resume_archive_file, archiveFileName)
                        ));
                } else {
                    archiveFileName = archiveFileNameHelper.getNewArchiveFileName();
                }

                try {
                    geoArchive.open(archiveFileName);
                    archiveFileNameHelper.setLastOpenedArchiveFileName(archiveFileName);

                    // 获取 Meta 信息
                    meta = getArchiveMeta();

                    // 设置开始时间
                    meta.setStartTime(new Date());

                    // 绑定 GPS 回调
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        minTime, minDistance, listener);
                } catch (SQLiteException e) {
                    Logger.e(e.getMessage());
                }

                status = ServiceBinder.STATUS_RUNNING;
            }
        }

        @Override
        public void stopRecord() {
            if (status == ServiceBinder.STATUS_RUNNING) {
                locationManager.removeUpdates(listener);

                long totalCount = getArchiveMeta().getCount();
                if (totalCount <= 0) {
                    (new File(archiveFileName)).delete();
                    uiHelper.showLongToast(getString(R.string.not_record_anything));
                } else {
                    // 设置结束记录时间
                    meta.setEndTime(new Date());

                    uiHelper.showLongToast(String.format(
                        getString(R.string.result_report), String.valueOf(totalCount)
                    ));
                }

                // 清除操作
                geoArchive.close();
                meta = null;

                archiveFileNameHelper.clearLastOpenedArchiveFileName();
                status = ServiceBinder.STATUS_STOPPED;
            }
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public ArchiveMeta getArchiveMeta() {
            return geoArchive.getArchiveMeta();
        }

        @Override
        public Archive getArchive() {
            return geoArchive;
        }

        @Override
        public Location getLastRecord() {
            return geoArchive.getLastRecord();
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        this.context = getApplicationContext();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.archiveFileNameHelper = new ArchiveNameHelper(context);
        this.uiHelper = new UIHelper(context);
        this.serviceBinder = new ServiceBinder();

        boolean autoStart = sharedPreferences.getBoolean(Preference.AUTO_START, false);
        if (autoStart) {
            serviceBinder.startRecord();
        }
    }


    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        serviceBinder.stopRecord();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }
}
