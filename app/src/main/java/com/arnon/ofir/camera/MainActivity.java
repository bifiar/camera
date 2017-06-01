package com.arnon.ofir.camera;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;


public class MainActivity extends AppCompatActivity {
    private Camera mCamera;
    private Button button;
    private Switch toggleButtonAutomaticallyTakePic;
    private boolean uploadToServer = false;
    Switch toggleButtonAutomaticallySendSMS;
    TextView altitude, latitude, longitude;

    //For uploading:
    private Bitmap bitmap;
    private String picPath;

    private int PICK_IMAGE_REQUEST = 1;

    private String UPLOAD_URL = "https://ofircamera.000webhostapp.com/picture/upload.php";

    private String KEY_IMAGE = "image";
    private String KEY_NAME = "name";


    private CameraPreview mCameraPreview;

    private int PICtimeOut = 30000; // 30 sec
    private int SMStimeOut = 30000; // 30 sec
    private int widthResulution = 640;
    private int heightResulution = 480;
    private int compressQuality = 10;    //3-100, 80 gives pic on 4 kb, its the best compress without loose high quality

    //GPS:
    private LocationManager locationManager;
    private LocationListener locationListener;
    private static final int minTime = 10000; //min time in milliseconds to show new gps single
    private static final int minDistance = 0; //min distance (in meters), to show new gps single
    private double Altitude = 0, Latitude = 0, Longitude = 0;

    //SMS:
    String destNum = "0529245293";

    //Background Threads:

