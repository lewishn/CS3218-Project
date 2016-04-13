package sg.edu.nus.comp.cs3218_project;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.provider.MediaStore;
import android.support.v4.content.res.TypedArrayUtils;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by lewishn on 4/10/2016.
 */
public class SensorLogger {
    public Sensor accelerometer;
    public Sensor gyroscope;
    public SensorManager sensorManager;

    private MediaMetadataRetriever retriever;
    private ArrayList<Short> audioData;
    private ArrayList<Pair<Long, float[]>> accelData;
    private ArrayList<Pair<Long, float[]>> gyroData;
    private ArrayList<Bitmap> imageData;
    private String path;
    private long audioStart;
    private long audioEnd;
    private Activity activity;

    private static ArrayList<Pair<Long, Long>> accumulated = new ArrayList<>();

    SensorLogger() {
        audioData = new ArrayList<Short>(16384);
        accelData = new ArrayList<Pair<Long, float[]>>();
        gyroData = new ArrayList<Pair<Long, float[]>>();
        imageData = new ArrayList<Bitmap>();
    }

    public void setActivity(Activity activity) {
       this.activity = activity;
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

    public void setVideoFile(File file) {
        path = file.getAbsolutePath();
    }



    public void calibrate() {
        // For acc and gyro, timestamp is in nanoseconds
        long acc = calibrateAccelerometer();
        long gyro = calibrateGyroscope(acc);
        long vid = calibrateVideo(Math.min(acc, gyro), Math.max(acc, gyro));

        String t = (acc - vid) + "," + (acc - gyro);
        try {
            File f = new File(activity.getExternalFilesDir(null), "calibrate.csv");
            FileWriter writer = new FileWriter(f, true);
            writer.write(t);
            writer.flush();
            writer.close();
        }catch (IOException e) {
            Log.e("Error CSV", e.getMessage());
        }

        Log.d("SensorLogger: Accel:", "" + acc);
        Log.d("SensorLogger: Gyro:", "" + gyro);
        Log.d("SensorLogger: Video:", "" + vid);
    }

    public void extractBestFrame() {
        long t = pickBest() / 1000;
        ArrayList<Long> accVidCalib = new ArrayList<>();

        try {
            File f = new File(activity.getExternalFilesDir(null), "calibrate.csv");
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                accVidCalib.add(Long.parseLong(data[0]));
            }
        } catch (IOException e) {
            Log.e("Error CSV", e.getMessage());
            return;
        }

        long sync = 0;
        for (Long l : accVidCalib) {
            sync += l;
        }
        sync = sync / accVidCalib.size();
        sync = sync / 1000;

        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        try {
            File f = new File(activity.getExternalFilesDir(null), "best-unsync.png");
            FileOutputStream out = new FileOutputStream(f);
            Bitmap bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            File f2 = new File(activity.getExternalFilesDir(null), "best-sync.png");
            out = new FileOutputStream(f2);
            bmp = retriever.getFrameAtTime(t - sync, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public long calibrateVideo(long t1, long t2) {
        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);

        long window = 500000;
        long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        long startTime = Math.max(0, t1 / 1000 - window);
        long endTime = Math.min(duration * 1000, t2 / 1000 + window);
        long resolution = 33333;
        long t;
        float[] hsv = new float[3];

        Log.d("SensorLogger: Vid Times:", startTime + "," + endTime);
        for (t = startTime; t < endTime; t += resolution) {
            Log.d("SensorLogger: Processing Video Frame", "" + t);
            Bitmap img = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
            long sum = 0;
            boolean isBlack = true;

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    int c = img.getPixel(x, y);
                    /*
                    if (!(Color.blue(c) < 20 && Color.red(c) < 20 && Color.green(c) < 20)) {
                        isBlack = false;
                        break;
                    }
                    */

                    Color.colorToHSV(c, hsv);
                    if (hsv[2] > 0.2) {
                        isBlack = false;
                        break;
                    }
                }
                if (!isBlack) {
                    break;
                }
            }
            if (isBlack) {
                break;
            }
        }

        try {
            File f = new File(activity.getExternalFilesDir(null), "image2.png");
            FileOutputStream out = new FileOutputStream(f);
            Bitmap bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            File f2 = new File(activity.getExternalFilesDir(null), "image0.png");
            out = new FileOutputStream(f2);
            bmp = retriever.getFrameAtTime(t - resolution * 2, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            File f3 = new File(activity.getExternalFilesDir(null), "image1.png");

            out = new FileOutputStream(f3);
            bmp = retriever.getFrameAtTime(t - resolution, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return t * 1000;
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
        long end = Math.min(duration, mid + window);
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
        return resolution * index;
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

        Log.d("SensorLogger:Gyro:", "Gyroscope detected at " + gyroData.get(index).first);
        for (int i = 0; i < gyroData.size(); i++) {
            Log.d("SensorLogger:Gyro", "" + gyroData.get(i).first + "[" + gyroData.get(i).second[0] +
                "," + gyroData.get(i).second[1] + "," + gyroData.get(i).second[2] + "]");
        }
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

    public long pickBest() {
        ArrayList<Pair<Long, float[]>> bestAccValues = new ArrayList<>();
        for (int i = 0; i < accelData.size(); i++) {
            double value = val(accelData.get(i).second);
            if (value < 10.3 && value > 9.5) {
                bestAccValues.add(accelData.get(i));
            }
        }

        Collections.sort(bestAccValues, new Comparator<Pair<Long, float[]>>() {
            @Override
            public int compare(Pair<Long, float[]> lhs, Pair<Long, float[]> rhs) {
                return (int) (val(lhs.second) - val(rhs.second));
            }
        });

        return bestAccValues.get(0).first - accelData.get(0).first;
    }


    public double val(float[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
