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
    private static final String KEY_MARKS = "app_marks"; // 新增：自定义标记 Map
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
    private Map<String, String> appMarks = new HashMap<>();

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
        loadMarks();
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
                .setMessage("【点击】弹出操作菜单，不再直接启动。\n\n"
                        + "【打标记】给软件打上标记（如：稳定、机场名）。一旦打上标记，它会自动被收编为 VPN。\n\n"
                        + "【收藏与排序】长按可直接收藏置顶，排序：收藏 > 已识别/标记 > 名称。\n\n"
                        + "【显示全部】勾选后可看到所有 App。")
                .setPositiveButton("我知道了", null)
                .show());

        themeButton.setOnClickListener(v -> showThemeDialog());

        // 【改版核心】：短按弹出菜单，不再直接打开
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return;
            AppInfo app = apps.get(position);
            showAppOptionsDialog(app);
        });

        // 长按直接切换收藏，方便快捷
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return true;
            AppInfo app = apps.get(position);
            toggleFavorite(app.packageName);
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

    // 解析并加载标记
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

    // 保存标记、收藏、收编
    private void saveSets() {
        JSONObject jsonObject = new JSONObject(appMarks);
        prefs.edit()
                .putStringSet(KEY_FAV, new HashSet<>(favoritePackages))
                .putStringSet(KEY_CUSTOM, new HashSet<>(customVpnPackages))
                .putString(KEY_MARKS, jsonObject.toString())
                .apply();
    }

    // 短按弹出的核心功能菜单
    private void showAppOptionsDialog(AppInfo app) {
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // 1. 打开
        options.add("🚀 打开应用");
        actions.add(() -> {
            if (app.launchIntent != null) {
                try { startActivity(app.launchIntent); } 
                catch (Exception e) { Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show(); }
            }
        });

        // 2. 打标记
        options.add("🏷️ 添加/修改标记");
        actions.add(() -> showMarkDialog(app));

        // 3. 复制包名
        options.add("📋 复制包名");
        actions.add(() -> copyPackage(app.packageName));

        // 4. 应用信息
        options.add("⚙️ 应用信息");
        actions.add(() -> jumpToSettings(app.packageName));

        // 5. 取消收编
        if (app.isCustom) {
            options.add("❌ 取消收编");
            actions.add(() -> unmarkVpn(app.packageName));
        }

        // 6. 卸载
        options.add("🗑️ 卸载应用");
        actions.add(() -> confirmUninstall(app));

        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(items, (dialog, which) -> actions.get(which).run())
                .show();
    }

    // 标记选择弹窗
    private void showMarkDialog(AppInfo app) {
        String[] marks = {"最低收费", "最高收费", "稳定", "下载速度快", "免费好用", "自建标记 (输入机场名等)"};
        new AlertDialog.Builder(this)
                .setTitle("选择或自定义标记")
                .setItems(marks, (dialog, which) -> {
                    if (which == 5) {
                        showCustomMarkInput(app);
                    } else {
                        applyMark(app.packageName, marks[which]);
                    }
                })
                .show();
    }

    // 自定义标记输入框
    private void showCustomMarkInput(AppInfo app) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("输入标记，如：XX机场、自用");
        if (app.mark != null) {
            input.setText(app.mark);
        }
        new AlertDialog.Builder(this)
                .setTitle("自定义标记")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) applyMark(app.packageName, text);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 应用标记（打上标记后，自动归为 VPN / 收编）
    private void applyMark(String packageName, String mark) {
        appMarks.put(packageName, mark);
        customVpnPackages.add(packageName); // 打标记自动视为收编
        saveSets();
        for (AppInfo a : allApps) {
            if (a.packageName.equals(packageName)) {
                a.mark = mark;
                a.isCustom = true;
            }
        }
        sortAndAssignIndex();
        filterApps(searchBox.getText().toString());
        Toast.makeText(this, "已标记为：" + mark + "（已自动收编）", Toast.LENGTH_SHORT).show();
    }

    private void unmarkVpn(String packageName) {
        customVpnPackages.remove(packageName);
        appMarks.remove(packageName); // 取消收编顺便清空标记
        saveSets();
        scanApps();
    }

    private void toggleFavorite(String packageName) {
        if (favoritePackages.contains(packageName)) {
            favoritePackages.remove(packageName);
        } else {
            favoritePackages.add(packageName);
        }
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
        final Map<String, String> marksSnap = new HashMap<>(appMarks);
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
                    
                    String mark = marksSnap.get(pkg);
                    boolean isAuto = vpnPackages.contains(pkg) || matchesKeyword(label, pkg);
                    boolean isCustom = customSnap.contains(pkg);
                    boolean isFav = favSnap.contains(pkg);
                    
                    // 核心逻辑：如果被打上了标记（mark不为空），也默认强制显示
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
                    found.add(new AppInfo(label, pkg, icon, launch, isAuto, isCustom, isFav, mark, formatSize(ai)));
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
        boolean isCustom;
        boolean isFavorite;
        String mark; // 标记
        final String sizeStr;
        int absoluteIndex;

        AppInfo(String label, String packageName, Drawable icon, Intent launchIntent,
                boolean isAutoDetected, boolean isCustom, boolean isFavorite, String mark, String sizeStr) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.isAutoDetected = isAutoDetected;
            this.isCustom = isCustom;
            this.isFavorite = isFavorite;
            this.mark = mark;
            this.sizeStr = sizeStr;
        }

        boolean isDetected() {
            return isAutoDetected || isCustom || mark != null;
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
                holder.mark = convertView.findViewById(R.id.tv_custom_mark);
                holder.check = convertView.findViewById(R.id.check_mark);
                holder.fav = convertView.findViewById(R.id.tv_favorite);
                holder.size = convertView.findViewById(R.id.tv_size);
                holder.plus = convertView.findViewById(R.id.tv_mark_vpn);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo app = apps.get(position);
            holder.index.setText(String.valueOf(app.absoluteIndex));
            holder.icon.setImageDrawable(app.icon);
            holder.name.setText(app.label);
            holder.size.setText(app.sizeStr);
            holder.fav.setVisibility(app.isFavorite ? View.VISIBLE : View.GONE);
            
            if (app.mark != null && !app.mark.isEmpty()) {
                holder.mark.setText("[" + app.mark + "]");
                holder.mark.setVisibility(View.VISIBLE);
            } else {
                holder.mark.setVisibility(View.GONE);
            }
            
            holder.root.setBackgroundColor(isDarkMode
                    ? Color.parseColor("#2C2C2C")
                    : Color.parseColor("#66FFFFFF"));

            if (isDarkMode) {
                holder.name.setTextColor(Color.WHITE);
                holder.size.setTextColor(Color.parseColor("#BDBDBD"));
                holder.index.setTextColor(Color.parseColor("#BDBDBD"));
                holder.plus.setTextColor(Color.parseColor("#E0E0E0"));
            } else {
                holder.name.setTextColor(Color.parseColor("#212121"));
                holder.size.setTextColor(Color.parseColor("#757575"));
                holder.index.setTextColor(Color.parseColor("#757575"));
                holder.plus.setTextColor(Color.parseColor("#888888"));
            }

            if (app.isDetected()) {
                holder.check.setVisibility(View.VISIBLE);
                holder.plus.setVisibility(View.GONE);
                holder.plus.setOnClickListener(null);
            } else if (isShowAll) {
                holder.check.setVisibility(View.GONE);
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

    private static class ViewHolder {
        LinearLayout root;
        TextView index;
        ImageView icon;
        TextView name;
        TextView mark;
        TextView check;
        TextView fav;
        TextView size;
        TextView plus;
    }
                          }
