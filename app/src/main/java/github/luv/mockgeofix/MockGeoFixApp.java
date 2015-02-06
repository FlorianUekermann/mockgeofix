package github.luv.mockgeofix;

import android.app.Application;
import android.content.Intent;

public class MockGeoFixApp extends Application {
    @Override
    public void onCreate() {
        Intent i = new Intent(getApplicationContext(), MockLocationService.class);
        startService(i);
    }
}