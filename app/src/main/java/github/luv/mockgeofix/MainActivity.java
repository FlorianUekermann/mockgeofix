package github.luv.mockgeofix;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Vector;

import github.luv.mockgeofix.dialog.EnableMockLocationDialogFragment;
import github.luv.mockgeofix.dialog.OpenLocationSourceSettingsDialogFragment;

public class MainActivity extends ActionBarActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    static String TAG = "MainActivity";
    TextView mTextStatus;
    TextView mDescription;
    TextView mIPs;
    Button mStartStopButton;

    SharedPreferences pref = null;

    private MockLocationService mService = null;
    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((MockLocationService.Binder)binder).getService();
            MainActivity.this.onServiceConnected();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MockLocationService.STARTED) ||
                    action.equals(MockLocationService.STOPPED)) {
                MainActivity.this.updateStatus();
                MainActivity.this.updateStartStopButton();
                // in case an interface goes down or up
                MainActivity.this.updateListensOn();
            }
            if (action.equals(MockLocationService.ERROR) && mService != null) {
                Toast.makeText(getApplicationContext(), mService.getLastErr(),
                               Toast.LENGTH_LONG).show();
            }
        }
    };

    public void onServiceConnected() {
        MainActivity.this.updateStatus();
        MainActivity.this.updateStartStopButton();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("listen_port")) {
            updateDescription();
        } else if (key.equals("listen_ip")) {
            updateListensOn();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        pref.registerOnSharedPreferenceChangeListener(this);

        mTextStatus = (TextView)findViewById(R.id.text_status);
        mStartStopButton = (Button)findViewById(R.id.startstop_button);
        mDescription = (TextView)findViewById(R.id.description);
        mIPs = (TextView)findViewById(R.id.ips);

        updateDescription();
        updateListensOn();

        bindService(new Intent(getApplicationContext(),MockLocationService.class),
                    mConn,
                    Context.BIND_AUTO_CREATE
                );
        registerReceiver(receiver, new IntentFilter(MockLocationService.STARTED));
        registerReceiver(receiver, new IntentFilter(MockLocationService.STOPPED));
        registerReceiver(receiver, new IntentFilter(MockLocationService.ERROR));

        // show a dialog when "allow mock location" is not enabled
        if (Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ALLOW_MOCK_LOCATION).equals("0")) {
            if ( ! ((MockGeoFixApp)getApplication()).enableMockLocationDialogShown ) {
                ((MockGeoFixApp)getApplication()).enableMockLocationDialogShown = true;
                (new EnableMockLocationDialogFragment()).show(getSupportFragmentManager(),
                        "enable_mock_location_dialog");
            }
        }

        // show a dialog when other location providers apart from the GPS one are enabled
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        for (String provider : locationManager.getAllProviders()) {
            if (locationManager.isProviderEnabled(provider) &&
                    !provider.equals(LocationManager.PASSIVE_PROVIDER) &&
                    !provider.equals(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(getApplicationContext(),
                    String.format(getString(R.string.provider_enabled), provider), Toast.LENGTH_LONG).show();
            }
        }

        if (! ((MockGeoFixApp)getApplication()).openLocationSourceSettingsDialogShown ) {
            if ( ! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                 || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                ((MockGeoFixApp) getApplication()).openLocationSourceSettingsDialogShown = true;
                (new OpenLocationSourceSettingsDialogFragment()).show(getSupportFragmentManager(),
                        "open_location_source_settings_dialog");
            }
        }

    }

    @Override
    public void onBackPressed() {
        ((MockGeoFixApp)getApplication()).enableMockLocationDialogShown = false;
        ((MockGeoFixApp)getApplication()).openLocationSourceSettingsDialogShown= false;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (mService != null && mConn != null) {
            unbindService(mConn);
        }
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    protected synchronized  void updateListensOn() {
        if (pref == null || mIPs == null) { return; }
        mIPs.setText("");
        String ifaceName = pref.getString("listen_ip","ALL");

        boolean first = true;
        Enumeration<NetworkInterface> ifaces;
        try {
            if (ifaceName.equals("ALL")) {
                ifaces = NetworkInterface.getNetworkInterfaces();
            } else {
                Vector<NetworkInterface> v = new Vector<>();
                NetworkInterface iface = NetworkInterface.getByName(ifaceName);
                if (iface != null) {
                    v.add(iface);
                }
                ifaces = v.elements();
            }
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                try {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        String strAddress = address.getHostAddress();
                        strAddress = strAddress.split("%",2)[0];
                        if (first) {
                            first = false;
                        } else {
                            mIPs.append("\n");
                        }
                        mIPs.append(strAddress);
                    }
                } catch (NullPointerException ex) {
                    Log.e(TAG, "NullPointerException thrown (address enumeration)");
                }
            }
        } catch(SocketException ex) {
            Log.e(TAG, "SocketException thrown (network interface enumeration)");
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException thrown (network interface enumeration)");
        }
    }

    protected synchronized void updateStatus() {
        if (mService==null) {return;}
        boolean running = mService.isRunning();
        if (running) {
            mTextStatus.setTextColor(Color.GREEN);
            mTextStatus.setText(getString(R.string.running));
        } else {
            mTextStatus.setTextColor(Color.RED);
            mTextStatus.setText(getString(R.string.stopped));
        }
    }

    protected synchronized void updateStartStopButton() {
        if (mService==null) {return;}
        mStartStopButton.setEnabled(true);
        boolean running = mService.isRunning();
        if (running) {
            mStartStopButton.setText(getString(R.string.stop));
            mStartStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mService.stop();
                }
            });
        } else {
            mStartStopButton.setText(getString(R.string.start));
            mStartStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mService.start();
                }
            });
        }
    }

    protected synchronized void updateDescription() {
        if (pref == null || mDescription == null) {return;}
        String port = pref.getString("listen_port","5554");
        mDescription.setText(Html.fromHtml(String.format(getString(R.string.long_description),
                port)));
    }

    public void showPreferences(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}