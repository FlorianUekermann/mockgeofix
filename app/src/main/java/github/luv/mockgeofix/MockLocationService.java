package github.luv.mockgeofix;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

import java.net.SocketAddress;
import java.net.SocketException;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
        try {
            while (!mStop) {
                Selector selector = Selector.open();
                mBindAddresses = mService.getBindAddresses();
                for (SocketAddress address : mBindAddresses) {
                    ServerSocketChannel ssc = ServerSocketChannel.open();
                    ssc.configureBlocking(false);
                    ssc.socket().setReuseAddress(true);
                    ssc.socket().bind(address);
                    ssc.register(selector, SelectionKey.OP_ACCEPT);
                }
                mService.threadHasStartedSuccessfully();
                try {
                    while (!mStop && !mRebind) {
                        Log.d(TAG, "running");
                        selector.select();
                        for (SelectionKey key : selector.selectedKeys()) {
                            if (key.isAcceptable() && key.channel() instanceof ServerSocketChannel) {
                                // accept connection
                                SocketChannel client = ((ServerSocketChannel)key.channel()).accept();
                                if (client != null) {
                                    client.configureBlocking(false);
                                    client.socket().setTcpNoDelay(true);
                                    client.register(selector, SelectionKey.OP_READ);
                                }
                            }
                            if (key.isReadable() && key.channel() instanceof SocketChannel) {
                                onIncomingData((SocketChannel) key.channel());
                            }
                        }
                        selector.selectedKeys().clear();
                    }
                } finally {
                    for (SelectionKey key : selector.keys()) {
                        try { key.channel().close(); } catch (IOException ignored) {}
                    }
                    selector.close();
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, e.toString() );
            // most common errors
            if (e.getClass() == BindException.class && e.getMessage().contains("EADDRINUSE")) {
                mService.errorHasOccurred(mContext.getString(R.string.err_address_already_in_use));
            } else if (e.getClass() == BindException.class && e.getMessage().contains("EACCES") ) {
                mService.errorHasOccurred(mContext.getString(R.string.err_socket_permission_denied));
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString() );
        } finally {
            mService.threadHasStopped();
        }
    }

    void onIncomingData(SocketChannel client) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException ex) {
            try { client.close(); } catch (IOException ignored) {}
            return;
        }
        if (bytesRead == -1) {
            try { client.close(); } catch (IOException ignored) {}
            return;
        }
        try {
            String s = new String(buffer.array(), "UTF-8");
            Log.i(TAG, s);
        } catch(UnsupportedEncodingException ignored) {}
    }
}