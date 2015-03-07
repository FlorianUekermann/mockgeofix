package github.luv.mockgeofix.command;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
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
        Log.d(TAG, "nmea RMC sentence processed (lat, long: "+String.valueOf(latitude)+
                " , "+String.valueOf(longitude)+")");

        ResponseWriter.ok(client);
    }

    public void processGPGGA(SocketChannel client, String[] nmeaFields, String command) {
        /* example: $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
        fields:
        123519       Fix taken at 12:35:19 UTC
        4807.038,N   Latitude 48 deg 07.038' N
        01131.000,E  Longitude 11 deg 31.000' E
        1            Fix quality: 0 = invalid
                                  everything else = valid
        08           Number of satellites being tracked
        0.9          Horizontal dilution of position
        545.4,M      Altitude, Meters, above mean sea level
        46.9,M       Height of geoid (mean sea level) above WGS84
                    ellipsoid
        (empty field) time in seconds since last DGPS update
        (empty field) DGPS station ID number
        *47          the checksum data, always begins with *
        */

        String strTime, strLatitude, strLatitudeQual, strLongitude, strLongitudeQual,
                strQuality, strSatellites, strAltitude, strAltitudeUnits;
        try {
            strTime = nmeaFields[1];
            strLatitude = nmeaFields[2];
            strLatitudeQual = nmeaFields[3];
            strLongitude = nmeaFields[4];
            strLongitudeQual = nmeaFields[5];
            strQuality = nmeaFields[6];
            strSatellites = nmeaFields[7];
            strAltitude = nmeaFields[9];
            strAltitudeUnits = nmeaFields[10];
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

        if (strQuality.equals("0")) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: FixQuality is '0' (invalid): "+command);
            return;
        }

        long timestamp;
        double latitude;
        double longitude;
        int satellites;
        double altitude;
        try {
            timestamp = convertTimeAndDate(strTime, null);
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
            satellites = Integer.valueOf(strSatellites);
        } catch(NumberFormatException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse satellites: "+command);
            return;
        }

        try {
            altitude = convertAltitude(strAltitude, strAltitudeUnits);
        } catch(NumberFormatException ex) {
            ResponseWriter.ok(client);
            Log.i(TAG, "Ignoring Nmea sentence: Can't parse altitude: "+command);
            return;
        }

        Location location = MockLocationProvider.getLocation();
        location.setLatitude(latitude);  // double
        location.setLongitude(longitude);
        location.setAltitude(altitude);
        location.setTime(timestamp);
        // satellites
        Bundle bundle = new Bundle();
        bundle.putInt("satellites", satellites);
        location.setExtras(bundle);

        MockLocationProvider.simulate(location);

        Log.d(TAG, "nmea GGA sentence processed (lat, long: "+String.valueOf(latitude)+
                " , "+String.valueOf(longitude)+")");

        ResponseWriter.ok(client);
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
        String hexsum = String.format("%02X", sum);
        if (checksum.length() < 2) {
            checksum = "0" + checksum;
        }

        return hexsum.toLowerCase().equals(checksum.toLowerCase());
    }

    public long convertTimeAndDate(String time, String date) throws ParseException {
        //  UTC time, in milliseconds since January 1, 1970.
        String timeNew = time.replaceFirst("[.].*","");
        if (date == null) {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date dateObj = new Date();
            date = dateFormat.format(dateObj);
        }
        // SuppressLint because we set the timezone using setTimeZone in next step
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sf = new SimpleDateFormat("HHmmss ddMMyy");
        sf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = sf.parse(timeNew + " " + date);
        return d.getTime();
    }

    public double convertLatitude(String strLatitude, String qual) throws NumberFormatException {
        int dot = strLatitude.indexOf(".");
        double lat;
        if (dot >= 4) {
            // just a sanity check - this should always be true for a valid NMEA coordinates
            double degrees = Double.valueOf( strLatitude.substring(0,dot-2) );
            double minutes = Double.valueOf( strLatitude.substring(dot-2) );
            lat = degrees + minutes / 60;
        } else {
            Log.e(TAG, "convertLatitude - using stupid fallback conversion for: "+strLatitude);
            lat = Double.valueOf(strLatitude) / 100;
        }
        if (qual.toLowerCase().equals("s")) {
            lat *= -1;
        }
        return lat;
    }

    public double convertLongitude(String strLongitude, String qual) throws NumberFormatException {
        int dot = strLongitude.indexOf(".");
        double longitude;
        if (dot >= 4) {
            // just a sanity check - this should always be true for a valid NMEA coordinates
            double degrees = Double.valueOf( strLongitude.substring(0,dot-2) );
            double minutes = Double.valueOf( strLongitude.substring(dot-2) );
            longitude = degrees + minutes / 60;
        } else {
            Log.e(TAG, "convertLongitude - using stupid fallback conversion for: "+strLongitude);
            longitude = Double.valueOf(strLongitude) / 100;
        }
        if (qual.toLowerCase().equals("w")) {
            longitude *= -1;
        }
        return longitude;
    }

    public double convertAltitude(String strAltitude, String units) throws NumberFormatException {
        if (units.toLowerCase().equals("f")) {
            return Double.valueOf(strAltitude) / 3.2808;
        } else if (units.toLowerCase().equals("m")) {
            return Double.valueOf(strAltitude);
        } else {
            return Double.valueOf(strAltitude);
        }
    }
}