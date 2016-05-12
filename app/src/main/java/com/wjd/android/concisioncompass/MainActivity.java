package com.wjd.android.concisioncompass;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 指南针Activity
 * Created by Jundooong on 2016/05/10.
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static final String TAG = "MainActivity";
    private static final int EXIT_TIME = 2000;// 两次按返回键的间隔判断
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagneticField;
    private LocationManager mLocationManager;
    private String mLocationProvider;// 位置提供者名称，GPS设备还是网络
    private float mCurrentDegree = 0f;
    private float[] mAccelerometerValues = new float[3];
    private float[] mMagneticFieldValues = new float[3];
    private float[] mValues = new float[3];
    private float[] mMatrix = new float[9];

    private long firstExitTime = 0L;// 用来保存第一次按返回键的时间

    private TextView mTvCoord;
    private LinearLayout mLlLocation;
    private TextView mTvAltitude;
    private ImageView mIvCompass;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initService();
        findViews();
    }

    private void findViews() {
        mIvCompass = (ImageView) findViewById(R.id.iv_compass);
        mTvCoord = (TextView) findViewById(R.id.tv_coord);
        mTvAltitude = (TextView) findViewById(R.id.tv_altitude);
        mLlLocation = (LinearLayout) findViewById(R.id.ll_location);
        mLlLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initLocationService();
                updateLocationService();
            }
        });
    }

    private void initService() {
        initSensorService();

        initLocationService();
    }

    private void initSensorService() {
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void initLocationService() {
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();// 条件对象，即指定条件过滤获得LocationProvider
        criteria.setAccuracy(Criteria.ACCURACY_FINE);// 较高精度
        criteria.setAltitudeRequired(true);// 是否需要高度信息
        criteria.setBearingRequired(true);// 是否需要方向信息
        criteria.setCostAllowed(true);// 是否产生费用
        criteria.setPowerRequirement(Criteria.POWER_LOW);// 设置低电耗
        mLocationProvider = mLocationManager.getBestProvider(criteria, true);// 获取条件最好的Provider,若没有权限，mLocationProvider 为null
        Log.e(TAG, "mLocationProvider = " + mLocationProvider);
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerService();

    }

    private void registerService() {
        registerSensorService();

        updateLocationService();
    }

    private void registerSensorService() {
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void updateLocationService() {
        if (!checkLocationPermission()) {
            mTvCoord.setText(R.string.check_location_permission);
            return;
        }

        if (mLocationProvider != null) {
            updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            mLocationManager.requestLocationUpdates(mLocationProvider, 2000, 10, mLocationListener);// 2秒或者距离变化10米时更新一次地理位置
        } else {
            mTvCoord.setText(R.string.cannot_get_location);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregister();
    }

    private void unregister() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }

        if (mLocationManager != null) {
            if (!checkLocationPermission()) {
                return;
            }
            mLocationManager.removeUpdates(mLocationListener);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerValues = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagneticFieldValues = event.values;
        }

        //调用getRotaionMatrix获得变换矩阵mMatrix[]
        SensorManager.getRotationMatrix(mMatrix, null, mAccelerometerValues, mMagneticFieldValues);
        SensorManager.getOrientation(mMatrix, mValues);
        //经过SensorManager.getOrientation(R, values);得到的values值为弧度
        //values[0]  ：azimuth 方向角，但用（磁场+加速度）得到的数据范围是（-180～180）,也就是说，0表示正北，90表示正东，180/-180表示正南，-90表示正西。
        // 而直接通过方向感应器数据范围是（0～359）360/0表示正北，90表示正东，180表示正南，270表示正西。
        float degree = (float) Math.toDegrees(mValues[0]);
        setImageAnimation(degree);
        mCurrentDegree = -degree;
    }

    // 设置指南针图片的动画效果
    private void setImageAnimation(float degree) {
        RotateAnimation ra = new RotateAnimation(mCurrentDegree, -degree, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        ra.setDuration(200);
        ra.setFillAfter(true);
        mIvCompass.startAnimation(ra);
    }

    /**
     * 适配android 6.0 检查权限
     */
    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED);
        }

        return true;

    }

    /**
     * 更新位置信息
     */
    private void updateLocation(Location location) {
        Log.e(TAG, "location = " + location);
        if (null == location) {
            mTvCoord.setText(getString(R.string.cannot_get_location));
            mTvAltitude.setVisibility(View.GONE);
        } else {
            mTvAltitude.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            double altitude = location.getAltitude();
            if (latitude >= 0.0f) {
                sb.append(getString(R.string.location_north, latitude));
            } else {
                sb.append(getString(R.string.location_south, (-1.0 * latitude)));
            }

            sb.append("    ");

            if (longitude >= 0.0f) {
                sb.append(getString(R.string.location_east, longitude));
            } else {
                sb.append(getString(R.string.location_west, (-1.0 * longitude)));
            }
            mTvCoord.setText(getString(R.string.correct_coord, sb.toString()));
            mTvAltitude.setText(getString(R.string.correct_altitude, altitude));
        }

    }


    LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status != LocationProvider.OUT_OF_SERVICE) {
                if (!checkLocationPermission()) {
                    mTvCoord.setText(R.string.check_location_permission);
                    return;
                }
                updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            } else {
                mTvCoord.setText(R.string.check_location_permission);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onBackPressed() {
        long curTime = System.currentTimeMillis();
        if (curTime - firstExitTime < EXIT_TIME) {
            finish();
        } else {
            Toast.makeText(this, R.string.exit_toast, Toast.LENGTH_SHORT).show();
            firstExitTime = curTime;
        }

    }
}
