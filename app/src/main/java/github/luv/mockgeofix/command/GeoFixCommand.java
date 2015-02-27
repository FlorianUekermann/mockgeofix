package github.luv.mockgeofix.command;

import android.content.Context;

import java.nio.channels.SocketChannel;

import github.luv.mockgeofix.MockLocationProvider;
import github.luv.mockgeofix.util.ResponseWriter;

public class GeoFixCommand implements Command {
    Context mContext;
    GeoFixCommand(Context context) {
        mContext = context;
    }

    @Override
    public void execute(SocketChannel client, String command) {
        String[] args = command.split(" ");
        double longitude;
        double latitude;
        double altitude = 0.0;
        boolean altitudeSet = false;
        int satellites = 0;
        boolean satellitesSet = false;
        try {
            longitude = Double.valueOf(args[2]);
            latitude = Double.valueOf(args[3]);
        } catch(IndexOutOfBoundsException ex) {
            ResponseWriter.writeLine(client,
                    "KO: not enough arguments: see 'help geo fix' for details");
            return;
        } catch(NumberFormatException ex) {
            ResponseWriter.writeLine(client,"KO: argument is not a number");
            return;
        }
        try {
            altitude = Double.valueOf(args[4]);
            altitudeSet = true;
            satellites = Integer.valueOf(args[5]);
            satellitesSet = true;
        } catch (NumberFormatException ex) {
            ResponseWriter.writeLine(client,"KO: argument is not a number");
            return;
        } catch (IndexOutOfBoundsException ignored) {}
        if (satellitesSet && (satellites < 1 || satellites > 12) ) {
            ResponseWriter.writeLine(client,
                    "KO: invalid number of satellites. Must be an integer between 1 and 12");
            return;
        }
        if (! altitudeSet && ! satellitesSet) {
            MockLocationProvider.simulate(longitude, latitude);
            ResponseWriter.ok(client);
        } else if (altitudeSet && ! satellitesSet) {
            MockLocationProvider.simulate(longitude, latitude, altitude);
            ResponseWriter.ok(client);
        } else {
            MockLocationProvider.simulate(longitude, latitude, altitude, satellites);
            ResponseWriter.ok(client);
        }
    }
}