    Handler handlerSMS = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("sendToSMS() ", "-------------------");
            sendSms();
        }
    };
    private boolean sendSMS = false;

    Runnable runnableSMS = new Runnable() {
        @Override
        public void run() {
            //while(true)
            {
                synchronized (this) {
                    while (sendSMS) {
                        try {
                            wait(SMStimeOut);
                            handlerSMS.sendEmptyMessage(0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }
    };
    Thread tSMS = new Thread(runnableSMS);


    android.os.Handler handler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                Log.d("CAMERA ", "take pic !");
                mCamera.startPreview(); //important! it allow to take multi pics!
                mCamera.takePicture(null, null, mPicture);
                //Toast.makeText(getApplicationContext(), "Test!!!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };
    private boolean send = false;

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            //while(true)
            {
                synchronized (this) {
                    while (send) {
                        try {
                            wait(PICtimeOut);
                            handler.sendEmptyMessage(0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }
    };
    Thread t = new Thread(runnable);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCamera = getCameraInstance();
        mCameraPreview = new CameraPreview(this, mCamera);
        final FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mCameraPreview);
        button = (Button) findViewById(R.id.button_capture);
        toggleButtonAutomaticallyTakePic = (Switch) findViewById(R.id.button_automatically_send_data);
        toggleButtonAutomaticallyTakePic.setBackgroundColor(Color.GREEN);
        altitude = (TextView) findViewById(R.id.altitude);
        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);
        toggleButtonAutomaticallySendSMS = (Switch) findViewById(R.id.button_automatically_send_SMS);
        toggleButtonAutomaticallySendSMS.setBackgroundColor(Color.GREEN);

        onClickButtonAutomaticallySendSMS();
        turnOnGps();


        setCameraParameters(widthResulution, heightResulution);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCamera == null) {
                    Toast.makeText(getApplicationContext(), "Camera is null !", Toast.LENGTH_LONG).show();
                } else {
                    try {
                        mCamera.startPreview(); //important! it allow to take multi pics!
                        mCamera.takePicture(null, null, mPicture);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        toggleButtonAutomaticallyTakePic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean on) {

                if (on) {
                    //Do something when Switch button is on/checked
                    toggleButtonAutomaticallyTakePic.setText("STOP TAKE PIC !");
                    toggleButtonAutomaticallyTakePic.setBackgroundColor(Color.RED);
                    send = true;
                    Thread t = new Thread(runnable);
                    t.start();

                } else {
                    //Do something when Switch is off/unchecked
                    toggleButtonAutomaticallyTakePic.setText("AUTOMATICALLY TAKE PIC");
                    toggleButtonAutomaticallyTakePic.setBackgroundColor(Color.GREEN);
                    send = false;
                    t.interrupt();


                }
            }
        });


    }

    /**
     * Helper method to access the camera returns null if it cannot get the
     * camera or does not exist
     *
     * @return
     */
    private Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            // cannot get camera or does not exist
            Log.d("MyCameraApp", "cannot get camera or does not exist");
        }
        return camera;
    }

    PictureCallback mPicture = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = getOutputMediaFile();
            if (pictureFile == null) {
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                picPath = pictureFile.getPath();
                Log.i("picPath:", pictureFile.getPath());
                //Upload to server:
                UpdateNewBitMap(pictureFile.getPath());
                uploadImage();


            } catch (FileNotFoundException e) {

            } catch (IOException e) {
            }


        }

    };

    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCameraApp");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }
        // Create a media file name

        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + getNewPicName());
        Toast.makeText(getApplicationContext(),
                "new Pic created!, " + mediaFile.getName() + "Location: Pictures/MyCameraApp", Toast.LENGTH_LONG).show();

        return mediaFile;
    }

    public void setCameraParameters(int width, int height) {
        Camera.Parameters params = mCamera.getParameters();

        // Check what resolutions are supported by your camera
        List<Camera.Size> sizes = params.getSupportedPictureSizes();

        // Iterate through all available resolutions and choose one.
        // The chosen resolution will be stored in mSize.
        Camera.Size mSize = null;
        for (Camera.Size size : sizes) {
            Log.i("ERROR", "Available resolution: " + size.width + " " + size.height);
            mSize = size;
        }
        mSize.width = width;
        mSize.height = height;

        Log.i("ERROR", "Chosen resolution: " + mSize.width + " " + mSize.height);
        params.setPictureSize(mSize.width, mSize.height);

        mCamera.setParameters(params);

        Toast.makeText(getApplicationContext(), "Quality: " + mSize.width + " / " + mSize.height, Toast.LENGTH_LONG).show();
    }

    public String getStringImage(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, compressQuality, baos);
        byte[] imageBytes = baos.toByteArray();
        String encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT);
        return encodedImage;
    }

    private void uploadImage() {
        //Showing the progress dialog
        final ProgressDialog loading = ProgressDialog.show(this, "Uploading...", "Please wait...", false, false);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, UPLOAD_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String s) {
                        //Disimissing the progress dialog
                        loading.dismiss();
                        //Showing toast message of the response
                        Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG).show();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        //Dismissing the progress dialog
                        loading.dismiss();

                        //Showing toast
                        Toast.makeText(MainActivity.this, "Cant upload pic!", Toast.LENGTH_LONG).show();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                //Converting Bitmap to String
                String image = getStringImage(bitmap);
                //Getting Image Name

                String name = getNewPicName().trim();

                //Creating parameters
                Map<String, String> params = new Hashtable<String, String>();

                //Adding parameters
                params.put(KEY_IMAGE, image);
                params.put(KEY_NAME, name);

                //returning parameters
                return params;
            }
        };


        //Creating a Request Queue
        RequestQueue requestQueue = Volley.newRequestQueue(this);

        //Adding request to the queue
        requestQueue.add(stringRequest);

    }

    public void UpdateNewBitMap(String path) {
        try {
            picPath = path;
            File f = new File(picPath);
            Uri imageUri = Uri.fromFile(f);
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void turnOnGps() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                Latitude = location.getLatitude();
                Longitude = location.getLongitude();
                Altitude = location.getAltitude();

                altitude.setText("altitude: " + Altitude);
                latitude.setText("Lltitude: " + Latitude);
                longitude.setText("altitude: " + Longitude);

                //send data to server
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {
                //GO to gps SETTING:
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        };
        configure_button();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                configure_button();
                break;
            default:
                break;
        }
    }

    void configure_button() {
        // first check for permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.INTERNET}, 10);
            }
            return;
        }
        // this code won't execute IF permissions are not allowed, because in the line above there is return statement.
        locationManager.requestLocationUpdates("gps", minTime, minDistance, locationListener);
    }

    public void onClickButtonAutomaticallySendSMS() {
        toggleButtonAutomaticallySendSMS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean on) {

                if (on) {
                    Log.i("Test", "---------------------------");
                    //Do something when Switch button is on/checked
                    toggleButtonAutomaticallySendSMS.setText("STOP SENDING SMS !");
                    toggleButtonAutomaticallySendSMS.setBackgroundColor(Color.RED);
                    sendSMS = true;
                    Thread t = new Thread(runnableSMS);
                    t.start();

                } else {
                    //Do something when Switch is off/unchecked
                    toggleButtonAutomaticallySendSMS.setText("AUTOMATICALLY SEND SMS");
                    toggleButtonAutomaticallySendSMS.setBackgroundColor(Color.GREEN);
                    sendSMS = false;
                    t.interrupt();
                }
            }
        });

    }

    public void sendSms() {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(destNum, null, getNewPicName(), null, null);

        Toast.makeText(getApplicationContext(), "SMS set to: " + destNum, Toast.LENGTH_SHORT).show();
    }

    public String getNewPicName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float temp = ((float) batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10);
        float BatteryLevel;


        // Error checking that probably isn't needed but I added just in case.
        if (level == -1 || scale == -1) {
            BatteryLevel = 50.0f;
        }

        BatteryLevel = ((float) level / (float) scale) * 100.0f;


        if (Longitude != 0 && Latitude != 0) {
            String ans = timeStamp + "|p=" + ("" + Latitude).substring(0, 8) + " " + ("" + Longitude).substring(0, 8) + "|al=" + (int) Altitude + "|bt" + BatteryLevel + "|tm=" + temp + ".jpg";
            return ans;
        }
        return timeStamp + "|p=" + Latitude + " " + Longitude + "|al=" + (int) Altitude + "|bt=" + BatteryLevel + "|tm=" + temp + ".jpg";
    }


    //////////////////////
    //Menu calculations://
    //////////////////////
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my_camera, menu);

        SubMenu sMenu = menu.addSubMenu(0, 99, 0, "set quality pic"); //If you want to add submenu

        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();

        for (int i = 0; i < sizes.size(); i++) {
            sMenu.add(0, i, 0, "set to: " + sizes.get(i).width + " / " + sizes.get(i).height).setShortcut('5', 'z');
        }

