package node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import static node.NodeHandler.synchronizeWithPeers;

public class NodeServer {
    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            final int port = 5000 + i;
            final String storageRoot = "node" + (i + 1) + "/storage";

            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    System.out.println("Node running on port " + port);
                    while (true) {
                        Socket socket = serverSocket.accept();
                        new Thread(new NodeHandler(socket, storageRoot)).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronizeWithPeers(storageRoot, port, Arrays.asList("127.0.0.1:5000", "127.0.0.1:5001", "127.0.0.1:5002"));
                }
            },
            // 5000, 24 * 60 * 60 * 1000); // لأول مرة بعد 5 ثوان، ثم كل 24 ساعة

        getDelayUntilEndOfDay(), 24 * 60 * 60 * 1000); // ثم كل 24 ساعة

        }
    }
    private static long getDelayUntilEndOfDay() {
        Calendar now = Calendar.getInstance();
        Calendar endOfDay = Calendar.getInstance();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 0);
        endOfDay.set(Calendar.MILLISECOND, 0);

        long delay = endOfDay.getTimeInMillis() - now.getTimeInMillis();
        return Math.max(delay, 0); // احتياطاً لو كان الوقت متأخر جداً
    }

}
