package github.luv.mockgeofix;

import android.app.Application;
import android.content.Intent;
import android.preference.PreferenceManager;

public class MockGeoFixApp extends Application {
    public boolean enableMockLocationDialogShown = false;
    public boolean openLocationSourceSettingsDialogShown = false;
    @Override
    public void onCreate() {
        PreferenceManager.setDefaultValues(this, R.xml.pref, false);
        CommandDispatcher.init(getApplicationContext());
        MockLocationProvider.init(getApplicationContext());
        Intent i = new Intent(getApplicationContext(), MockLocationService.class);
        startService(i);
    }
}