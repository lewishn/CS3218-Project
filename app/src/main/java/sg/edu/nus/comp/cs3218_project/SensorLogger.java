package sg.edu.nus.comp.cs3218_project;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.res.TypedArrayUtils;
import android.util.Log;
import android.util.Pair;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by lewishn on 4/10/2016.
 */
public class SensorLogger {
    public Sensor accelerometer;
    public Sensor gyroscope;
    public SensorManager sensorManager;

    private ArrayList<Short> audioData;
    private ArrayList<Pair<Long, float[]>> accelData;
    private ArrayList<Pair<Long, float[]>> gyroData;
    private long audioStart;
    private long audioEnd;

    private static ArrayList<Pair<Long, Long>> accumulated = new ArrayList<>();

    SensorLogger() {
        audioData = new ArrayList<Short>(16384);
        accelData = new ArrayList<Pair<Long, float[]>>();
        gyroData = new ArrayList<Pair<Long, float[]>>();
    }

    public void setInitialAudioTimeFrame(long time) {
        audioStart = time;
    }

    public void setFinalAudioTimeFrame(long time) {
        audioEnd = time;
    }

    public void addAudioSignals(short[] data) {
        for (short d : data) {
            audioData.add(d);
        }
    }

    public void addGyroSignals(long timestamp, float[] data) {
        gyroData.add(new Pair(timestamp, Arrays.copyOf(data, data.length)));
    }

    public void addAccelSignals(long timestamp, float[] data) {
        accelData.add(new Pair(timestamp, Arrays.copyOf(data, data.length)));
    }

    public void test() {
        Log.d("SensorLogger:", "Audio Timestamp: " + audioStart + "-" + audioEnd);
        Log.d("SensorLogger:", "Audio Length: " + (audioEnd - audioStart));
        Log.d("SensorLogger:", "Audio Data Length: " + audioData.size());
        Log.d("SensorLogger:", "Gyro Timestamp: " + gyroData.get(0).first + "-" + gyroData.get(gyroData.size() - 1).first);
        Log.d("SensorLogger:", "Gyro Length: " +  (gyroData.get(gyroData.size() - 1).first - gyroData.get(0).first));
        Log.d("SensorLogger:", "Gyro Data Length: " + gyroData.size());
        Log.d("SensorLogger:", "Accel TimeStamp:" + accelData.get(0).first + "-" + accelData.get(accelData.size() - 1).first);
        Log.d("SensorLogger:", "Accel Length: " +  (accelData.get(accelData.size() - 1).first - accelData.get(0).first));
        Log.d("SensorLogger:", "Accel Data Length: " + accelData.size());

        long acc = calibrateAccelerometer();
        long aud = calibrateAudio(acc);
        long gyro = calibrateGyroscope(acc);
        accumulated.add(new Pair(aud - acc, aud - gyro));

        String s = "";
        for (Pair p: accumulated) {
            Log.d("SensorLogger: accumulated:", p.first + "," + p.second);
        }

        Log.d("SensorLogger: Sound - Accel:", "" + (aud - acc));
        Log.d("SensorLogger: Sound - Gyro:", "" + (aud - gyro));
        Log.d("SensorLogger: Accel - Gyro:", "" + (acc - gyro));

    }

    public long calibrateAccelerometer() {
        int index = 0;
        float max = 0;

        for (int i = 0; i < accelData.size(); i++) {
            if (z(accelData, i) > max) {
                index = i;
                max = z(accelData, i);
            }

            Log.d("SensorLogger:Accel", "" + accelData.get(i).first + "[" +
                    x(accelData, i) + "," + y(accelData, i) + "," + z(accelData, i) + "]");
        }

        Log.d("SensorLogger:", "Accelerometer detected at " + accelData.get(index).first);
        return accelData.get(index).first - accelData.get(0).first;
    }

    public long calibrateAudio(long mid) {
        long window = 500000000;
        long duration = audioEnd - audioStart;
        long resolution = duration / audioData.size();

        long start = Math.max(0, mid - window);
        long end = Math.max(duration, mid + window);
        int startIndex = (int) (start / resolution);
        int endIndex = (int) (end / resolution);

        int index = 0;
        short max = 0;
        for (int i = startIndex; i < Math.min(audioData.size(), endIndex); i++) {
            if (audioData.get(i) > max) {
                max = audioData.get(i);
                index = i;
            }
        }
        return ((audioEnd - audioStart) / audioData.size()) * index;
    }

    public long calibrateGyroscope(long mid) {
        int mid_window = 500000000;
        float threshold = 0.1f;
        int index = 0;
        int window = 5;

        while (index < gyroData.size() - window) {
            if (gyroData.get(index).first - gyroData.get(0).first < mid - mid_window) {
                index++;
                continue;
            } else if (gyroData.get(index).first - gyroData.get(0).first > mid + mid_window) {
                break;
            }

            if (y(gyroData, index) > threshold) {
                boolean isConditionMet = true;
                for (int i = 1; i < window; i++) {
                    if (y(gyroData, index + i) > threshold) {
                        isConditionMet = false;
                        break;
                    }
                }
                if (isConditionMet) {
                    break;
                } else {
                    index++;
                }
            } else {
                index++;
            }
        }
        /*
        Log.d("SensorLogger:Gyro:", "Gyroscope detected at " + gyroData.get(index).first);
        for (int i = 0; i < gyroData.size(); i++) {
            Log.d("SensorLogger:Gyro", "" + gyroData.get(i).first + "[" + gyroData.get(i).second[0] +
                "," + gyroData.get(i).second[1] + "," + gyroData.get(i).second[2] + "]");
        }
        */
        return gyroData.get(index).first - gyroData.get(0).first;
    }

    public float x(ArrayList<Pair<Long, float[]>> pair, int index) {
       return pair.get(index).second[0];
    }
    public float y(ArrayList<Pair<Long, float[]>> pair, int index) {
        return pair.get(index).second[1];
    }
    public float z(ArrayList<Pair<Long, float[]>> pair, int index) {
        return pair.get(index).second[2];
    }
}
