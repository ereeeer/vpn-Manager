package com.example.vpnlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout rootLayout;
    private TextView titleText; // 新增：用于控制标题颜色
    private ListView listView;
    private Button refreshButton;
    private Button themeButton;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private AppAdapter adapter;
    
    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text); // 绑定标题
        listView = findViewById(R.id.list);
        refreshButton = findViewById(R.id.refresh);
        themeButton = findViewById(R.id.theme_btn);
        searchBox = findViewById(R.id.search_box);
        showAllCheckbox = findViewById(R.id.show_all_checkbox);

        adapter = new AppAdapter();
        listView.setAdapter(adapter);

        themeButton.setOnClickListener(v -> showThemeDialog());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            if (app.launchIntent != null) {
                try {
                    startActivity(app.launchIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法启动：" + app.label, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "该应用没有可启动入口：" + app.label, Toast.LENGTH_SHORT).show();
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            String[] options = {"复制包名", "应用信息", "卸载应用"};
            
            new AlertDialog.Builder(this)
                    .setTitle(app.label)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Package Name", app.packageName);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(this, "包名已复制: " + app.packageName, Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            jumpToSettings(app.packageName);
                        } else if (which == 2) {
                            try {
                                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                                uninstallIntent.setData(Uri.parse("package:" + app.packageName));
                                startActivity(uninstallIntent);
                            } catch (Exception e) {
                                jumpToSettings(app.packageName);
                            }
                        }
                    })
                    .show();
            return true;
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        refreshButton.setOnClickListener(v -> {
            if (searchBox.getText().length() > 0) {
                searchBox.setText(""); 
            } else {
                scanApps();
            }
        });

        showAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> scanApps());

        scanApps();
    }

    // 【重要：换色同时智能改变字体颜色】
    private void showThemeDialog() {
        String[] themes = {"护眼灰蓝 (推荐)", "豆沙绿 (护眼)", "燕麦米黄 (温馨)", "夜间深灰 (不刺眼)"};
        new AlertDialog.Builder(this)
                .setTitle("选择一个舒适的背景色")
                .setItems(themes, (dialog, which) -> {
                    String colorHex;
                    boolean isDark = false; // 判断是不是深色主题

                    switch (which) {
                        case 0: colorHex = "#E6EDF2"; break; // 淡淡的灰蓝
                        case 1: colorHex = "#E3ECE3"; break; // 淡淡的绿
                        case 2: colorHex = "#F5EFE6"; break; // 淡淡的米黄
                        case 3: colorHex = "#303030"; isDark = true; break; // 深色
                        default: colorHex = "#E6EDF2";
                    }
                    
                    rootLayout.setBackgroundColor(Color.parseColor(colorHex));
                    
                    // 如果是深色背景，标题和复选框文字变成白色；浅色背景就变回黑色。
                    if (isDark) {
                        titleText.setTextColor(Color.WHITE);
                        showAllCheckbox.setTextColor(Color.WHITE);
                    } else {
                        titleText.setTextColor(Color.parseColor("#333333"));
                        showAllCheckbox.setTextColor(Color.parseColor("#555555"));
                    }
                })
                .show();
    }

    private void jumpToSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置页", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanApps() {
        refreshButton.setEnabled(false);
        refreshButton.setText("扫描中...");

        int firstVisiblePosition = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        int top = (v == null) ? 0 : v.getTop();

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> found = new ArrayList<>();

            String[] keywords = {"vpn", "代理", "proxy", "加速", "梯子", "翻墙", "clash", "v2ray", "ssr", "shadowsocks", "surfboard", "trojan", "roxi"};
            boolean isShowAll = showAllCheckbox.isChecked();

            for (ApplicationInfo ai : installed) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                if (launch == null) continue;

                CharSequence labelCs = pm.getApplicationLabel(ai);
                String label = labelCs == null ? ai.packageName : labelCs.toString();
                String lowerLabel = label.toLowerCase(Locale.ROOT);

                boolean isMatch = false;
                for (String kw : keywords) {
                    if (lowerLabel.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }

                boolean hasVpnPermission = pm.checkPermission(android.Manifest.permission.BIND_VPN_SERVICE, ai.packageName) == PackageManager.PERMISSION_GRANTED;
                boolean isDetected = isMatch || hasVpnPermission;

                if (!isShowAll && !isDetected) {
                    continue;
                }

                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(ai.packageName);
                } catch (Exception e) {
                    icon = pm.getDefaultActivityIcon();
                }

                found.add(new AppInfo(label, ai.packageName, icon, launch, isDetected));
            }

            Collections.sort(found, (a, b) -> {
                if (a.isDetected != b.isDetected) {
                    return a.isDetected ? -1 : 1;
                }
                return Collator.getInstance().compare(a.label, b.label);
            });

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(found);
                filterApps(searchBox.getText().toString());
                listView.setSelectionFromTop(firstVisiblePosition, top);

                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
                Toast.makeText(MainActivity.this, "刷新成功，共 " + allApps.size() + " 个应用", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void filterApps(String query) {
        apps.clear();
        if (query.isEmpty()) {
            apps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            for (AppInfo app : allApps) {
                if (app.label.toLowerCase(Locale.ROOT).contains(lowerQuery) || 
                    app.packageName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    apps.add(app);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static class AppInfo {
        final String label;
        final String packageName;
        final Drawable icon;
        final Intent launchIntent;
        final boolean isDetected;

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent, boolean isDetected) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isDetected = isDetected;
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return apps.size(); }
        @Override
        public Object getItem(int position) { return apps.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, parent, false);
            }
            AppInfo app = apps.get(position);
            
            ImageView iconView = row.findViewById(R.id.app_icon);
            TextView nameView = row.findViewById(R.id.app_name);
            TextView packageView = row.findViewById(R.id.app_package);
            TextView checkMarkView = row.findViewById(R.id.check_mark);

            iconView.setImageDrawable(app.icon);
            nameView.setText(app.label);
            packageView.setText(app.packageName);

            if (app.isDetected) {
                checkMarkView.setVisibility(View.VISIBLE);
            } else {
                checkMarkView.setVisibility(View.GONE);
            }

            return row;
        }
    }
}
