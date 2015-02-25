package github.luv.mockgeofix.command;

import java.nio.channels.SocketChannel;

public interface Command {
    public void execute(SocketChannel client, String command);
}
