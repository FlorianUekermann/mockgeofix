package github.luv.mockgeofix.command;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.channels.SocketChannel;
import java.util.WeakHashMap;

import github.luv.mockgeofix.util.ResponseWriter;

public class PasswordCommand implements Command {
    static String TAG = "PasswordCommand";

    protected SharedPreferences pref = null;
    protected WeakHashMap<SocketChannel, Boolean> mIsLoggedIn = new WeakHashMap<>();

    public PasswordCommand(Context context) {
        pref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void execute(SocketChannel client, String command) {
        if (pref == null) {
            Log.e(TAG, "internal error: pref is null");
            ResponseWriter.writeLine(client, "KO: Internal Error in 'PasswordCommand'.");
            return;
        }
        String password;
        try {
            password = command.split(" ", 2)[1];
        } catch (ArrayIndexOutOfBoundsException ex) {
            password = "";
        }
        String storedPassword = pref.getString("password","");
        if ( ! pref.getBoolean("require_password",false)) {
            ResponseWriter.writeLine(client, "KO: Password is not required.");
        } else {
            if (password.equals(storedPassword)) {
                mIsLoggedIn.put(client, Boolean.TRUE);
                ResponseWriter.ok(client);
            } else {
                mIsLoggedIn.put(client, Boolean.FALSE);
                ResponseWriter.writeLine(client, "KO: Incorrect password.");
            }
        }
    }

    public boolean loggedIn(SocketChannel client) {
        if ( mIsLoggedIn.get(client) == Boolean.TRUE ) {
            return true;
        } else {
            return false;
        }
    }

    public boolean passwordRequired() {
        if (pref == null) {
            Log.e(TAG, "internal error: pref is null");
            // later on we blow up and send "KO: Internal Error in 'PasswordCommand'."
            // to client.
            // Unless pref is set in the meantime, than things get weird and
            // hard to debug :). But as pref is set only in the constructor, we should be fine.
            return true;
        }
        return pref.getBoolean("require_password",false);
    }
}
