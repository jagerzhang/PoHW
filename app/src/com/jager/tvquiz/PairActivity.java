package com.jager.tvquiz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** v1.3.0 未发布：电视上显示局域网管理地址与二维码，家长用手机扫码进入。 */
public class PairActivity extends Activity {

    private ImageView qr;
    private TextView info, hint;
    private Button toggle;
    private final SettingsStore st = null;
    private SettingsStore store;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SettingsStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 32), Ui.dp(this, 20), Ui.dp(this, 32), Ui.dp(this, 20));

        TextView title = Ui.tv(this, "手机管理 · 扫码连接", 26, 0xFFFFFFFF);
        root.addView(title, Ui.fixed(this, 46));

        info = Ui.tv(this, "", 20, 0xFFC7D2E2, Gravity.CENTER);
        root.addView(info, Ui.fixed(this, 40));

        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        root.addView(qr, new LinearLayout.LayoutParams(
                Ui.dp(this, 300), Ui.dp(this, 300)));

        hint = Ui.tv(this, "", 14, Ui.MUTED, Gravity.CENTER);
        hint.setLineSpacing(0, 1.15f);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        toggle = Ui.button(this, "", 0xFF185FA5, 0xFFFFFFFF, 18);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = store.lanAdminEnabled();
                if (on) {
                    LanServer.stop();
                    store.lanAdminEnabled(false);
                } else {
                    if (!LanServer.start(PairActivity.this)) {
                        Toast.makeText(PairActivity.this, "无法开启，请检查 WiFi 连接",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.lanAdminEnabled(true);
                }
                refresh();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), Ui.dp(this, 50));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(toggle, lp);

        Button back = Ui.button(this, "返回", 0xFF243044, 0xFFFFFFFF, 16);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back, lp);

        setContentView(root);
        // 覆盖安装后服务进程可能已重启，按持久化开关恢复监听
        if (store.lanAdminEnabled() && !LanServer.isRunning()) LanServer.start(this);
        refresh();
    }

    private void refresh() {
        boolean on = store.lanAdminEnabled() && LanServer.isRunning();
        toggle.setText(on ? "关闭手机管理" : "开启手机管理");
        String url = LanServer.url();
        info.setText(on ? url : "未开启");
        if (on) {
            qr.setImageBitmap(render(url));
            hint.setText("手机与电视连同一 WiFi，扫码后在页面输入家长密码。\n"
                    + "数据只在本机与局域网内流转，不访问互联网、不上传任何数据。");
        } else {
            qr.setImageBitmap(null);
            hint.setText("默认关闭；开启后仅监听局域网 8099 端口，随时可关闭。\n"
                    + "原来的电视端家长设置完整保留，可继续作为备用。");
        }
    }

    /** 每模块 8px、4 模块静区；QrEncoder 输出与标准库矩阵逐位一致。 */
    private Bitmap render(String url) {
        int[][] m = QrEncoder.encode(url);
        if (m == null) return null;
        int scale = 8, quiet = 4, side = (m.length + quiet * 2) * scale;
        Bitmap bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m[y].length; x++) {
                if (m[y][x] != 1) continue;
                int px = (x + quiet) * scale, py = (y + quiet) * scale;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        bmp.setPixel(px + dx, py + dy, Color.BLACK);
                    }
                }
            }
        }
        return bmp;
    }

    @Override protected void onDestroy() { super.onDestroy(); }
}
