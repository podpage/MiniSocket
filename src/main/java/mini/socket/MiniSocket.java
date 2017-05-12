package mini.socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * Created by podpage on 11.05.2017.
 * Based on https://github.com/hellerchr/esp8266-websocketclient by Christian Heller
 */
public class MiniSocket {

    private int WS_FIN = 0x80;
    private int WS_OPCODE_TEXT = 0x01;
    private int WS_OPCODE_BINARY = 0x02;

    private int WS_MASK = 0x80;
    private int WS_SIZE16 = 126;

    private InputStream in;
    private OutputStream os;
    private PrintWriter pw;

    private String generateKey() {
        Random random = new Random();
        String key = "";
        for (int i = 0; i < 23; ++i) {
            int r = random.nextInt(3);
            if (r == 0)
                key += (char) (random.nextInt(10) + 48);
            else if (r == 1)
                key += (char) (random.nextInt(26) + 65);
            else if (r == 2)
                key += (char) (random.nextInt(31) + 97);
        }
        return key;
    }

    public boolean connect(String host, String path, int port, boolean secure) throws IOException {
        Socket s;
        if (secure) {
            SocketFactory sf = SSLSocketFactory.getDefault();
            s = sf.createSocket(host, port);
        } else {
            s = new Socket(host, port);
        }

        os = s.getOutputStream();
        pw = new PrintWriter(os);
        pw.println("GET ws" + (secure ? "s" : "") + "://" + host + path + " HTTP/1.1");
        pw.println("Host: " + path + "");
        pw.println("Connection: Upgrade");
        pw.println("Upgrade: websocket");
        pw.println("Sec-WebSocket-Version: 13");
        pw.println("Sec-WebSocket-Key: " + generateKey() + "=");
        pw.println("");
        pw.flush();
        in = s.getInputStream();

        boolean hasCorrectStatus = false, isUpgrade = false, isWebsocket = false, hasAcceptedKey = false;
        String str = "";
        int charcode;
        int pre = 0;
        int lb = 0;

        while ((charcode = in.read()) != -1) {
            str += (char) charcode;
            if (pre == 13 && charcode == 10) {
                lb++;
                if (str.contains("HTTP/")) {
                    String status = str.substring(9, 12);
                    if (status.equals("101")) {
                        hasCorrectStatus = true;
                    } else {
                        System.out.println("Error: " + status);
                        return false;
                    }
                } else if (str.contains(":")) {
                    int col = str.indexOf(":");
                    String key = str.substring(0, col);
                    String value = str.substring(col + 2, str.length());
                    if (key.equals("Connection") && (value.equalsIgnoreCase("upgrade"))) {
                        isUpgrade = true;
                    } else if (key.equals("Sec-WebSocket-Accept")) {
                        hasAcceptedKey = true;
                    } else if (key.equals("Upgrade") && value.equals("websocket")) {
                        isWebsocket = true;
                    }
                }
                str = "";
            }
            if (!(charcode == 10 || charcode == 13)) {
                lb = 0;
            }
            pre = charcode;
            if (lb == 2) {
                break;
            }
        }
        return hasCorrectStatus && isUpgrade && isWebsocket && hasAcceptedKey;
    }

    public void send(String text) throws IOException {
        Random random = new Random();
        int size = text.length();
        os.write(WS_FIN | WS_OPCODE_TEXT);
        os.write(WS_MASK | size);
        if (size > 125) {
            os.write((byte) WS_MASK | WS_SIZE16);
            os.write((byte) (size >> 8));
            os.write((byte) (size & 0xFF));
        } else {
            pw.write(WS_MASK | (byte) text.length());
        }
        byte[] mask = new byte[4];
        for (int i = 0; i < 4; i++) {
            mask[i] = (byte) random.nextInt(256);
            os.write(mask[i]);
        }
        for (int i = 0; i < size; ++i) {
            os.write(text.charAt(i) ^ mask[i % 4]);
        }
        os.flush();
    }

    public String get() throws Exception {
        String message = "";
        int msgtype = in.read();
        int length = in.read();
        boolean hasMask = false;
        if ((length & WS_MASK) == 0) {
            //hasMask = true;
            length = length & ~WS_MASK;
        }
        if (length == WS_SIZE16) {
            int i1 = in.read();
            int i2 = in.read();
            length = i1 << 8;
            length |= i2;
        }
        if (hasMask) {
            byte[] mask = new byte[4];
            mask[0] = (byte) in.read();
            mask[1] = (byte) in.read();
            mask[2] = (byte) in.read();
            mask[3] = (byte) in.read();
            for (int i = 0; i < length; ++i) {
                message += (char) (in.read() ^ mask[i % 4]);
            }
        } else {
            message = "";
            for (int i = 0; i < length; ++i) {
                message += (char) in.read();
            }
        }
        return message;
    }
}