//        Available resolution: 4160 3120
//        I/ERROR: Available resolution: 4160 2340
//        I/ERROR: Available resolution: 4000 3000
//        I/ERROR: Available resolution: 3264 2448
//        I/ERROR: Available resolution: 3200 2400
//        I/ERROR: Available resolution: 2592 1944
//        I/ERROR: Available resolution: 2048 1536
//        I/ERROR: Available resolution: 1920 1080
//        I/ERROR: Available resolution: 1600 1200
//        I/ERROR: Available resolution: 1440 1080
//        I/ERROR: Available resolution: 1536 864
//        I/ERROR: Available resolution: 1280 960
//        I/ERROR: Available resolution: 1280 768
//        I/ERROR: Available resolution: 1280 720
//        I/ERROR: Available resolution: 1024 768
//        I/ERROR: Available resolution: 800 600
//        I/ERROR: Available resolution: 864 480
//        I/ERROR: Available resolution: 800 480
//        I/ERROR: Available resolution: 720 480
//        I/ERROR: Available resolution: 640 480
//        I/ERROR: Available resolution: 352 288
//        I/ERROR: Available resolution: 320 240
//        I/ERROR: Available resolution: 176 144
//        I/ERROR: Available resolution: 160 120


        sMenu = menu.addSubMenu(0, 98, 0, "set frequency taking pic"); //If you want to add submenu

        sMenu.add(0, 100, 0, "picture every 3 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 101, 0, "picture every 5 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 102, 0, "picture every 7 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 103, 0, "picture every 10 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 104, 0, "picture every 15 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 105, 0, "picture every 20 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 106, 0, "picture every 30 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 107, 0, "picture every 40 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 108, 0, "picture every 50 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 109, 0, "picture every 1 minute ").setShortcut('5', 'z');
        sMenu.add(0, 110, 0, "picture every 5 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 111, 0, "picture every 10 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 112, 0, "picture every 20 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 113, 0, "picture every 30 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 114, 0, "picture every 1 hour ").setShortcut('5', 'z');
        sMenu.add(0, 115, 0, "picture every 2 hours ").setShortcut('5', 'z');
        sMenu.add(0, 116, 0, "picture every 3 hours ").setShortcut('5', 'z');


        sMenu = menu.addSubMenu(0, 97, 0, "set frequency taking SMS"); //If you want to add submenu
        sMenu.add(0, 117, 0, "SMS every 3 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 118, 0, "SMS every 5 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 119, 0, "SMS every 7 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 120, 0, "SMS every 10 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 121, 0, "SMS every 15 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 122, 0, "SMS every 20 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 123, 0, "SMS every 30 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 124, 0, "SMS every 40 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 125, 0, "SMS every 50 seconds ").setShortcut('5', 'z');
        sMenu.add(0, 126, 0, "SMS every 1 minute ").setShortcut('5', 'z');
        sMenu.add(0, 127, 0, "SMS every 5 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 128, 0, "SMS every 10 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 129, 0, "SMS every 20 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 130, 0, "SMS every 30 minutes ").setShortcut('5', 'z');
        sMenu.add(0, 131, 0, "SMS every 1 hour ").setShortcut('5', 'z');
        sMenu.add(0, 132, 0, "SMS every 2 hours ").setShortcut('5', 'z');
        sMenu.add(0, 133, 0, "SMS every 3 hours ").setShortcut('5', 'z');

        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();

        for (int i = 0; i < sizes.size(); i++) {
            if (item.getItemId() == i) {
                setCameraParameters(sizes.get(i).width, sizes.get(i).height);
                return true;
            }
        }


        //1 sec = 1000
        switch (item.getItemId()) {
            case 100:
                PICtimeOut = 3000;
                Toast.makeText(getApplicationContext(), "set Frequency to 3 sec", Toast.LENGTH_LONG).show();
                return true;
            case 101:
                PICtimeOut = 5000;
                Toast.makeText(getApplicationContext(), "set Frequency to 5 sec", Toast.LENGTH_LONG).show();
                return true;
            case 102:
                PICtimeOut = 7000;
                Toast.makeText(getApplicationContext(), "set Frequency to 7 sec", Toast.LENGTH_LONG).show();
                return true;
            case 103:
                PICtimeOut = 10000;
                Toast.makeText(getApplicationContext(), "set Frequency to 10 sec", Toast.LENGTH_LONG).show();
                return true;
            case 104:
                PICtimeOut = 15000;
                Toast.makeText(getApplicationContext(), "set Frequency to 15 sec", Toast.LENGTH_LONG).show();
                return true;
            case 105:
                PICtimeOut = 20000;
                Toast.makeText(getApplicationContext(), "set Frequency to 20 sec", Toast.LENGTH_LONG).show();
                return true;
            case 106:
                PICtimeOut = 30000;
                Toast.makeText(getApplicationContext(), "set Frequency to 30 sec", Toast.LENGTH_LONG).show();
                return true;
            case 107:
                PICtimeOut = 40000;
                Toast.makeText(getApplicationContext(), "set Frequency to 40 sec", Toast.LENGTH_LONG).show();
                return true;
            case 108:
                PICtimeOut = 50000;
                Toast.makeText(getApplicationContext(), "set Frequency to 50 sec", Toast.LENGTH_LONG).show();
                return true;
            case 109:
                PICtimeOut = 60000;
                Toast.makeText(getApplicationContext(), "set Frequency to 1 min", Toast.LENGTH_LONG).show();
                return true;
            case 110:
                PICtimeOut = 30000;
                Toast.makeText(getApplicationContext(), "set Frequency to 5 min", Toast.LENGTH_LONG).show();
                return true;
            case 111:
                PICtimeOut = 60000;
                Toast.makeText(getApplicationContext(), "set Frequency to 10 min", Toast.LENGTH_LONG).show();
                return true;
            case 112:
                PICtimeOut = 120000;
                Toast.makeText(getApplicationContext(), "set Frequency to 20 min", Toast.LENGTH_LONG).show();
                return true;
            case 113:
                PICtimeOut = 180000;
                Toast.makeText(getApplicationContext(), "set Frequency to 30 min", Toast.LENGTH_LONG).show();
                return true;
            case 114:
                PICtimeOut = 360000;
                Toast.makeText(getApplicationContext(), "set Frequency to 1 hours", Toast.LENGTH_LONG).show();
                return true;
            case 115:
                PICtimeOut = 720000;
                Toast.makeText(getApplicationContext(), "set Frequency to 2 hours", Toast.LENGTH_LONG).show();
                return true;
            case 116:
                PICtimeOut = 1080000;
                Toast.makeText(getApplicationContext(), "set Frequency to 3 hours", Toast.LENGTH_LONG).show();
                return true;
            //SMS case:
            case 117:
                SMStimeOut = 3000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 3 sec", Toast.LENGTH_LONG).show();
                return true;
            case 118:
                SMStimeOut = 5000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 5 sec", Toast.LENGTH_LONG).show();
                return true;
            case 119:
                SMStimeOut = 7000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 7 sec", Toast.LENGTH_LONG).show();
                return true;
            case 120:
                SMStimeOut = 10000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 10 sec", Toast.LENGTH_LONG).show();
                return true;
            case 121:
                SMStimeOut = 15000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 15 sec", Toast.LENGTH_LONG).show();
                return true;
            case 122:
                SMStimeOut = 20000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 20 sec", Toast.LENGTH_LONG).show();
                return true;
            case 123:
                SMStimeOut = 30000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 30 sec", Toast.LENGTH_LONG).show();
                return true;
            case 124:
                SMStimeOut = 40000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 40 sec", Toast.LENGTH_LONG).show();
                return true;
            case 125:
                SMStimeOut = 50000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 50 sec", Toast.LENGTH_LONG).show();
                return true;
            case 126:
                SMStimeOut = 60000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 1 min", Toast.LENGTH_LONG).show();
                return true;
            case 127:
                SMStimeOut = 300000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 5 min", Toast.LENGTH_LONG).show();
                return true;
            case 128:
                SMStimeOut = 600000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 10 min", Toast.LENGTH_LONG).show();
                return true;
            case 129:
                SMStimeOut = 1200000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 20 min", Toast.LENGTH_LONG).show();
                return true;
            case 130:
                SMStimeOut = 1800000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 30 min", Toast.LENGTH_LONG).show();
                return true;
            case 131:
                SMStimeOut = 3600000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 1 hours", Toast.LENGTH_LONG).show();
                return true;
            case 132:
                SMStimeOut = 7200000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 2 hours", Toast.LENGTH_LONG).show();
                return true;
            case 133:
                SMStimeOut = 10800000;
                Toast.makeText(getApplicationContext(), "set SMS Frequency to 3 hours", Toast.LENGTH_LONG).show();
                return true;
        }
        Log.i("SMStimeOut", "SMStimeOut is " + SMStimeOut);
        return super.onOptionsItemSelected(item);

    }


}
