package github.luv.mockgeofix.command;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.nio.channels.SocketChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import github.luv.mockgeofix.MockLocationProvider;
import github.luv.mockgeofix.util.ResponseWriter;

public class GeoNmeaCommand implements Command {
    Context mContext;
    String TAG = "GeoNmeaCommand";

    GeoNmeaCommand(Context context) {
        mContext = context;
    }

    @Override
    public void execute(SocketChannel client, String command) {
        String[] args = command.split(" ");
        String[] nmeaFields;

        try {
            nmeaFields = args[2].split(",");
        } catch (IndexOutOfBoundsException ex) {
            ResponseWriter.writeLine(client,
                    "KO: NMEA sentence missing, try 'help geo nmea'");
            return;
        }

        String type;
        try {
            type = nmeaFields[0];
        } catch (IndexOutOfBoundsException ex) {
            // ok because that's what the emulator does as well
            ResponseWriter.ok(client);
            return;
        }

        if (type.equals("$GPRMC")) {
            processGPRMC(client, nmeaFields, command);
        } else if (type.equals("$GPGGA")) {
            processGPGGA(client, nmeaFields, command);
        } else {
            // ok because that's what the emulator does as well
            ResponseWriter.ok(client);
        }
    }

    public void processGPRMC(SocketChannel client, String[] nmeaFields, String command) {
        // example: $GPRMC,081836,A,3751.65,S,14507.36,E,000.0,360.0,130998,011.3,E*62
        /* example fields:
        225446       Time of fix 22:54:46 UTC
        A            Status A=active or V=Void
        4916.45,N    Latitude 49 deg. 16.45 min North
        12311.12,W   Longitude 123 deg. 11.12 min West
        000.5        Speed over ground, Knots
        054.7        Track angle in degrees, True
        191194       Date of fix  19 November 1994
        020.3,E      Magnetic variation 20.3 deg East
        optional field:  mode indicator
                (can be A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator)
        *68          mandatory checksum
        */

        String strTime, strStatus, strLatitude, strLatitudeQual, strLongitude, strLongitudeQual,
                strSpeed, strTrackAngle, strDate;
        try {
            strTime = nmeaFields[1];
            strStatus = nmeaFields[2];
            strLatitude = nmeaFields[3];
            strLatitudeQual = nmeaFields[4];
            strLongitude = nmeaFields[5];
            strLongitudeQual = nmeaFields[6];
            strSpeed = nmeaFields[7];
            strTrackAngle = nmeaFields[8];
            strDate = nmeaFields[9];
        } catch (IndexOutOfBoundsException ex) {
            // ok because that's what the emulator does as well
            Log.i(TAG, "Ignoring Nmea sentence: too few fields: "+command);
            ResponseWriter.ok(client);
            return;
        }

        if (!checkChecksum(command)) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: invalid checksum: "+command);
            return;
        }

        if (strStatus.toLowerCase().equals("v")) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Status is 'V' (void): "+command);
            return;
        }

        long timestamp;
        double latitude;
        double longitude;
        float speed;
        float bearing;
        try {
            timestamp = convertTimeAndDate(strTime, strDate);
        } catch(ParseException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse date or time: "+command);
            return;
        }

        try {
            latitude = convertLatitude(strLatitude, strLatitudeQual);
            longitude = convertLongitude(strLongitude, strLongitudeQual);
        } catch(NumberFormatException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse latitude or longitude: "+command);
            return;
        }

        try {
            speed = Float.valueOf(strSpeed) * (float)0.51444;
        } catch(NumberFormatException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse speed: "+command);
            return;
        }

        try {
            // this might be completely wrong
            bearing = Float.valueOf(strTrackAngle);
        } catch(NumberFormatException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse track angle: "+command);
            return;
        }

        Location location = MockLocationProvider.getLocation();
        location.setTime(timestamp);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setSpeed(speed);
        location.setBearing(bearing);

        MockLocationProvider.simulate(location);
        Log.d(TAG, "nmea sentence processed (lat, long: "+String.valueOf(latitude)+
                " , "+String.valueOf(longitude)+")");

        ResponseWriter.ok(client);
    }

    public void processGPGGA(SocketChannel client, String[] nmeaFields, String command) {
        ResponseWriter.writeLine(client, "KO: Not Implemented Yet");
    }

    public boolean checkChecksum(String command) {
        int from = command.indexOf("$");
        int to = command.indexOf("*");
        if (from == -1 || to == -1) {
            return false;
        }
        String checkString = command.substring(from+1,to);
        String checksum = command.substring(to+1);
        int sum = 0;
        for (int i=0; i<checkString.length(); i++) {
            sum ^= (int)( checkString.charAt(i) );
        }
        String hexsum = Integer.toHexString(sum);
        return hexsum.toLowerCase().equals(checksum.toLowerCase());
    }

    public long convertTimeAndDate(String time, String date) throws ParseException {
        //  UTC time, in milliseconds since January 1, 1970.

        // suppressed because we set the timezone using setTimeZone in next step
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sf = new SimpleDateFormat("HHmmss ddMMyy");
        sf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = sf.parse(time+" "+date);
        return d.getTime();
    }

    public double convertLatitude(String strLatitude, String qual) throws NumberFormatException {
        double lat = Double.valueOf(strLatitude) / 100;
        if (qual.equals("S")) {
            lat *= -1;
        }
        return lat;
    }

    public double convertLongitude(String strLongitude, String qual) throws NumberFormatException {
        double longitude = Double.valueOf(strLongitude) / 100;
        if (qual.equals("W")) {
            longitude *= -1;
        }
        return longitude;
    }
}