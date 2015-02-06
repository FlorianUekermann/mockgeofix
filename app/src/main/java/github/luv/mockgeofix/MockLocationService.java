package github.luv.mockgeofix;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class MockLocationService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    String TAG = "MockLocationService";

    protected MockLocationThread mThread = null;
    SharedPreferences pref = null;

    public final static String STARTED = "STARTED";
    public final static String STOPPED = "STOPPED";
    public final static String ERROR = "ERROR";

    protected int port = 5554;

    protected String lastErr;

    public class Binder extends android.os.Binder {
        MockLocationService getService() {
            return MockLocationService.this;
        }
    }

    private final IBinder mBinder = new Binder();

    @Override
    public void onCreate() {
        super.onCreate();
        pref = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());

        updatePort();
        pref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("listen_port")) {
            updatePort();
        }
    }

    protected synchronized void updatePort() {
        if (pref == null) {return;}
        try {
            port = Integer.parseInt(pref.getString("listen_port", ""));
            if (port < 0 || port > 65535) {
                throw new NumberFormatException("Invalid port number.");
            }
        } catch (NumberFormatException ex) {
            String portStr = Integer.toString(port);
            Log.e(TAG, String.format("Invalid port number %s. Defaulting to 5554", portStr));
            errorHasOccurred(String.format("Invalid port number %s. Defaulting to 5554.",
                    portStr));
            port = 5554;
        }
        if (mThread != null) {
            mThread.updatePort();
        }
    }

    /* interface to be used by clients */
    public boolean isRunning() {
        return !(mThread == null);
    }

    public String getLastErr() { return lastErr; }

    public void start() {
        if (mThread == null) {
            mThread = new MockLocationThread(getApplicationContext(), this);
            mThread.start();
            broadcast(STARTED);
        }
    }

    public void stop() {
        if (mThread == null) { return; }
        mThread.kill();
        mThread.interrupt();
    }
    /* end of interface */

    /* methods used my MockLocationThread
       to communicate its' state */
    protected void threadHasStopped() {
        mThread = null;
        broadcast(STOPPED);
    }

    protected void threadHasStartedSuccessfully() {
        broadcast(STARTED);
    }

    protected void errorHasOccurred(String err) {
        lastErr = err;
        broadcast(ERROR);
    }

    /* helper methods */
    protected void broadcast(String msg) {
        Intent i = new Intent();
        i.setAction(msg);
        sendBroadcast(i);
    }
}

class MockLocationThread extends Thread {
    Context mContext;
    MockLocationService mService;

    public MockLocationThread(Context context, MockLocationService service) {
        mContext = context;
        mService = service;
    }
    private boolean stop = false;

    public void kill() {
        stop = true;
    }

    public void updatePort() {
        // bind to the new port
    }

    @SuppressWarnings({"InfiniteLoopStatement", "EmptyCatchBlock"})
    @Override
    public void run() {
        super.run();
        try {
            // bind to the tcp port and only on success call:
            //mService.errorHasOccurred("TEST ERR");
            mService.threadHasStartedSuccessfully();
            while (!stop) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
                String port = pref.getString("listen_port", "");
                Log.d(String.format("PORT %s", port), "running");
                try {
                    Thread.sleep(1000, 0);
                } catch (InterruptedException ex) {
                }
            }
        } finally {
            mService.threadHasStopped();
        }
    }
}
