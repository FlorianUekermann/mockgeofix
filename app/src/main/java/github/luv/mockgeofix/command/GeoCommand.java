package github.luv.mockgeofix.command;

import android.content.Context;

import java.nio.channels.SocketChannel;

import github.luv.mockgeofix.util.ResponseWriter;

public class GeoCommand implements Command {
    protected GeoFixCommand mFixCommand;
    protected GeoNmeaCommand mNmeaCommand;
    protected Context mContext;

    public GeoCommand(Context context) {
        mFixCommand = new GeoFixCommand(context);
        mNmeaCommand = new GeoNmeaCommand(context);
        mContext = context;
    }

    @Override
    public void execute(SocketChannel client, String command) {
        String[] args = command.split(" ");
        try {
            if ( args[1].equals("fix") ) {
                mFixCommand.execute(client, command);
            } else if (args[1].equals("nmea")) {
                mNmeaCommand.execute(client, command);
            } else {
                StringBuilder r = new StringBuilder();
                r.append("allows you to change Geo-related settings, or to send GPS NMEA sentences\r\n\r\n");
                r.append("available sub-commands:\r\n");
                r.append("    nmea             send an GPS NMEA sentence\r\n");
                r.append("    fix              send a simple GPS fix\r\n\r\n");
                r.append("KO:  bad sub-command\r\n");
                ResponseWriter.write(client, r.toString());
            }
        } catch(IndexOutOfBoundsException ex) {
            StringBuilder r = new StringBuilder();
            r.append("allows you to change Geo-related settings, or to send GPS NMEA sentences\r\n\r\n");
            r.append("available sub-commands:\r\n");
            r.append("    nmea             send an GPS NMEA sentence\r\n");
            r.append("    fix              send a simple GPS fix\r\n\r\n");
            r.append("KO: missing sub-command\r\n");
            ResponseWriter.write(client, r.toString());
        }
    }
}
