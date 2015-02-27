package github.luv.mockgeofix.command;

import android.content.Context;

import java.nio.channels.SocketChannel;

public class GeoFixCommand implements Command {
    Context mContext;
    GeoFixCommand(Context context) {
        mContext = context;
    }

    @Override
    public void execute(SocketChannel client, String command) {

    }
}
