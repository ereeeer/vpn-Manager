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
            new AlertDialog.Builder(this)
                    .setTitle("卸载应用")
                    .setMessage("确定要卸载 " + app.label + " 吗？\n\n如果点击无反应，请选择跳转到系统设置手动卸载。")
                    .setPositiveButton("直接卸载", (dialog, which) -> {
                        try {
                            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                            uninstallIntent.setData(Uri.parse("package:" + app.packageName));
                            startActivity(uninstallIntent);
                        } catch (Exception e) {
                            jumpToSettings(app.packageName);
                        }
                    })
                    .setNeutralButton("去设置页", (dialog, which) -> jumpToSettings(app.packageName))
                    .setNegativeButton("取消", null)
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

        // 刷新按钮
        refreshButton.setOnClickListener(v -> {
            // 如果搜索框有字，刷新时清空搜索框；如果没字，保持不动
            if (searchBox.getText().length() > 0) {
                searchBox.setText(""); 
            } else {
                scanApps();
            }
        });

        showAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> scanApps());

        scanApps();
    }

    private void jumpToSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
            Toast.makeText(this, "请在系统界面点击【卸载】", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置页", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanApps() {
        refreshButton.setEnabled(false);
        refreshButton.setText("扫描中...");

        // 核心魔法 1：在刷新前，记住当前列表滑到了第几个，以及偏移量
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

            Collections.sort(found, (a, b) -> Collator.getInstance().compare(a.label, b.label));

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(found);
                
                filterApps(searchBox.getText().toString());

                // 核心魔法 2：数据更新完后，瞬间滑动回刚才记住的位置
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
