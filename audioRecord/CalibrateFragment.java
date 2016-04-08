package sg.edu.nus.comp.cs3218_project;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CalibrateFragment extends Fragment
        implements View.OnClickListener, SensorEventListener, FragmentCompat.OnRequestPermissionsResultCallback {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private Button mButtonVideo;

    private static final String TAG = "CalibrateFragment";
    private static final String FRAGMENT_DIALOG = "dialog";

    private Sensor accelerometer;
    private Sensor gyroscope;
    private SensorManager sensorManager;
    private TextView acceleration;
    private Activity mActivity;

    private static final int  FS = 16000; // sampling frequency
    public AudioRecord audioRecord;
    private int audioEncoding = 2;
    private int nChannels = 16;
    private Thread recordingThread;
    public static short[] buffer = new short[2048];
    public static int bufferSize = 2048;     // in bytes
    private boolean isRecording = false;

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public static CalibrateFragment newInstance() {
        return new CalibrateFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calibrate, container, false);
    }
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mButtonVideo = (Button) view.findViewById(R.id.video);
        mButtonVideo.setOnClickListener(this);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
    }


    private void initSensor() {
        sensorManager = (SensorManager) mActivity.getSystemService(mActivity.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        acceleration = (TextView) getView().findViewById(R.id.acceleration);
    }

    private float[] accelData = {0, 0, 0};
    private float[] gyroData = {0, 0, 0};

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelData = event.values;
        } else {
            gyroData = event.values;
        }
        acceleration.setText("X: " + accelData[0] +
                        "\nY: " + accelData[1] +
                        "\nZ: " + accelData[2] +
                        "\ngX: " + gyroData[0] +
                        "\ngY: " + gyroData[1] +
                        "\ngZ: " + gyroData[2]
        );

        //Log.d("Accelerometer", "" + event.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onResume() {
        super.onResume();
        initSensor();
    }

    @Override
    public void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        try {
            if (!isRecording) {
                recordAudio();
            } else {
                stopRecordAudio();
            }
        } catch (Exception e) {

        }
    }

    ArrayList <Short> audioData = new ArrayList<Short>();

    public void recordAudio() throws Exception{
        mButtonVideo.setText(R.string.stop);
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
            }
            audioRecord = new AudioRecord(1, FS, nChannels, audioEncoding, AudioRecord.getMinBufferSize(FS, nChannels, audioEncoding));
        } catch (Exception e) {
            Log.d("Error in SoundSampler ", e.getMessage());
            throw new Exception();
        }

        audioRecord.startRecording();
        isRecording = true;

        recordingThread = new Thread() {
            public void run() {
                while (true) {
                    audioRecord.read(buffer, 0, bufferSize);
                    for (short buf: buffer) {
                        audioData.add(buf);
                    }
                }
            }
        };
        recordingThread.start();

    }

    public void stopRecordAudio() throws Exception {
        mButtonVideo.setText(R.string.record);
        audioRecord.stop();
        isRecording = false;
        recordingThread.join();
        for (Short data: audioData) {
            Log.d("Audio: ", data.toString());
        }
    }
}