package github.luv.mockgeofix.command;

import android.content.Context;

import java.nio.channels.SocketChannel;

public class GeoNmeaCommand implements Command {
    Context mContext;
    GeoNmeaCommand(Context context) {
        mContext = context;
    }

    @Override
    public void execute(SocketChannel client, String command) {

    }
}
