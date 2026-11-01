package com.example.vpnlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private ListView listView;
    private Button refreshButton;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private AppAdapter adapter;
    
    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        refreshButton = findViewById(R.id.refresh);
        searchBox = findViewById(R.id.search_box);
        showAllCheckbox = findViewById(R.id.show_all_checkbox);

        adapter = new AppAdapter();
        listView.setAdapter(adapter);

        // 点击打开
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

        // 长按卸载
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("卸载应用")
                    .setMessage("确定要卸载 " + app.label + " 吗？\n包名：" + app.packageName)
                    .setPositiveButton("卸载", (dialog, which) -> {
                        try {
                            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                            uninstallIntent.setData(Uri.parse("package:" + app.packageName));
                            startActivity(uninstallIntent);
                            Toast.makeText(MainActivity.this, "卸载完成后，请点击右上角刷新", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "系统限制直接卸载，已为您跳转设置页", Toast.LENGTH_SHORT).show();
                            Intent detailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            detailsIntent.setData(Uri.parse("package:" + app.packageName));
                            startActivity(detailsIntent);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        // 搜索框
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

        // 刷新按钮
        refreshButton.setOnClickListener(v -> {
            searchBox.setText(""); 
            scanApps();
        });

        // 勾选框切换事件：一旦改变状态，立刻重新扫描
        showAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> scanApps());

        scanApps();
    }

    private void scanApps() {
        refreshButton.setEnabled(false);
        refreshButton.setText("扫描中...");

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> found = new ArrayList<>();

            String[] keywords = {"vpn", "代理", "proxy", "加速", "梯子", "翻墙", "clash", "v2ray", "ssr", "shadowsocks", "surfboard", "trojan", "roxi"};
            boolean isShowAll = showAllCheckbox.isChecked();

            for (ApplicationInfo ai : installed) {
                // 过滤掉系统自带应用
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                // 必须有启动入口（排除后台服务）
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

                // 核心逻辑：如果勾选了"显示全部"，或者 名字命中 或者 有VPN权限，则加入列表
                if (!isShowAll && !isMatch && !hasVpnPermission) {
                    continue;
                }

                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(ai.packageName);
                } catch (Exception e) {
                    icon = pm.getDefaultActivityIcon();
                }

                found.add(new AppInfo(label, ai.packageName, icon, launch));
            }

            Collections.sort(found, (a, b) -> Collator.getInstance().compare(a.label, b.label));

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(found);
                
                filterApps(searchBox.getText().toString());

                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
                Toast.makeText(MainActivity.this, isShowAll ? "已显示全部应用" : "已智能过滤", Toast.LENGTH_SHORT).show();
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

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
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

            iconView.setImageDrawable(app.icon);
            nameView.setText(app.label);
            packageView.setText(app.packageName);
            return row;
        }
    }
}
