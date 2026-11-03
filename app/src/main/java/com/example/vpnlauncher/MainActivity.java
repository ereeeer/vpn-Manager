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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
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

import org.json.JSONObject;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "VpnManagerPrefs";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_CUSTOM = "custom_vpns";
    private static final String KEY_MARKS = "app_marks";
    private static final String KEY_VIRTUAL = "virtual_airports";
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

    private static final String[] THEME_NAMES = {
            "渐变蓝", "深色夜间", "护眼绿", "薰衣草紫", "暖橙米色", "薄荷青"
    };

    private LinearLayout rootLayout;
    private TextView titleText;
    private TextView subtitleText;
    private TextView emptyView;
    private ListView listView;
    private Button btnSettings;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private TextView tvHelp;
    private AppAdapter adapter;

    private final LruCache<String, Drawable> iconCache = new LruCache<>(150);
    private ThemeColors currentTheme = ThemeColors.THEMES[0];

    private boolean isDarkMode = false;
    private boolean isShowAll = false;
    private int scanGeneration = 0;
    private boolean receiverRegistered = false;

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    private SharedPreferences prefs;
    private Set<String> favoritePackages = new HashSet<>();
    private Set<String> customVpnPackages = new HashSet<>();
    private Map<String, String> appMarks = new HashMap<>();
    private Set<String> virtualAirports = new HashSet<>();

    private final BroadcastReceiver uninstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                String pkg = intent.getData() != null
                        ? intent.getData().getSchemeSpecificPart() : null;
                if (pkg != null) {
                    favoritePackages.remove(pkg);
                    customVpnPackages.remove(pkg);
                    appMarks.remove(pkg);
                    saveSets();
                }
                scanApps();
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                scanApps();
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
        virtualAirports = new HashSet<>(prefs.getStringSet(KEY_VIRTUAL, Collections.emptySet()));
        loadMarks();
        isShowAll = prefs.getBoolean(KEY_SHOW_ALL, false);

        rootLayout = findViewById(R.id.root_layout);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        emptyView = findViewById(R.id.empty_view);
        listView = findViewById(R.id.list);
        btnSettings = findViewById(R.id.btn_settings);
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
                .setMessage("【设置中心】点击右上角「设置」，可以添加自建机场、切换界面主题。\n\n"
                        + "【自动刷新】安装、卸载应用以及修改标签、增删机场后，列表会自动更新。\n\n"
                        + "【点击应用】弹出菜单，可打开、打标签、卸载等。\n\n"
                        + "【长按收藏】长按任意条目可直接置顶收藏。")
                .setPositiveButton("我知道了", null)
                .show());

        btnSettings.setOnClickListener(v -> {
            String[] options = {"➕ 添加自建机场", "🎨 切换界面主题"};
            new AlertDialog.Builder(this)
                    .setTitle("设置")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) showAddAirportDialog();
                        else if (which == 1) showThemeDialog();
                    })
                    .show();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return;
            showAppOptionsDialog(apps.get(position));
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return true;
            toggleFavorite(apps.get(position).packageName);
            return true;
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

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

    private void loadMarks() {
        appMarks.clear();
        String marksJson = prefs.getString(KEY_MARKS, "{}");
        try {
            JSONObject jsonObject = new JSONObject(marksJson);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                appMarks.put(key, jsonObject.getString(key));
            }
        } catch (Exception ignored) {}
    }

    private void saveSets() {
        JSONObject jsonObject = new JSONObject(appMarks);
        prefs.edit()
                .putStringSet(KEY_FAV, new HashSet<>(favoritePackages))
                .putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages))
                .putStringSet(KEY_VIRTUAL, new HashSet<>(virtualAirports))
                .putString(KEY_MARKS, jsonObject.toString())
                .apply();
    }

    private void showAddAirportDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：一元机场、XX专线");
        new AlertDialog.Builder(this)
                .setTitle("添加自建机场")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (virtualAirports.contains(name)) {
                            Toast.makeText(this, "该机场已存在", Toast.LENGTH_SHORT).show();
                        } else {
                            virtualAirports.add(name);
                            saveSets();
                            scanApps();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameAirportDialog(AppInfo app) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(app.label);
        new AlertDialog.Builder(this)
                .setTitle("修改机场名称")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(app.label)) {
                        virtualAirports.remove(app.label);
                        virtualAirports.add(newName);
                        saveSets();
                        scanApps();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAirport(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("删除机场")
                .setMessage("确定删除自建机场「" + app.label + "」？")
                .setPositiveButton("删除", (dialog, which) -> {
                    virtualAirports.remove(app.label);
                    saveSets();
                    scanApps();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppOptionsDialog(AppInfo app) {
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (app.isVirtual) {
            options.add("📝 修改名称");
            actions.add(() -> showRenameAirportDialog(app));
            options.add("🗑️ 删除机场");
            actions.add(() -> deleteAirport(app));
        } else {
            options.add("🚀 打开应用");
            actions.add(() -> {
                if (app.launchIntent != null) {
                    try {
                        startActivity(app.launchIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            options.add("🏷️ 添加/修改标签");
            actions.add(() -> showMarkDialog(app));

            options.add("📋 复制包名");
            actions.add(() -> copyPackage(app.packageName));

            options.add("⚙️ 应用信息");
            actions.add(() -> jumpToSettings(app.packageName));

            if (app.isCustom) {
                options.add("❌ 取消收编");
                actions.add(() -> unmarkVpn(app.packageName));
            }

            options.add("🗑️ 卸载应用");
            actions.add(() -> confirmUninstall(app));
        }

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(options.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void showMarkDialog(AppInfo app) {
        String[] marks = {"最低收费", "最高收费", "稳定", "下载速度快", "免费好用", "清除标签"};
        new AlertDialog.Builder(this)
                .setTitle("选择标签")
                .setItems(marks, (dialog, which) -> {
                    if (which == 5) {
                        appMarks.remove(app.packageName);
                        saveSets();
                        scanApps();
                    } else {
                        applyMark(app.packageName, marks[which]);
                    }
                })
                .show();
    }

    private void applyMark(String packageName, String mark) {
        appMarks.put(packageName, mark);
        customVpnPackages.add(packageName);
        saveSets();
        scanApps();
    }

    private void unmarkVpn(String packageName) {
        customVpnPackages.remove(packageName);
        appMarks.remove(packageName);
        saveSets();
        scanApps();
    }

    private void toggleFavorite(String packageName) {
        if (favoritePackages.contains(packageName)) favoritePackages.remove(packageName);
        else favoritePackages.add(packageName);
        saveSets();

        for (AppInfo a : allApps) {
            a.isFavorite = favoritePackages.contains(a.packageName);
        }
        int first = listView.getFirstVisiblePosition();
        View topView = listView.getChildAt(0);
        int top = topView == null ? 0 : topView.getTop();
        sortAndAssignIndex();
        filterApps(searchBox.getText().toString());
        listView.setSelectionFromTop(first, top);
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

    private void showThemeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("选择界面主题")
                .setSingleChoiceItems(THEME_NAMES, prefs.getInt(KEY_THEME, 0),
                        (dialog, which) -> {
                            applyTheme(which, true);
                            dialog.dismiss();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyTheme(int which, boolean persist) {
        if (which < 0 || which >= ThemeColors.THEMES.length) which = 0;
        currentTheme = ThemeColors.THEMES[which];
        isDarkMode = currentTheme.dark;

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{currentTheme.backgroundStart, currentTheme.backgroundEnd});
        rootLayout.setBackground(background);

        titleText.setTextColor(currentTheme.text);
        if (subtitleText != null) subtitleText.setTextColor(currentTheme.secondary);
        showAllCheckbox.setTextColor(currentTheme.text);
        tvHelp.setTextColor(Color.WHITE);
        tvHelp.setBackground(circleDrawable(currentTheme.accent));
        emptyView.setTextColor(currentTheme.secondary);

        searchBox.setTextColor(currentTheme.text);
        searchBox.setHintTextColor(currentTheme.secondary);
        searchBox.setBackground(roundDrawable(currentTheme.searchBackground,
                currentTheme.stroke, dp(18)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showAllCheckbox.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{currentTheme.accent, currentTheme.secondary}));
        }

        btnSettings.setTextColor(currentTheme.text);
        btnSettings.setBackground(roundDrawable(currentTheme.card,
                currentTheme.stroke, dp(16)));
        btnSettings.setAllCaps(false);

        if (persist) prefs.edit().putInt(KEY_THEME, which).apply();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private GradientDrawable roundDrawable(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable circleDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        final Map<String, String> marksSnap = new HashMap<>(appMarks);
        final Set<String> virtualSnap = new HashSet<>(virtualAirports);
        final boolean showAll = isShowAll;
        final String self = getPackageName();

        emptyView.setText("正在扫描…");
        int first = listView.getFirstVisiblePosition();
        View topView = listView.getChildAt(0);
        int top = topView == null ? 0 : topView.getTop();

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> vpnPackages = new HashSet<>();
            List<ResolveInfo> vpnServices =
                    pm.queryIntentServices(new Intent(VPN_SERVICE_ACTION), 0);
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
                    String mark = marksSnap.get(pkg);
                    boolean isAuto = vpnPackages.contains(pkg) || matchesKeyword(label, pkg);
                    boolean isCustom = customSnap.contains(pkg);
                    boolean isFav = favSnap.contains(pkg);

                    if (!showAll && !isAuto && !isCustom && !isFav && mark == null) continue;

                    Drawable icon = iconCache.get(pkg);
                    if (icon == null) {
                        try {
                            icon = pm.getApplicationIcon(ai);
                        } catch (Exception e) {
                            icon = pm.getDefaultActivityIcon();
                        }
                        iconCache.put(pkg, icon);
                    }
                    found.add(new AppInfo(label, pkg, icon, launch, isAuto,
                            isCustom, isFav, mark, formatSize(ai), false));
                }
            }

            for (String airportName : virtualSnap) {
                String fakePkg = "virtual_" + airportName.hashCode();
                Drawable icon = pm.getDefaultActivityIcon();
                boolean isFav = favSnap.contains(fakePkg);
                found.add(new AppInfo(airportName, fakePkg, icon, null,
                        false, true, isFav, "机场", "自建", true));
            }

            runOnUiThread(() -> {
                if (generation != scanGeneration || isFinishing() || isDestroyed()) return;
                if (listView.getAdapter() == null) return;

                allApps.clear();
                allApps.addAll(found);
                sortAndAssignIndex();
                filterApps(searchBox.getText().toString());
                listView.post(() -> listView.setSelectionFromTop(first, top));

                if (found.isEmpty()) {
                    emptyView.setText(showAll
                            ? "没有可启动的第三方应用"
                            : "没有识别到 VPN 应用\n勾选「显示全部应用」后，可手动收编");
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
        return String.format(Locale.getDefault(), "%.1f MB",
                size / (1024.0 * 1024.0));
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
        boolean isCustom;
        boolean isFavorite;
        String mark;
        final String sizeStr;
        final boolean isVirtual;
        int absoluteIndex;

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent,
                boolean isAutoDetected, boolean isCustom, boolean isFavorite,
                String mark, String sizeStr, boolean isVirtual) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isAutoDetected = isAutoDetected;
            this.isCustom = isCustom;
            this.isFavorite = isFavorite;
            this.mark = mark;
            this.sizeStr = sizeStr;
            this.isVirtual = isVirtual;
        }

        boolean isDetected() {
            return isAutoDetected || isCustom || mark != null || isVirtual;
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
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.row_app, parent, false);
                holder = new ViewHolder();
                holder.root = convertView.findViewById(R.id.row_root);
                holder.index = convertView.findViewById(R.id.tv_index);
                holder.icon = convertView.findViewById(R.id.app_icon);
                holder.name = convertView.findViewById(R.id.app_name);
                holder.mark = convertView.findViewById(R.id.tv_custom_mark);
                holder.check = convertView.findViewById(R.id.check_mark);
                holder.fav = convertView.findViewById(R.id.tv_favorite);
                holder.size = convertView.findViewById(R.id.tv_size);
                holder.plus = convertView.findViewById(R.id.tv_mark_vpn);
                holder.arrow = convertView.findViewById(R.id.tv_arrow);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo app = apps.get(position);
            ThemeColors theme = currentTheme;

            holder.index.setText(String.valueOf(app.absoluteIndex));
            holder.index.setTextColor(theme.secondary);
            holder.index.setBackground(circleDrawable(theme.indexBackground));

            if (app.isVirtual) holder.icon.setImageResource(android.R.drawable.ic_menu_compass);
            else holder.icon.setImageDrawable(app.icon);

            holder.name.setText(app.label);
            holder.name.setTextColor(theme.text);
            holder.size.setText(app.sizeStr);
            holder.size.setTextColor(theme.secondary);
            holder.fav.setVisibility(app.isFavorite ? View.VISIBLE : View.GONE);
            holder.arrow.setTextColor(theme.secondary);

            if (app.mark != null && !app.mark.isEmpty()) {
                holder.mark.setText(app.mark);
                holder.mark.setVisibility(View.VISIBLE);
            } else {
                holder.mark.setVisibility(View.GONE);
            }

            holder.root.setBackground(roundDrawable(theme.card, theme.cardStroke, dp(20)));

            if (app.isDetected()) {
                holder.check.setVisibility(View.VISIBLE);
                holder.check.setText("✓");
                holder.check.setTextColor(Color.WHITE);
                holder.check.setBackground(circleDrawable(Color.rgb(48, 190, 98)));
                holder.plus.setVisibility(View.GONE);
                holder.plus.setOnClickListener(null);
            } else if (isShowAll) {
                holder.check.setVisibility(View.GONE);
                holder.plus.setTextColor(theme.accent);
                holder.plus.setVisibility(View.VISIBLE);
                holder.plus.setOnClickListener(v -> applyMark(app.packageName, "手动收编"));
            } else {
                holder.check.setVisibility(View.GONE);
                holder.plus.setVisibility(View.GONE);
                holder.plus.setOnClickListener(null);
            }

            holder.fav.setOnClickListener(v -> toggleFavorite(app.packageName));
            return convertView;
        }
    }

    private static clas        
