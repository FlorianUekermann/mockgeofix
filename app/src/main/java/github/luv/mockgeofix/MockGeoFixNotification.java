package github.luv.mockgeofix;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class MockGeoFixNotification {
    static String TAG = "MockGeoFixNotification";

    static private MockGeoFixNotification instance = new MockGeoFixNotification();
    static public MockGeoFixNotification getInstance() { return instance; }
    private MockGeoFixNotification() {}

    protected Context mContext;
    protected int mNotificationId = 1;
    protected NotificationCompat.Builder mBuilder;
    protected NotificationManager mNotifyMgr;

    static public void init(Context context) {
        getInstance()._init(context);
    }

    static public void show() {
        getInstance()._verifyInitiated();
        getInstance()._show();
    }

    static public void close() {
        getInstance()._verifyInitiated();
        getInstance()._close();
    }

    protected void _init(Context context) {
        if (mContext != null) {
            throw new AssertionError(TAG+".init called twice!");
        }
        mContext = context;
        mNotifyMgr =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationId = 1;
        mBuilder = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(mContext.getString(R.string.notification_text))
                .setOngoing(true);

        Intent resultIntent = new Intent(mContext, MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        mContext,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(pendingIntent);
    }

    protected void _show() {
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    protected void _close() {
        mNotifyMgr.cancel(1);
    }

    private void _verifyInitiated() {
        if (mContext == null) {
            throw new AssertionError(TAG+".init has not been called!");
        }
    }
}