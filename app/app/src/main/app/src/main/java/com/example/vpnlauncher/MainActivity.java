package com.example.vpnlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
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
    private AppAdapter adapter;
    private final List<AppInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        refreshButton = findViewById(R.id.refresh);

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

        refreshButton.setOnClickListener(v -> scanApps());

        scanApps();
    }

    private void scanApps() {
        refreshButton.setEnabled(false);
        refreshButton.setText("扫描中...");

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppInfo> found = new ArrayList<>();

            for (ApplicationInfo ai : installed) {
                CharSequence labelCs = pm.getApplicationLabel(ai);
                String label = labelCs == null ? ai.packageName : labelCs.toString();

                if (!label.toLowerCase(Locale.ROOT).contains("vpn")) {
                    continue;
                }

                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);

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
                apps.clear();
                apps.addAll(found);
                adapter.notifyDataSetChanged();

                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");

                if (apps.isEmpty()) {
                    Toast.makeText(this, "没有找到名称包含 VPN 的应用", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
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
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.row_app, parent, false);
            }

            AppInfo app = apps.get(position);

            ImageView iconView = row.findViewById(R.id.app_icon);
            TextView nameView = row.findViewById(R.id.app_name);

            iconView.setImageDrawable(app.icon);
            nameView.setText(app.label);

            return row;
        }
    }
}
