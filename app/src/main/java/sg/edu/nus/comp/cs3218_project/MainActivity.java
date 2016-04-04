package sg.edu.nus.comp.cs3218_project;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void goToRecord(View view) {
        Intent recordIntent = new Intent(this, RecordActivity.class);
        startActivity(recordIntent);
    }

    public void goToCalibrate(View view) {
        Intent calibrateIntent = new Intent(this, CalibrateActivity.class);
        startActivity(calibrateIntent);
    }
}
