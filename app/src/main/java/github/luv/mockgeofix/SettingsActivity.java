package github.luv.mockgeofix;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;


@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {
    static final String TAG = "SettingsActivity";

    private MockLocationService mService = null;
    private boolean mInitialBinding = true;
    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((MockLocationService.Binder)binder).getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    protected void onDestroy() {
        if (mService != null && mConn != null) {
            unbindService(mConn);
        }
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        bindService(new Intent(getApplicationContext(),MockLocationService.class),
                mConn,
                Context.BIND_AUTO_CREATE
        );
        setupSimplePreferencesScreen();
        mInitialBinding = true;
        bindPreference(findPreference("listen_port"));
        bindPreference(findPreference("listen_ip"));
        bindPreference(findPreference("accuracy"));
        bindPreference(findPreference("password"));
        bindPreference(findPreference("require_password"));
        mInitialBinding = false;
    }


    private void setupSimplePreferencesScreen() {
        addPreferencesFromResource(R.xml.pref);

        ListPreference listen_ip = (ListPreference)findPreference("listen_ip");
        HashMap<String, String> entries = new HashMap<>();

        try{
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                String key = iface.getDisplayName();
                String desc = "(";
                int addressCount = 0;
                try {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        addressCount += 1;
                        InetAddress address = addresses.nextElement();
                        String strAddress = address.getHostAddress();
                        strAddress = strAddress.split("%",2)[0];
                        desc += String.format("%s", strAddress);
                        if (addresses.hasMoreElements()) {
                            desc += ", ";
                        }
                    }
                } catch (NullPointerException ex) {
                    Log.e(TAG, "NullPointerException thrown (address enumeration)");
                }
                desc += ")";
                if (addressCount > 0) {
                    entries.put(key, desc);
                }
            }
        } catch(SocketException ex) {
            Log.e(TAG, "SocketException thrown (network interface enumeration)");
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException thrown (network interface enumeration)");
        }

        String [] prefEntries = new String[entries.size()+1];
        String [] prefEntryValues = new String[entries.size()+1];
        int i = 0;
        prefEntries[i] = "ALL";
        prefEntryValues[i] = "ALL";
        i += 1;
        for (String key : entries.keySet()) {
            prefEntries[i] = key;
            prefEntryValues[i] = key + " " + entries.get(key);
            i += 1;
        }
        listen_ip.setEntries(prefEntryValues);
        listen_ip.setEntryValues(prefEntries);
    }

    public void bindPreference(Preference pref) {
        pref.setOnPreferenceChangeListener(this);
        Object currentValue;
        if (pref instanceof CheckBoxPreference) {
            currentValue = PreferenceManager.getDefaultSharedPreferences(pref.getContext())
                    .getBoolean(pref.getKey(), false);
        } else {
            currentValue = PreferenceManager.getDefaultSharedPreferences(pref.getContext())
                    .getString(pref.getKey(), "");
        }
        onPreferenceChange(pref, currentValue);
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object value) {
        String stringValue = value.toString();

        /* validation */
        if (pref.getKey().equals("listen_port")) {
            try {
                int port = Integer.parseInt(stringValue);
                if (port < 0 || port > 65535) {
                    throw new NumberFormatException("Invalid port number.");
                }
            } catch (NumberFormatException ex) {
                Log.i(TAG, String.format("Invalid port number %s. Not Changing.",stringValue) );
                Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_LONG).show();
                return false;
            }
        }

        /* set summary in the views*/
        if (pref.getKey().equals("require_password")) {
            Boolean boolValue = (Boolean)value;
            if (boolValue == Boolean.FALSE) {
                pref.setSummary("");
            } else {
                pref.setSummary(getResources().getString(R.string.require_password_summary));
            }
        } else if (pref instanceof  ListPreference) {
            ListPreference listPreference = (ListPreference) pref;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            pref.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);
        } else if (pref instanceof EditTextPreference) {
            EditTextPreference textPreference = (EditTextPreference) pref;
            if ( (textPreference.getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    == InputType.TYPE_TEXT_VARIATION_PASSWORD ) {
                pref.setSummary( stringValue.replaceAll(".","*") );
            } else {
                pref.setSummary(stringValue);
            }
        }

        /* Warn the user that the change won't take effect until the service is restarted */
        if (pref.getKey().equals("listen_port") || pref.getKey().equals("listen_ip")) {
            if ( ! mInitialBinding && (mService != null && mService.isRunning()) ) {
                Toast.makeText(getApplicationContext(), getString(R.string.note_needsreset),
                        Toast.LENGTH_LONG).show();
            }
        }

        return true;
    }
}
