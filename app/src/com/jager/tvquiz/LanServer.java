package com.jager.tvquiz;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;

/**
 * v1.3.0 未发布：设备内置的局域网管理服务，仅在家长手动开启时监听 8099。
 *
 * <p>不访问互联网、不上传任何数据、不依赖任何第三方库；手机与电视在同一
 * WiFi 时用浏览器打开 http://<电视IP>:8099 并输入管理密码即可管理。
 * usesCleartextTraffic=false 只约束本进程出站流量，作为 ServerSocket 服务端不受影响。
 */
public final class LanServer {

    public static final int PORT = 8099;
    private static final String TAG = "PoHW.Lan";
    private static volatile ServerSocket server = null;
    private static volatile Thread acceptThread = null;

    private LanServer() { }

    public static synchronized boolean start(final Context c) {
        if (server != null) return true;
        try {
            server = new ServerSocket(PORT);
            AdminApi.reset();
            acceptThread = new Thread(new Runnable() {
                @Override public void run() {
                    while (server != null && !server.isClosed()) {
                        try {
                            final Socket s = server.accept();
                            new Thread(new Runnable() {
                                @Override public void run() { handle(c, s); }
                            }, "pohw-lan").start();
                        } catch (Exception e) {
                            if (server != null && !server.isClosed()) {
                                Log.w(TAG, "accept failed", e);
                            }
                            return;
                        }
                    }
                }
            }, "pohw-lan-accept");
            acceptThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "cannot bind " + PORT, e);
            server = null;
            return false;
        }
    }

    public static synchronized void stop() {
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        acceptThread = null;
        AdminApi.reset();
    }

    public static boolean isRunning() { return server != null && !server.isClosed(); }

    /** 枚举本机 IPv4，优先私有地址；无需 ACCESS_WIFI_STATE 权限。 */
    public static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.isLoopbackAddress() || !(a instanceof Inet4Address)) continue;
                    String ip = a.getHostAddress();
                    if (ip == null) continue;
                    if (ip.startsWith("192.168.") || ip.startsWith("10.")
                            || ip.startsWith("172.")) return ip;
                }
            }
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }

    public static String url() { return "http://" + localIp() + ":" + PORT + "/"; }

    private static void handle(Context c, Socket s) {
        BufferedReader r = null;
        try {
            s.setSoTimeout(8000);
            r = new BufferedReader(new InputStreamReader(s.getInputStream(), "ISO-8859-1"));
            String req = r.readLine();
            if (req == null) { s.close(); return; }
            String[] parts = req.split(" ");
            if (parts.length < 2) { s.close(); return; }
            String method = parts[0];
            String path = parts[1].split("\\?")[0];
            int len = 0;
            String auth = null;
            String line;
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                String low = line.toLowerCase(java.util.Locale.ROOT);
                if (low.startsWith("content-length:")) {
                    try { len = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) { }
                }
                if (low.startsWith("authorization:")) {
                    auth = line.substring(14).trim();
                }
            }
            StringBuilder body = new StringBuilder();
            if (len > 0 && len < 8192) {
                char[] buf = new char[len];
                int got = 0;
                while (got < len) {
                    int n = r.read(buf, got, len - got);
                    if (n <= 0) break;
                    got += n;
                }
                body.append(buf, 0, got);
            }

            byte[] out;
            String type;
            int code = 200;
            if (path.startsWith("/api/")) {
                AdminApi.Result res = AdminApi.handle(c, method, path, auth, body.toString());
                code = res.code;
                out = res.json.getBytes("UTF-8");
                type = "application/json; charset=utf-8";
            } else {
                String name = "index.html";
                if ("/app.js".equals(path)) name = "app.js";
                else if ("/style.css".equals(path)) name = "style.css";
                out = AdminAssets.read(c, name);
                type = AdminAssets.type(name);
                if (out == null) { code = 404; out = "not found".getBytes("UTF-8"); }
            }
            OutputStream os = s.getOutputStream();
            String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n"
                    + "Content-Type: " + type + "\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(head.getBytes("UTF-8"));
            os.write(out);
            os.flush();
        } catch (Exception e) {
            Log.w(TAG, "request failed: " + e.getClass().getSimpleName());
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
            try { s.close(); } catch (Exception ignored) { }
        }
    }
}
