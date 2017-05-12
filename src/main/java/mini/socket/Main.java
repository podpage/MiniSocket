package mini.socket;

/**
 * Created by podpage on 11.05.2017.
 */
public class Main {

    public static void main(String... args) {
        try {
            MiniSocket miniSocket = new MiniSocket();
            miniSocket.connect("echo.websocket.org", "/?encoding=text", 80, false);
            miniSocket.send("Hello World");
            System.out.println(miniSocket.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
