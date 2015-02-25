package github.luv.mockgeofix.util;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class ResponseWriter {
    static String TAG = "ResponseWriter";

    static Charset charset = Charset.forName("UTF-8");
    static CharsetEncoder encoder = charset.newEncoder();

    public static boolean write(SocketChannel client, String response) {
        try {
            ByteBuffer r = encoder.encode(CharBuffer.wrap(response));
            while (r.hasRemaining()) {
                client.write(r);
            }
            return true;
        } catch (CharacterCodingException ex) {
            Log.e(TAG, "CharacterCodingException suppressed");
            return false;
        } catch (IOException ex) {
            try { client.close(); } catch (IOException ignored) {}
            return false;
        }
    }

    public static boolean writeLine(SocketChannel client, String response) {
        return write(client, response+"\r\n");
    }

    public static boolean ok(SocketChannel client) {
        return write(client, "OK\r\n");
    }

    public static boolean unknownCommand(SocketChannel client) {
        return write(client, "KO: unknown command, try 'help'\r\n");
    }

    public static boolean notLoggedIn(SocketChannel client) {
        return write(client, "KO: password required. You must log in first. Use 'password'\r\n");
    }
}
