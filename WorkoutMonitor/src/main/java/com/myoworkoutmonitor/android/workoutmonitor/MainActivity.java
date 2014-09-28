/*
 * Copyright (C) 2014 Thalmic Labs Inc.
 * Distributed under the Myo SDK license agreement. See LICENSE.txt for details.
 */

package com.myoworkoutmonitor.android.workoutmonitor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;


public class MainActivity extends Activity {

    // This code will be returned in onActivityResult() when the enable Bluetooth activity exits.
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = MainActivity.class.getName();

    private TextView mTextView;
    private TextView anglesView;

    private float maxPitch = Float.MIN_VALUE;
    private float minPitch = Float.MAX_VALUE;
    private boolean isRecording = false;
    private boolean isStarted = false;

    private boolean maxReached = false;
    private boolean minReached = false;

    private boolean isRecorded = false;

    private int repCount;

    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        private Arm mArm = Arm.UNKNOWN;
        private XDirection mXDirection = XDirection.UNKNOWN;

        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            mTextView.setTextColor(Color.CYAN);
            mTextView.setText("Connected!");
            anglesView = (TextView)findViewById(R.id.angle_text);
            anglesView.setTextColor(Color.CYAN);
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            mTextView.setTextColor(Color.RED);
        }

        // onArmRecognized() is called whenever Myo has recognized a setup gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmRecognized(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mArm = arm;
            mXDirection = xDirection;
        }

        // onArmLost() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmLost(Myo myo, long timestamp) {
            mArm = Arm.UNKNOWN;
            mXDirection = XDirection.UNKNOWN;
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            int numberRepeat = 0;


            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (mXDirection == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }

            if (pitch > maxPitch && isRecording) {
                maxPitch = pitch;
            }
            if (pitch < minPitch && isRecording) {
                minPitch = pitch;
            }
            // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
//            mTextView.setRotation(roll);
//            mTextView.setRotationX(pitch);
//            mTextView.setRotationY(yaw);
            float savedMax = Float.MIN_VALUE;
            float savedMin = Float.MAX_VALUE;

            File file = new File("/sdcard/Exercise_1.txt");
            if (file.exists()) {
                String data = read("Exercise_1.txt");
                String[] data_array = data.split(",");
                savedMax = Float.parseFloat(data_array[0].replace(" ", ""));
                savedMin = Float.parseFloat(data_array[1].replace(" ", ""));

                incrementRep(pitch, savedMax, savedMin);
            }


            StringBuilder sb = new StringBuilder();
            sb.append("Current Angles:").append(System.getProperty("line.separator"));
            sb.append("Number rep: ").append(round(repCount, 3)).append(System.getProperty("line.separator"));
            sb.append("Max Pitch: ").append(round(maxPitch, 3)).append(System.getProperty("line.separator"));
            sb.append("Min Pitch: ").append(round(minPitch, 3)).append(System.getProperty("line.separator"));
            sb.append("Saved Max: ").append(round(savedMax, 3)).append(System.getProperty("line.separator"));
            sb.append("Saved Min: ").append(round(savedMin, 3)).append(System.getProperty("line.separator"));



            sb.append("Roll: ").append(round(roll, 3)).append(System.getProperty("line.separator"));
            sb.append("Pitch: ").append(round(pitch, 3)).append(System.getProperty("line.separator"));
            sb.append("Yaw: ").append(round(yaw, 3)).append(System.getProperty("line.separator"));
            anglesView.setText(sb.toString());

            //code to update the curve
            if (isRecorded) {
                ImageView imageView = (ImageView) findViewById(R.id.imageView);
                imageView.setPivotX(imageView.getWidth()/2);
                imageView.setPivotY(imageView.getHeight()/2);
                imageView.setRotation(updateImageView(savedMin, savedMax, pitch));

            }
        }

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            mTextView.setText("Got Pose!");
            switch (pose) {
                case UNKNOWN:
                    mTextView.setText(getString(R.string.display_text));
                    break;
                case REST:
                    int restTextId = R.string.display_text;
                    switch (mArm) {
                        case LEFT:
                            restTextId = R.string.arm_left;
                            break;
                        case RIGHT:
                            restTextId = R.string.arm_right;
                            break;
                    }
                    mTextView.setText(getString(restTextId));
                    break;
                case FIST:
                    mTextView.setText(getString(R.string.pose_fist));
                    break;
                case WAVE_IN:
                    mTextView.setText(getString(R.string.pose_wavein));
                    break;
                case WAVE_OUT:
                    mTextView.setText(getString(R.string.pose_waveout));
                    break;
                case FINGERS_SPREAD:
                    mTextView.setText(getString(R.string.pose_fingersspread));
                    break;
                case THUMB_TO_PINKY:
                    mTextView.setText(getString(R.string.pose_thumbtopinky));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workoutmonitor);

        mTextView = (TextView) findViewById(R.id.text);

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final Button startRecordingButton = (Button) findViewById(R.id.start_recording);
        final Button stopRecordingButton = (Button) findViewById(R.id.stop_recording);
        final Button startExerciseButton = (Button) findViewById(R.id.start_exercise);

        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Start Recording
                isRecording = true;

            }
        });

        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop Recording
                isRecording = false;
                String toSave = maxPitch + "," + minPitch;
                write("Exercise_1", toSave);
                isRecorded = true;
            }
        });

        startExerciseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop Recording
                isStarted = true;
            }
        });

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If Bluetooth is not enabled, request to turn it on.
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth, so exit.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    /**
     * Round to certain number of decimals
     *
     * @param d
     * @param decimalPlace
     * @return
     */
    public float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    public Boolean write(String fileName, String fileContent){
        try {
            String path = "/sdcard/"+fileName+".txt";
            File file = new File(path);
            // If file does not exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(fileContent);
            bw.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String read(String fileName){
        BufferedReader br = null;
        String response = null;
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath());

            StringBuffer output = new StringBuffer();
            String path = dir.getPath() + "/" + fileName;
            Log.i(TAG, "path: " + path );
//            String path = "/sdcard/"+fileName+".txt";
            br = new BufferedReader(new FileReader(path));
            String line = "";
            while ((line = br.readLine()) != null) {
                output.append(line +"\n");
            }
            response = output.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return response;
    }

    public boolean incrementRep(float pitch, float savedMax, float savedMin) {
        boolean valid = false;
        float validMaxMinimun = savedMax * 0.8f;
        float validMaxMaximun = savedMax * 1.2f;

        float validMinMinimun = savedMin * 0.8f;
        float validMinMaximun = savedMin * 1.2f;
//
//        boolean withinMaxAcceptable = false;
//        boolean withinMinAcceptable = false;
//        if( pitch > validMaxMinimun && pitch < validMaxMinimun) withinMaxAcceptable = true;
//        if( pitch > validMinMinimun && pitch < validMinMaximun) withinMinAcceptable = true;
//
//        // maxReached minReached


        if (pitch > validMaxMinimun && !maxReached) {
            repCount ++;
            maxReached = true;
        }

        if (pitch < validMaxMinimun) {
            maxReached = false;
        }

        return valid;
    }

    private float updateImageView(float min, float max, float current) {
        float interval = Math.abs(max) + Math.abs(min);
        if (current<0) {
            return ((min-current)/interval)*360;
        } else {
            return ((current + Math.abs(min))/interval)*360;
        }
    }
}
