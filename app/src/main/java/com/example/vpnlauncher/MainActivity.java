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
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
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

    private static final String PREFS = "VpnManagerPrefs";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_CUSTOM = "custom_vpns";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_SHOW_ALL = "show_all";
    private static final String VPN_SERVICE_ACTION = "android.net.VpnService";

    private static final String[] KEYWORDS = {
            "vpn", "代理", "proxy", "加速", "梯子", "翻墙",
            "clash", "v2ray", "xray", "ssr", "shadowsocks",
            "surfboard", "trojan", "wireguard", "openvpn",
            "hysteria", "sing-box", "singbox", "nekobox",
            "sagernet", "outline", "hiddify", "roxi", "tuic"
    };

    private LinearLayout rootLayout;
    private TextView titleText;
    private TextView emptyView;
    private ListView listView;
    private Button refreshButton;
    private Button themeButton;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private TextView tvHelp;
    private AppAdapter adapter;

    // 图标缓存
    private final LruCache<String, Drawable> iconCache = new LruCache<>(150);

    private boolean isDarkMode = false;
    private boolean isShowAll = false;
    private int scanGeneration = 0;
    private boolean receiverRegistered = false;

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    private SharedPreferences prefs;
    private Set<String> favoritePackages = new HashSet<>();
    private Set<String> customVpnPackages = new HashSet<>();

    private final BroadcastReceiver uninstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                String pkg = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
                if (pkg != null) {
                    favoritePackages.remove(pkg);
                    customVpnPackages.remove(pkg);
                    saveSets();
                }
                scanApps(); // 卸载后刷新
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                scanApps(); // 安装后自动刷新
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        favoritePackages = new HashSet<>(prefs.getStringSet(KEY_FAV, Collections.emptySet()));
        customVpnPackages = new HashSet<>(prefs.getStringSet(KEY_CUSTOM, Collections.emptySet()));
        isShowAll = prefs.getBoolean(KEY_SHOW_ALL, false);

        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text);
        emptyView = findViewById(R.id.empty_view);
        listView = findViewById(R.id.list);
        refreshButton = findViewById(R.id.refresh);
        themeButton = findViewById(R.id.theme_btn);
        searchBox = findViewById(R.id.search_box);
        showAllCheckbox = findViewById(R.id.show_all_checkbox);
        tvHelp = findViewById(R.id.tv_help);

        adapter = new AppAdapter();
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter);

        applyTheme(prefs.getInt(KEY_THEME, 0), false);

        showAllCheckbox.setOnCheckedChangeListener(null);
        showAllCheckbox.setChecked(isShowAll);
        showAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isShowAll = isChecked;
            prefs.edit().putBoolean(KEY_SHOW_ALL, isShowAll).apply();
            scanApps();
        });

        tvHelp.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("使用说明")
                .setMessage("【智能识别】\n列出声明了系统 VPN 服务的应用，以及名称或包名包含 VPN、Clash、v2ray、WireGuard 等关键词的应用。\n\n"
                        + "【手动收编】\n勾选「显示全部应用」后，未识别的软件右侧有 [+]。点一下永久收编。长按已收编项可选「取消收编」。\n\n"
                        + "【收藏与排序】\n长按可收藏置顶，排序：收藏 > 已识别 > 名称。\n\n"
                        + "【长按菜单】\n收藏、复制包名、应用信息、卸载。\n\n"
                        + "【主题】\n点 🎨 切换背景，选择会记住。")
                .setPositiveButton("我知道了", null)
                .show());

        themeButton.setOnClickListener(v -> showThemeDialog());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return;
            AppInfo app = apps.get(position);
            if (app.launchIntent == null) {
                Toast.makeText(this, "该应用没有可启动入口：" + app.label, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                startActivity(app.launchIntent);
            } catch (Exception e) {
                Toast.makeText(this, "无法启动：" + app.label, Toast.LENGTH_SHORT).show();
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return true;
            AppInfo app = apps.get(position);
            List<String> options = new ArrayList<>();
            String optFav = app.isFavorite ? "取消收藏" : "⭐ 收藏置顶";
            String optCopy = "复制包名";
            String optInfo = "应用信息";
            String optUnmark = "取消收编";
            String optUninstall = "卸载应用";
            options.add(optFav);
            options.add(optCopy);
            options.add(optInfo);
            if (app.isCustom) options.add(optUnmark);
            options.add(optUninstall);
            String[] items = options.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle(app.label)
                    .setItems(items, (dialog, which) -> {
                        String pick = items[which];
                        if (optFav.equals(pick)) toggleFavorite(app.packageName);
                        else if (optCopy.equals(pick)) copyPackage(app.packageName);
                        else if (optInfo.equals(pick)) jumpToSettings(app.packageName);
                        else if (optUnmark.equals(pick)) unmarkVpn(app.packageName);
                        else if (optUninstall.equals(pick)) confirmUninstall(app);
                    })
                    .show();
            return true;
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        refreshButton.setOnClickListener(v -> scanApps());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uninstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(uninstallReceiver, filter);
        }
        receiverRegistered = true;

        scanApps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!receiverRegistered) return;
        try {
            unregisterReceiver(uninstallReceiver);
        } catch (Exception ignored) {}
        receiverRegistered = false;
    }

    private void saveSets() {
        prefs.edit()
                .putStringSet(KEY_FAV, new HashSet<>(favoritePackages))
                .putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages))
                .apply();
    }

    private void markAsVpn(String packageName) {
        customVpnPackages.add(packageName);
        saveSets();
        Toast.makeText(this, "已收编为 VPN", Toast.LENGTH_SHORT).show();
        scanApps();
    }

    private void unmarkVpn(String packageName) {
        customVpnPackages.remove(packageName);
        saveSets();
        Toast.makeText(this, "已取消收编", Toast.LENGTH_SHORT).show();
        scanApps();
    }

    private void copyPackage(String packageName) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Package Name", packageName));
        Toast.makeText(this, "包名已复制: " + packageName, Toast.LENGTH_SHORT).show();
    }

    private void confirmUninstall(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("卸载应用")
                .setMessage("确定卸载「" + app.label + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (d, w) -> {
                    try {
                        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                        uninstallIntent.setData(Uri.parse("package:" + app.packageName));
                        startActivity(uninstallIntent);
                    } catch (Exception e) {
                        jumpToSettings(app.packageName);
                    }
                })
                .show();
    }

    private void toggleFavorite(String packageName) {
        if (favoritePackages.contains(packageName)) {
            favoritePackages.remove(packageName);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoritePackages.add(packageName);
            Toast.makeText(this, "已加入收藏置顶", Toast.LENGTH_SHORT).show();
        }
        saveSets();
        for (AppInfo app : allApps) {
            app.isFavorite = favoritePackages.contains(app.packageName);
        }
        int first = listView.getFirstVisiblePosition();
        View topView = listView.getChildAt(0);
        int top = topView == null ? 0 : topView.getTop();
        sortAndAssignIndex();
        filterApps(searchBox.getText().toString());
        listView.setSelectionFromTop(first, top);
    }

    private void showThemeDialog() {
        String[] themes = {"明显灰蓝", "明显护眼绿", "明显米黄", "极致纯黑 (夜间)"};
        new AlertDialog.Builder(this)
                .setTitle("选择一个对比强烈的背景色")
                .setItems(themes, (dialog, which) -> applyTheme(which, true))
                .show();
    }

    private void applyTheme(int which, boolean persist) {
        String colorHex;
        switch (which) {
            case 1: colorHex = "#CCE8CF"; isDarkMode = false; break;
            case 2: colorHex = "#FCE8C8"; isDarkMode = false; break;
            case 3: colorHex = "#121212"; isDarkMode = true; break;
            default: colorHex = "#D6E4F0"; isDarkMode = false; which = 0;
        }
        rootLayout.setBackgroundColor(Color.parseColor(colorHex));
        int title = isDarkMode ? Color.WHITE : Color.parseColor("#333333");
        int secondary = isDarkMode ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555");
        titleText.setTextColor(title);
        showAllCheckbox.setTextColor(secondary);
        tvHelp.setTextColor(secondary);
        emptyView.setTextColor(secondary);
        if (persist) prefs.edit().putInt(KEY_THEME, which).apply();
        if (adapter != null) adapter.notifyDataSetChanged();
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
        final int generation = ++scanGeneration;
        final Set<String> favSnap = new HashSet<>(favoritePackages);
        final Set<String> customSnap = new HashSet<>(customVpnPackages);
        final boolean showAll = isShowAll;
        final String self = getPackageName();

        refreshButton.setEnabled(false);
        refreshButton.setText("扫描中...");
        emptyView.setText("正在扫描…");

        int first = listView.getFirstVisiblePosition();
        View topView = listView.getChildAt(0);
        int top = topView == null ? 0 : topView.getTop();

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> vpnPackages = new HashSet<>();
            List<ResolveInfo> vpnServices = pm.queryIntentServices(new Intent(VPN_SERVICE_ACTION), 0);
            if (vpnServices != null) {
                for (ResolveInfo ri : vpnServices) {
                    if (ri.serviceInfo != null) vpnPackages.add(ri.serviceInfo.packageName);
                }
            }

            Intent launcher = new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<AppInfo> found = new ArrayList<>();

            if (activities != null) {
                for (ResolveInfo ri : activities) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (!seen.add(pkg) || self.equals(pkg)) continue;

                    ApplicationInfo ai;
                    try {
                        ai = pm.getApplicationInfo(pkg, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        continue;
                    }
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch == null) continue;

                    CharSequence labelCs = pm.getApplicationLabel(ai);
                    String label = labelCs == null ? pkg : labelCs.toString();
                    boolean isAuto = vpnPackages.contains(pkg) || matchesKeyword(label, pkg);
                    boolean isCustom = customSnap.contains(pkg);
                    boolean isFav = favSnap.contains(pkg);
                    if (!showAll && !isAuto && !isCustom && !isFav) continue;

                    Drawable icon = iconCache.get(pkg);
                    if (icon == null) {
                        try {
                            icon = pm.getApplicationIcon(ai);
                        } catch (Exception e) {
                            icon = pm.getDefaultActivityIcon();
                        }
                        iconCache.put(pkg, icon);
                    }
                    found.add(new AppInfo(label, pkg, icon, launch, isAuto, isCustom, isFav, formatSize(ai)));
                }
            }

            runOnUiThread(() -> {
                if (generation != scanGeneration || isFinishing() || isDestroyed()) return;
                if (listView.getAdapter() == null) return; 
                
                allApps.clear();
                allApps.addAll(found);
                sortAndAssignIndex();
                filterApps(searchBox.getText().toString());
                listView.post(() -> listView.setSelectionFromTop(first, top));
                refreshButton.setEnabled(true);
                refreshButton.setText("刷新");
                if (found.isEmpty()) {
                    emptyView.setText(showAll
                            ? "没有可启动的第三方应用"
                            : "没有识别到 VPN 应用\n勾选「显示全部应用」后，点 [+] 手动收编");
                }
            });
        }).start();
    }

    private static boolean matchesKeyword(String label, String packageName) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        String lowerPkg = packageName.toLowerCase(Locale.ROOT);
        for (String kw : KEYWORDS) {
            if (lowerLabel.contains(kw) || lowerPkg.contains(kw)) return true;
        }
        return false;
    }

    private static String formatSize(ApplicationInfo ai) {
        long size = 0;
        try {
            if (ai.publicSourceDir != null) size += new File(ai.publicSourceDir).length();
            if (ai.splitSourceDirs != null) {
                for (String split : ai.splitSourceDirs) size += new File(split).length();
            }
        } catch (Exception ignored) {}
        return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
    }

    private void sortAndAssignIndex() {
        Collections.sort(allApps, (a, b) -> {
            if (a.isFavorite != b.isFavorite) return a.isFavorite ? -1 : 1;
            if (a.isDetected() != b.isDetected()) return a.isDetected() ? -1 : 1;
            return Collator.getInstance().compare(a.label, b.label);
        });
        for (int i = 0; i < allApps.size(); i++) allApps.get(i).absoluteIndex = i + 1;
    }

    private void filterApps(String query) {
        apps.clear();
        if (query == null || query.isEmpty()) {
            apps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            for (AppInfo app : allApps) {
                if (app.label.toLowerCase(Locale.ROOT).contains(lowerQuery)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
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
        final boolean isAutoDetected;
        final boolean isCustom;
        boolean isFavorite;
        final String sizeStr;
        int absoluteIndex;

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent,
                boolean isAutoDetected, boolean isCustom, boolean isFavorite, String sizeStr) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isAutoDetected = isAutoDetected;
            this.isCustom = isCustom;
            this.isFavorite = isFavorite;
            this.sizeStr = sizeStr;
        }

        boolean isDetected() {
            return isAutoDetected || isCustom;
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, parent, false);
                holder = new ViewHolder();
                holder.root = convertView.findViewById(R.id.row_root);
                holder.index = convertView.findViewById(R.id.tv_index);
                holder.icon = convertView.findViewById(R.id.app_icon);
                holder.name = convertView.findViewById(R.id.app_name);
                holder.pkg = convertView.findViewById(R.id.app_package);
                holder.check = convertView.findViewById(R.id.check_mark);
                holder.fav = convertView.findViewById(R.id.tv_favorite);
                holder.size = convertView.findViewById(R.id.tv_size);
                holder.mark = convertView.findViewById(R.id.tv_mark_vpn);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo app = apps.get(position);
            holder.index.setText(String.valueOf(app.absoluteIndex));
            holder.icon.setImageDrawable(app.icon);
            holder.name.setText(app.label);
            holder.pkg.setText(app.packageName);
            holder.size.setText(app.sizeStr);
            holder.fav.setVisibility(app.isFavorite ? View.VISIBLE : View.GONE);
            
            holder.root.setBackgroundColor(isDarkMode
                    ? Color.parseColor("#2C2C2C")
                    : Color.parseColor("#66FFFFFF"));

            if (isDarkMode) {
                holder.name.setTextColor(Color.WHITE);
                holder.pkg.setTextColor(Color.parseColor("#BDBDBD"));
                holder.index.setTextColor(Color.parseColor("#BDBDBD"));
                holder.size.setTextColor(Color.parseColor("#BDBDBD"));
                holder.mark.setTextColor(Color.parseColor("#E0E0E0"));
            } else {
                holder.name.setTextColor(Color.parseColor("#212121"));
                holder.pkg.setTextColor(Color.parseColor("#757575"));
                holder.index.setTextColor(Color.parseColor("#757575"));
                holder.size.setTextColor(Color.parseColor("#757575"));
                holder.mark.setTextColor(Color.parseColor("#888888"));
            }

            if (app.isDetected()) {
                holder.check.setVisibility(View.VISIBLE);
                holder.mark.setVisibility(View.GONE);
                holder.mark.setOnClickListener(null);
            } else if (isShowAll) {
                holder.check.setVisibility(View.GONE);
                holder.mark.setVisibility(View.VISIBLE);
                holder.mark.setOnClickListener(v -> markAsVpn(app.packageName));
            } else {
                holder.check.setVisibility(View.GONE);
                holder.mark.setVisibility(View.GONE);
                holder.mark.setOnClickListener(null);
            }

            holder.fav.setOnClickListener(v -> {
                toggleFavorite(app.packageName);
                holder.fav.setVisibility(app.isFavorite ? View.VISIBLE : View.GONE);
            });

            return convertView;
        }
    }

    private static class ViewHolder {
        LinearLayout root;
        TextView index;
        ImageView icon;
        TextView name;
        TextView pkg;
        TextView check;
        TextView fav;
        TextView size;
        TextView mark;
    }
}
