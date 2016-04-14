package sg.edu.nus.comp.cs3218_project;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class SensorLogger {
    private MediaMetadataRetriever retriever;
    private ArrayList<Short> audioData;
    private ArrayList<Pair<Long, float[]>> accelData;
    private ArrayList<Pair<Long, float[]>> gyroData;
    private ArrayList<Bitmap> imageData;
    private String path;
    private long audioEnd;
    private Activity activity;
    long accVidSync = 0;
    long accGyroSync = 0;

    SensorLogger() {
        accelData = new ArrayList<Pair<Long, float[]>>();
        gyroData = new ArrayList<Pair<Long, float[]>>();
        imageData = new ArrayList<Bitmap>();
    }

    public void setActivity(Activity activity) {
       this.activity = activity;
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

    /**
     * Calibrates accelerometer, gyroscope, and video/audio
     * Data 1: Delay between accelerometer and video/audio in nanoseconds
     * Data 2: Delay between accelerometer and gyroscope in nanoseconds
     * Results are appended to calibrate.csv file
     */
    public void calibrate() {
        // For acc and gyro, timestamp is in nanoseconds
        long acc = calibrateAccelerometer();
        long gyro = calibrateGyroscope(acc);
        long vid = calibrateVideo(Math.min(acc, gyro), Math.max(acc, gyro));

        String t = (acc - vid) + "," + (acc - gyro);
        try {
            File f = new File(activity.getExternalFilesDir(null), "calibrate.csv");
            FileWriter writer = new FileWriter(f, true);
            writer.write(t + System.lineSeparator());
            writer.flush();
            writer.close();
        }catch (IOException e) {
            Log.e("Error CSV", e.getMessage());
        }
    }

    /**
     * Finds the timestamp at which impact of the phone with the surface occurs
     * This point is taken at the point where acceleration in the z axis is greatest,
     * i.e. The point just before the phone hits the surface and stops moving
     *
     * @return the timestamp at which impact occurs
     */
    public long calibrateAccelerometer() {
        int index = 0;
        float max = 0;

        for (int i = 0; i < accelData.size(); i++) {
            if (z(accelData, i) > max) {
                index = i;
                max = z(accelData, i);
            }
        }
        return accelData.get(index).first - accelData.get(0).first;
    }

    /**
     * Finds the timestamp at which impact of the phone with the surface occurs
     * This point is taken at the point where the gyroscope value is above a certain threshold,
     * and subsequent values are below the threshold.
     *
     * The timestamp is searched between mid - 0.5s, and mid + 0.5s
     * This is done to reduce computation, as well as to ignore other movements of the phone which
     * happen before/after the actual impact.
     *
     * @param mid the accelerometer timestamp at which impact occurs
     * @return the timestamp at which impact occurs
     */
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
        return gyroData.get(index).first - gyroData.get(0).first;
    }

    /**
     * Finds the timestamp at which impact of the phone with the surface occurs
     * This is the point where the video frame is completely black
     *
     * To reduce computation, the video is checked between from min(acc, gyro) - 0.5s and max(acc, gyro) + 0.5s
     *
     * @param t1 min(accelerometer timestamp, gyroscope timestamp)
     * @param t2 max(accelerometer timestamp, gyroscope timestamp)
     * @return the video timestamp at which impact occurs
     */
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

        for (t = startTime; t < endTime; t += resolution) {
            Bitmap img = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
            long sum = 0;
            boolean isBlack = true;

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    int c = img.getPixel(x, y);
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
        return t * 1000;
    }

    /*
     * x coordinate value
     */
    public float x(ArrayList<Pair<Long, float[]>> pair, int index) {
       return pair.get(index).second[0];
    }

    /*
     * y coordinate value
     */
    public float y(ArrayList<Pair<Long, float[]>> pair, int index) {
        return pair.get(index).second[1];
    }

    /*
     * z coordinate value
     */
    public float z(ArrayList<Pair<Long, float[]>> pair, int index) {
        return pair.get(index).second[2];
    }


    /**
     * Extracts the frame with least blur from an input video and its corresponging
     * accelerometer/gyroscope data
     */
    public void extractBestFrame() {
        long t = pickBest() / 1000;
        ArrayList<Long> accVidCalib = new ArrayList<>();
        ArrayList<Long> accGyroCalib = new ArrayList<>();

        try {
            File f = new File(activity.getExternalFilesDir(null), "calibrate.csv");
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                accVidCalib.add(Long.parseLong(data[0]));
                accGyroCalib.add(Long.parseLong(data[1]));
            }
        } catch (IOException e) {
            Log.e("Error CSV", e.getMessage());
            return;
        }

        for (int i = 0; i < accVidCalib.size(); i++) {
            accVidSync += accVidCalib.get(i);
            accGyroSync += accGyroCalib.get(i);
        }

        accVidSync = accVidSync / accVidCalib.size();
        accGyroSync = accGyroSync / accGyroCalib.size();

        accVidSync = accVidSync / 1000;

        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        try {
            File f = new File(activity.getExternalFilesDir(null), "best-unsync.png");
            FileOutputStream out = new FileOutputStream(f);
            Bitmap bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            File f2 = new File(activity.getExternalFilesDir(null), "best-sync.png");
            out = new FileOutputStream(f2);
            bmp = retriever.getFrameAtTime(t - accVidSync, MediaMetadataRetriever.OPTION_CLOSEST);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the timestamp at which the video has least movement
     *
     * @return timestamp at which the video has least movement
     */
    public long pickBest() {
        long initial = accelData.get(0).first;
        Collections.sort(accelData, new Comparator<Pair<Long, float[]>>() {
            @Override
            public int compare(Pair<Long, float[]> lhs, Pair<Long, float[]> rhs) {
                return Double.compare(val2(lhs.second), val2(rhs.second));
            }
        });

        return findBestTimestamp(accelData) - initial;
    }

    /**
     * Returns the timestamp at which the video has least movement
     * The accelerometer data corresponding to least movement, and those within a certain percentage of this value is chosen.
     * For these accelerometer data, the corresponding gyroscope data is found.
     * The choice of which timestamp to pick is then ranked using both accelerometer and gyroscope data.
     *
     * Through experimentation, we found linear movement to contribute more to blur, and thus, the
     * accelerometer value contributes more to the choice of timestamp
     *
     * @param accel accelerometer data sorted in ascending order by magnitude
     * @return
     */
    public Long findBestTimestamp(ArrayList<Pair<Long, float[]>> accel) {
        ArrayList<Pair<Long, Double>> bestAccelData = new ArrayList<Pair<Long, Double>>();
        ArrayList<Double> bestGyroData = new ArrayList<Double>();

        bestAccelData.add(new Pair<Long, Double>(accel.get(0).first, val(accel.get(0).second)));
        for (int i = 1; i < accel.size(); i++) {
            double v = val(accel.get(i).second);
            if (v / bestAccelData.get(0).second < 0.1) {
                bestAccelData.add(new Pair<Long, Double>(accel.get(i).first, v));
            } else {
                break;
            }
        }

        for (int i = 0; i < bestAccelData.size(); i++) {
            long diff = Long.MAX_VALUE;
            for (int j = 0; j < gyroData.size(); j++) {
                long d = Math.abs(bestAccelData.get(i).first - gyroData.get(j).first - accGyroSync);
                if (d <= diff) {
                    diff = d;
                } else {
                    bestGyroData.add(val2(gyroData.get(j).second));
                    break;
                }
            }
        }

        double minVal = Double.MAX_VALUE;
        int minIndex = 0;
        for (int i = 0; i < bestAccelData.size(); i++) {
            double v = bestAccelData.get(i).second + 0.5 * bestGyroData.get(i);
            if (v < minVal) {
                minVal = v;
                minIndex = i;
            }
        }

        return bestAccelData.get(minIndex).first;
    }


    public double val(float[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    public double val2(float[] v) {
        return Math.abs(v[0]) + Math.abs(v[1]) + Math.abs(v[2]);
    }
}
