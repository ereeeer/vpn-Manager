package com.example.vpnlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private LinearLayout rootLayout;
    private TextView titleText;
    private ListView listView;
    private Button refreshButton;
    private Button themeButton;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private AppAdapter adapter;
    
    private boolean isDarkMode = false;
    private boolean isShowAll = false; // 记录当前是不是“显示全部”模式

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    private SharedPreferences prefs;
    private Set<String> favoritePackages;
    private Set<String> customVpnPackages; // 新增：手动标记为 VPN 的包名

    private BroadcastReceiver uninstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                boolean isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (!isReplacing) {
                    runOnUiThread(() -> scanApps());
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VpnManagerPrefs", MODE_PRIVATE);
        favoritePackages = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        customVpnPackages = new HashSet<>(prefs.getStringSet("custom_vpns", new HashSet<>())); // 初始化手动标记集合

        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text); 
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
            
            String favStr = app.isFavorite ? "取消收藏" : "⭐ 收藏置顶";
            String[] options = {favStr, "复制包名", "应用信息", "卸载应用"};
            
            new AlertDialog.Builder(this)
                    .setTitle(app.label)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            toggleFavorite(app.packageName);
                        } else if (which == 1) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Package Name", app.packageName);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(this, "包名已复制: " + app.packageName, Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 2) {
                            jumpToSettings(app.packageName);
                        } else if (which == 3) {
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

        showAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isShowAll = isChecked; // 记录状态
            scanApps();
        });

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(uninstallReceiver, filter);

        scanApps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(uninstallReceiver);
        } catch (Exception ignored) {}
    }

    // 点击 [+] 手动标记为 VPN
    private void markAsVpn(String packageName) {
        customVpnPackages.add(packageName);
        prefs.edit().putStringSet("custom_vpns", customVpnPackages).apply();
        Toast.makeText(this, "已成功收编为 VPN！", Toast.LENGTH_SHORT).show();
        scanApps(); // 重新扫描，它就会带上绿√了
    }

    private void toggleFavorite(String packageName) {
        if (favoritePackages.contains(packageName)) {
            favoritePackages.remove(packageName);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoritePackages.add(packageName);
            Toast.makeText(this, "已加入收藏置顶", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet("favorites", favoritePackages).apply();
        
        for (AppInfo app : allApps) {
            app.isFavorite = favoritePackages.contains(app.packageName);
        }

        int firstVisiblePosition = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        int top = (v == null) ? 0 : v.getTop();

        sortAndAssignIndex();
        filterApps(searchBox.getText().toString());
        listView.setSelectionFromTop(firstVisiblePosition, top);
    }

    private void showThemeDialog() {
        String[] themes = {"明显灰蓝", "明显护眼绿", "明显米黄", "极致纯黑 (夜间)"};
        new AlertDialog.Builder(this)
                .setTitle("选择一个对比强烈的背景色")
                .setItems(themes, (dialog, which) -> {
                    String colorHex;
                    switch (which) {
                        case 0: colorHex = "#D6E4F0"; isDarkMode = false; break;
                        case 1: colorHex = "#CCE8CF"; isDarkMode = false; break;
                        case 2: colorHex = "#FCE8C8"; isDarkMode = false; break;
                        case 3: colorHex = "#121212"; isDarkMode = true; break;
                        default: colorHex = "#D6E4F0"; isDarkMode = false;
                    }
                    
                    rootLayout.setBackgroundColor(Color.parseColor(colorHex));
                    
                    if (isDarkMode) {
                        titleText.setTextColor(Color.WHITE);
                        showAllCheckbox.setTextColor(Color.WHITE);
                    } else {
                        titleText.setTextColor(Color.parseColor("#333333"));
                        showAllCheckbox.setTextColor(Color.parseColor("#555555"));
                    }
                    
                    adapter.notifyDataSetChanged();
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
                
                // 核心：只要满足 智能识别 或 被手动标记过，就是 Detected
                boolean isDetected = isMatch || hasVpnPermission || customVpnPackages.contains(ai.packageName);
                
                boolean isFav = favoritePackages.contains(ai.packageName);

                if (!isShowAll && !isDetected && !isFav) {
                    continue;
                }

                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(ai.packageName);
                } catch (Exception e) {
                    icon = pm.getDefaultActivityIcon();
                }

                long sizeInBytes = 0;
                try {
                    File apkFile = new File(ai.publicSourceDir);
                    sizeInBytes = apkFile.length();
                } catch (Exception ignored) {}
                String sizeStr = String.format(Locale.getDefault(), "%.1f MB", sizeInBytes / (1024.0 * 1024.0));

                found.add(new AppInfo(label, ai.packageName, icon, launch, isDetected, isFav, sizeStr));
            }

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(found);
                sortAndAssignIndex();
                filterApps(searchBox.getText().toString());
                
                listView.setSelectionFromTop(firstVisiblePosition, top);

                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
            });
        }).start();
    }

    private void sortAndAssignIndex() {
        Collections.sort(allApps, (a, b) -> {
            if (a.isFavorite != b.isFavorite) {
                return a.isFavorite ? -1 : 1;
            }
            if (a.isDetected != b.isDetected) {
                return a.isDetected ? -1 : 1;
            }
            return Collator.getInstance().compare(a.label, b.label);
        });

        for (int i = 0; i < allApps.size(); i++) {
            allApps.get(i).absoluteIndex = i + 1;
        }
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
        boolean isDetected; // 改成非 final，因为可能被手动标记
        boolean isFavorite; 
        final String sizeStr;
        int absoluteIndex; 

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent, boolean isDetected, boolean isFavorite, String sizeStr) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isDetected = isDetected;
            this.isFavorite = isFavorite;
            this.sizeStr = sizeStr;
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
            
            TextView indexView = row.findViewById(R.id.tv_index);
            ImageView iconView = row.findViewById(R.id.app_icon);
            TextView nameView = row.findViewById(R.id.app_name);
            TextView packageView = row.findViewById(R.id.app_package);
            TextView checkMarkView = row.findViewById(R.id.check_mark);
            TextView favoriteView = row.findViewById(R.id.tv_favorite);
            TextView sizeView = row.findViewById(R.id.tv_size);
            TextView markVpnView = row.findViewById(R.id.tv_mark_vpn); // 新增的手动标记按钮

            indexView.setText(String.valueOf(app.absoluteIndex));
            
            iconView.setImageDrawable(app.icon);
            nameView.setText(app.label);
            packageView.setText(app.packageName);
            sizeView.setText(app.sizeStr);

            if (app.isFavorite) {
                favoriteView.setVisibility(View.VISIBLE);
            } else {
                favoriteView.setVisibility(View.GONE);
            }

            if (isDarkMode) {
                nameView.setTextColor(Color.WHITE);
                packageView.setTextColor(Color.parseColor("#BBBBBB"));
                indexView.setTextColor(Color.parseColor("#BBBBBB"));
                sizeView.setTextColor(Color.parseColor("#BBBBBB"));
                markVpnView.setTextColor(Color.parseColor("#BBBBBB"));
            } else {
                nameView.setTextColor(Color.parseColor("#212121"));
                packageView.setTextColor(Color.parseColor("#757575"));
                indexView.setTextColor(Color.parseColor("#757575"));
                sizeView.setTextColor(Color.parseColor("#757575"));
                markVpnView.setTextColor(Color.parseColor("#888888"));
            }

            // 核心逻辑：如果被识别或已手动标记，显示绿√，隐藏 [+] 框
            if (app.isDetected) {
                checkMarkView.setVisibility(View.VISIBLE);
                markVpnView.setVisibility(View.GONE);
            } else {
                checkMarkView.setVisibility(View.GONE);
                // 如果当前开启了“显示全部”，且它没被识别，才显示 [+] 供用户点击
                if (isShowAll) {
                    markVpnView.setVisibility(View.VISIBLE);
                } else {
                    markVpnView.setVisibility(View.GONE);
                }
            }

            // 点击 [+] 标记按钮
            markVpnView.setOnClickListener(v -> markAsVpn(app.packageName));

            return row;
        }
    }
}
