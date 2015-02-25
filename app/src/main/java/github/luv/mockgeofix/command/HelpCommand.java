package github.luv.mockgeofix.command;

import java.nio.channels.SocketChannel;

import github.luv.mockgeofix.util.ResponseWriter;

public class HelpCommand implements Command {
    @Override
    public void execute(SocketChannel client, String command) {
        String param;
        try {
            param = command.split(" ", 2)[1].trim();
        } catch(ArrayIndexOutOfBoundsException ex) {
            param = "";
        }
        StringBuilder helpText = new StringBuilder();
        boolean success = true;
        if (param.equals("")) {
            helpText.append("MockGeoFix console command help:\r\n\r\n");
            helpText.append("    help         print a list of commands\r\n");
            helpText.append("    geo          Geo-location commands\r\n");
            helpText.append("    password     login using your password\r\n");
            helpText.append("    quit|exit    quit control session\r\n");
            helpText.append("\r\ntry 'help <command>' for command-specific help\r\n");
        } else if (param.equals("password")) {
            helpText.append("login using your password (set in MockGeoFix Settings screen)\r\n");
        } else if (param.equals("help")) {
            helpText.append("print a list of commands\r\n");
        } else if (param.equals("quit") || param.equals("exit")) {
            helpText.append("quit control session\r\n");
        } else if (param.equals("geo")) {
            helpText.append(
                "allows you to change Geo-related settings, or to send GPS NMEA sentences\r\n\r\n");
            helpText.append("available sub-commands:\r\n");
            helpText.append("    geo nmea             send an GPS NMEA sentence\r\n");
            helpText.append("    geo fix              send a simple GPS fix\r\n\r\n");
        } else if (param.equals("geo nmea")) {
            helpText.append(
              "'geo nema <sentence>' sends a NMEA 0183 sentence to the emulated device, as\r\n");
            helpText.append(
              "if it came from an emulated GPS modem. <sentence> must begin with '$GP'. only\r\n");
            helpText.append(
              "'$GPGGA' and '$GPRCM' sentences are supported at the moment.\r\n");
        } else if (param.equals("geo fix")) {
            helpText.append("'geo fix <longitude> <latitude> [<altitude> [<satellites>]]'\r\n");
            helpText.append(" allows you to send a simple GPS fix to the emulated system.\r\n");
            helpText.append(" The parameters are:\r\n\r\n");
            helpText.append("  <longitude>   longitude, in decimal degrees\r\n");
            helpText.append("  <latitude>    latitude, in decimal degrees\r\n");
            helpText.append("  <altitude>    optional altitude in meters\r\n");
            helpText.append("  <satellites>  number of satellites being tracked (1-12)\r\n\r\n");
        } else if (param.startsWith("geo")) {
            helpText.append("try one of these instead:\r\n\r\n");
            helpText.append("    geo nmea\r\n");
            helpText.append("    geo fix\r\n\r\n");
            success = false;
        } else {
            helpText.append("try one of these instead:\r\n\r\n");
            helpText.append("     help\r\n");
            helpText.append("     geo\r\n");
            helpText.append("     password\r\n");
            helpText.append("     quit|exit\r\n\r\n");
            success = false;
        }
        ResponseWriter.write(client, helpText.toString() );
        if (success) {
            ResponseWriter.ok(client);
        } else {
            ResponseWriter.writeLine(client, "KO: unknown command");
        }
    }
}
