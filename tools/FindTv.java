import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lists the devices on this computer's network (its /24) that answer on adb's port, 5555: a TV with network debugging
 * on is one of them. Needs only a JDK (no Python, which a fresh Mac lacks):
 *
 *   java tools/FindTv.java
 *
 * Then `adb connect <IP>:5555` and `adb shell getprop ro.product.manufacturer` tell which one is the TV.
 */
public class FindTv {
    public static void main(String[] args) throws Exception {
        InetAddress me;
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("192.0.2.1"), 9);   // sends nothing: only picks the network's own address
            me = s.getLocalAddress();
        }
        byte[] own = me.getAddress();
        if (own.length != 4 || me.isAnyLocalAddress() || me.isLoopbackAddress()) {
            System.err.println("No IPv4 network found: is this computer on the TV's Wi-Fi?");
            System.exit(1);
        }
        System.err.println("Looking on " + (own[0] & 255) + "." + (own[1] & 255) + "." + (own[2] & 255) + ".1-254, port 5555...");
        ExecutorService pool = Executors.newFixedThreadPool(64);
        List<Future<String>> answers = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            byte[] ip = own.clone();
            ip[3] = (byte) i;
            answers.add(pool.submit(() -> {
                InetAddress address = InetAddress.getByAddress(ip);
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, 5555), 800);
                    return address.getHostAddress();
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        int found = 0;
        for (Future<String> answer : answers) {
            String ip = answer.get();
            if (ip == null) continue;
            System.out.println(ip);
            found++;
        }
        pool.shutdown();
        if (found == 0) {
            System.err.println("Nothing answered on port 5555. Is USB debugging on, on the TV? On a Mac, the app this runs in"
                    + " may also need Local Network access (System Settings > Privacy & Security > Local Network).");
        }
    }
}
