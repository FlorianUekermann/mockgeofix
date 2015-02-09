package github.luv.mockgeofix;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Vector;

public class MockLocationService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    String TAG = "MockLocationService";

    protected MockLocationThread mThread = null;
    protected SharedPreferences pref = null;

    public final static String STARTED = "STARTED";
    public final static String STOPPED = "STOPPED";
    public final static String ERROR = "ERROR";
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
        if (key.equals("listen_port") || key.equals("listen_ip")) {
            if (mThread != null) {
                mThread.rebind();
                mThread.interrupt();
            }
        }
    }

    /* interface to be used by clients */
    public boolean isRunning() {
        return !(mThread == null);
    }

    public String getLastErr() { return lastErr; }

    public void start() {
        if (mThread == null) {
            mThread = new MockLocationThread(getApplicationContext(), this );
            mThread.start();
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
    protected Vector<SocketAddress> getBindAddresses() {
        Vector<SocketAddress> ret = new Vector<>();

        /* retrieve port settings from preferences */
        String portStr = pref.getString("listen_port", "");
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 0 || port > 65535) {
                throw new NumberFormatException("Invalid port number.");
            }
        } catch (NumberFormatException ex) {
            Log.e(TAG, String.format("Invalid port number %s. Defaulting to 5554", portStr));
            errorHasOccurred(String.format("Invalid port number %s. Defaulting to 5554.",
                    portStr));
            port = 5554;
        }

        /* retrieve interfaces to listen on, set port and return a vector of SocketAddresses */
        String ifaceName = pref.getString("listen_ip","ALL");

        if (ifaceName.equals("ALL")) {
            ret.add( new InetSocketAddress(port) );
            return ret;
        }

        try {
            NetworkInterface iface = NetworkInterface.getByName(ifaceName);
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                ret.add(new InetSocketAddress(address, port));
            }
        } catch(SocketException ex) {
            Log.e(TAG, "SocketException thrown (getBindAddress)");
        } catch (NullPointerException ex) {
            Log.e(TAG, "NullPointerException thrown (getBindAddress)");
        }
        return ret;
    }

    /* helper methods */
    protected void broadcast(String msg) {
        Intent i = new Intent();
        i.setAction(msg);
        sendBroadcast(i);
    }
}

class MockLocationThread extends Thread {
    String TAG = "MockLocationService.Thread";

    protected Context mContext;
    protected MockLocationService mService;
    protected Vector<SocketAddress> mBindAddresses;

    public MockLocationThread(Context context, MockLocationService service) {
        mContext = context;
        mService = service;
    }
    private boolean mStop = false;
    private boolean mRebind = false;

    public void rebind() {
        mRebind = true;
    }

    public void kill() {
        mStop = true;
    }

    @SuppressWarnings({"InfiniteLoopStatement", "EmptyCatchBlock"})
    @Override
    public void run() {
        super.run();
        Vector<ServerSocket> ssockets = new Vector<>();
        try {
            while (!mStop) {
                mBindAddresses = mService.getBindAddresses();
                for (SocketAddress address : mBindAddresses) {
                    ServerSocket ss = new ServerSocket();
                    ss.setReuseAddress(true);
                    ss.bind(address);
                    ssockets.add(ss);
                }
                mService.threadHasStartedSuccessfully();
                try {
                    while (!mStop && !mRebind) {
                        Log.d(TAG, "running");
                        try {
                            Thread.sleep(1000, 0);
                        } catch (InterruptedException ex) {
                        }
                    }
                } finally {
                    for (ServerSocket ss : ssockets) {
                        try {
                            if (!ss.isClosed()) { ss.close(); }
                        } catch (IOException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                    ssockets.clear();
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, e.toString() );
            // most common errors
            if (e.getClass() == BindException.class && e.getMessage().contains("EADDRINUSE")) {
                mService.errorHasOccurred(mContext.getString(R.string.err_address_already_in_use));
            } else if (e.getClass() == BindException.class && e.getMessage().contains("EACCES") ) {
                mService.errorHasOccurred(mContext.getString(R.string.err_socket_permission_denied));
            } else {
                mService.errorHasOccurred(e.toString());
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString() );
            mService.errorHasOccurred(e.toString());
        } finally {
            for (ServerSocket ss : ssockets) {
                try {
                    if (!ss.isClosed()) { ss.close(); }
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
            }
            mService.threadHasStopped();
        }
    }
}
