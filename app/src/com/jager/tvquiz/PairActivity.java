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

/**
 * v1.3.1：电视上直接显示局域网管理地址与二维码，家长用手机扫码即可进入。
 *
 * <p>本页只能从已通过家长密码门禁的设置页进入，因此进页就自动开启监听并渲染
 * 二维码，不再要求家长再点一次「开启」；页面保留「关闭手机管理」随时可关。
 * 布局按 540dp 高度的电视屏收紧，并显式串好两颗按钮的遥控器上下焦点。
 */
public class PairActivity extends Activity {

    private ImageView qr;
    private TextView info, hint;
    private Button toggle, back;
    private SettingsStore store;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SettingsStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12));

        root.addView(Ui.tv(this, "手机管理 · 扫码连接", 24, 0xFFFFFFFF, Gravity.CENTER),
                Ui.fixed(this, 38));

        info = Ui.tv(this, "", 19, Ui.AMBER, Gravity.CENTER);
        root.addView(info, Ui.fixed(this, 34));

        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(qr, new LinearLayout.LayoutParams(
                Ui.dp(this, 236), Ui.dp(this, 236)));

        hint = Ui.tv(this, "", 13, Ui.MUTED, Gravity.CENTER);
        hint.setLineSpacing(0, 1.1f);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        toggle = Ui.button(this, "", 0xFF185FA5, 0xFFFFFFFF, 16);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = store.lanAdminEnabled() && LanServer.isRunning();
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
        root.addView(toggle, buttonLp());

        back = Ui.button(this, "返回", 0xFF243044, 0xFFFFFFFF, 16);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back, buttonLp());
        wireFocus();
        setContentView(root);

        autoEnable();
        refresh();
        back.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), Ui.dp(this, 44));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 6);
        return lp;
    }

    /** TV 遥控器：两个按钮上下互达，边缘不绕回，避免焦点落到屏幕外。 */
    private void wireFocus() {
        toggle.setId(View.generateViewId());
        back.setId(View.generateViewId());
        toggle.setNextFocusDownId(back.getId());
        back.setNextFocusUpId(toggle.getId());
    }

    /** 已通过密码门禁才可能进本页；进页即开启，保证一屏就有可扫的二维码。 */
    private void autoEnable() {
        if (!GuardPolicy.canStartGuard(store.hasPin())) {
            Toast.makeText(this, "请先在家长设置里设置家长密码", Toast.LENGTH_LONG).show();
            return;
        }
        if (LanServer.isRunning()) {
            store.lanAdminEnabled(true);
            return;
        }
        if (LanServer.start(this)) store.lanAdminEnabled(true);
        else Toast.makeText(this, "无法开启，请检查 WiFi 连接", Toast.LENGTH_LONG).show();
    }

    private void refresh() {
        boolean on = store.lanAdminEnabled() && LanServer.isRunning();
        toggle.setText(on ? "关闭手机管理" : "开启手机管理");
        String url = LanServer.url();
        info.setText(on ? url : "未开启");
        if (!on) {
            qr.setImageBitmap(null);
            hint.setText("当前未监听；点「开启手机管理」后即显示二维码。\n"
                    + "原来的电视端家长设置完整保留，可继续作为备用。");
            return;
        }
        qr.setImageBitmap(render(url));
        if (url.contains("127.0.0.1")) {
            hint.setText("未检测到局域网 IP：请确认电视已连 WiFi 后重进本页。\n"
                    + "当前地址只能本机访问，手机扫这个码打不开后台。");
        } else {
            hint.setText("手机与电视连同一 WiFi，扫码后在页面输入家长密码。\n"
                    + "端口 " + LanServer.port() + "（被占用自动顺延）；数据只在局域网内流转，不访问互联网。");
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
}
