package com.example.vpnlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/* loaded from: classes2.dex */
public class MainActivity extends Activity {
    private static final String KEY_CUSTOM = "custom_vpns";
    private static final String KEY_FAV = "favorites";
    private static final String KEY_MARKS = "app_marks";
    private static final String KEY_SHOW_ALL = "show_all";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_UNINSTALLED = "uninstalled_history";
    private static final String KEY_VIRTUAL = "virtual_airports";
    private static final String PREFS = "VpnManagerPrefs";
    private static final String VPN_SERVICE_ACTION = "android.net.VpnService";
    private AppAdapter adapter;
    private TextView btnClearSearch;
    private TextView btnSettings;
    private TextView emptyView;
    private ListView listView;
    private SharedPreferences prefs;
    private LinearLayout rootLayout;
    private EditText searchBox;
    private CheckBox showAllCheckbox;
    private TextView subtitleText;
    private TextView titleText;
    private TextView tvHelp;
    private static final String[] KEYWORDS = {"vpn", "代理", "proxy", "加速", "梯子", "翻墙", "clash", "v2ray", "xray", "ssr", "shadowsocks", "surfboard", "trojan", "wireguard", "openvpn", "hysteria", "sing-box", "singbox", "nekobox", "sagernet", "outline", "hiddify", "roxi", "tuic"};
    private static final String[] EXCLUDE_KEYWORDS = {"清理", "杀毒", "病毒", "安全", "卫士", "内存", "优化", "防骚扰", "拦截"};
    private static final String[] THEME_NAMES = {"渐变蓝", "深色夜间", "护眼绿", "薰衣草紫", "暖橙米色", "薄荷青"};
    private final LruCache<String, Drawable> iconCache = new LruCache<>(150);
    private ThemeColors currentTheme = ThemeColors.THEMES[0];
    private boolean isShowAll = false;
    private boolean receiverRegistered = false;
    private int scanGeneration = 0;
    private final List<AppInfo> allApps = new ArrayList();
    private final List<AppInfo> apps = new ArrayList();
    private Set<String> favoritePackages = new HashSet();
    private Set<String> customVpnPackages = new HashSet();
    private Set<String> virtualAirports = new HashSet();
    private Set<String> uninstalledHistory = new HashSet();
    private Map<String, String> appMarks = new HashMap();
    private final BroadcastReceiver uninstallReceiver = new BroadcastReceiver() { // from class: com.example.vpnlauncher.MainActivity.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (!"android.intent.action.PACKAGE_REMOVED".equals(action)) {
                if ("android.intent.action.PACKAGE_ADDED".equals(action)) {
                    MainActivity.this.scanApps();
                }
            } else {
                if (intent.getBooleanExtra("android.intent.extra.REPLACING", false)) {
                    return;
                }
                String pkg = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
                if (pkg != null) {
                    MainActivity.this.favoritePackages.remove(pkg);
                    MainActivity.this.customVpnPackages.remove(pkg);
                    MainActivity.this.appMarks.remove(pkg);
                    MainActivity.this.uninstalledHistory.add(pkg);
                    MainActivity.this.saveSets();
                }
                MainActivity.this.scanApps();
            }
        }
    };

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.prefs = getSharedPreferences(PREFS, 0);
        this.favoritePackages = new HashSet(this.prefs.getStringSet(KEY_FAV, Collections.emptySet()));
        this.customVpnPackages = new HashSet(this.prefs.getStringSet(KEY_CUSTOM, Collections.emptySet()));
        this.virtualAirports = new HashSet(this.prefs.getStringSet(KEY_VIRTUAL, Collections.emptySet()));
        this.uninstalledHistory = new HashSet(this.prefs.getStringSet(KEY_UNINSTALLED, Collections.emptySet()));
        migrateVirtualFavoriteKeys();
        loadMarks();
        this.isShowAll = this.prefs.getBoolean(KEY_SHOW_ALL, false);
        this.rootLayout = (LinearLayout) findViewById(R.id.root_layout);
        this.titleText = (TextView) findViewById(R.id.title_text);
        this.subtitleText = (TextView) findViewById(R.id.subtitle_text);
        this.emptyView = (TextView) findViewById(R.id.empty_view);
        this.listView = (ListView) findViewById(R.id.list);
        this.btnSettings = (TextView) findViewById(R.id.btn_settings);
        this.searchBox = (EditText) findViewById(R.id.search_box);
        this.showAllCheckbox = (CheckBox) findViewById(R.id.show_all_checkbox);
        this.tvHelp = (TextView) findViewById(R.id.tv_help);
        this.btnClearSearch = (TextView) findViewById(R.id.btn_clear_search);
        this.adapter = new AppAdapter();
        this.listView.setEmptyView(this.emptyView);
        this.listView.setAdapter((ListAdapter) this.adapter);
        applyTheme(this.prefs.getInt(KEY_THEME, 0), false);
        this.showAllCheckbox.setOnCheckedChangeListener(null);
        this.showAllCheckbox.setChecked(this.isShowAll);
        this.showAllCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda10
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                MainActivity.this.lambda$onCreate$0(compoundButton, z);
            }
        });
        this.tvHelp.setOnClickListener(new View.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$1(view);
            }
        });
        this.btnClearSearch.setOnClickListener(new View.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$2(view);
            }
        });
        this.btnSettings.setOnClickListener(new View.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$onCreate$4(view);
            }
        });
        this.listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda15
            @Override // android.widget.AdapterView.OnItemClickListener
            public final void onItemClick(AdapterView adapterView, View view, int i, long j) {
                MainActivity.this.lambda$onCreate$5(adapterView, view, i, j);
            }
        });
        this.listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda16
            @Override // android.widget.AdapterView.OnItemLongClickListener
            public final boolean onItemLongClick(AdapterView adapterView, View view, int i, long j) {
                boolean lambda$onCreate$6;
                lambda$onCreate$6 = MainActivity.this.lambda$onCreate$6(adapterView, view, i, j);
                return lambda$onCreate$6;
            }
        });
        this.searchBox.addTextChangedListener(new TextWatcher() { // from class: com.example.vpnlauncher.MainActivity.2
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                MainActivity.this.filterApps(s.toString());
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable s) {
                MainActivity.this.btnClearSearch.setVisibility(s.length() > 0 ? 0 : 8);
            }
        });
        IntentFilter f = new IntentFilter();
        f.addAction("android.intent.action.PACKAGE_REMOVED");
        f.addAction("android.intent.action.PACKAGE_ADDED");
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.uninstallReceiver, f, 4);
        } else {
            registerReceiver(this.uninstallReceiver, f);
        }
        this.receiverRegistered = true;
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(CompoundButton v, boolean c) {
        this.isShowAll = c;
        this.prefs.edit().putBoolean(KEY_SHOW_ALL, c).apply();
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View v) {
        new AlertDialog.Builder(this).setTitle("使用说明").setMessage("【设置中心】点击右上角「设置」，可以添加自有机场、切换界面主题。\n\n【卸载追踪】如果你卸载了某个应用，以后重新安装时，它会带上「曾卸载」的标签。\n\n【收费标签】点击标签选「最低/最高收费」时，可自己输入具体金额。").setPositiveButton("我知道了", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$2(View v) {
        this.searchBox.setText("");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$3(DialogInterface d, int w) {
        if (w == 0) {
            showAddAirportDialog();
        } else {
            showThemeDialog();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$4(View v) {
        String[] ops = {"➕ 自有机场", "🎨 切换界面主题"};
        new AlertDialog.Builder(this).setTitle("设置").setItems(ops, new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda18
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$onCreate$3(dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$5(AdapterView p, View v, int pos, long id) {
        if (pos < 0 || pos >= this.apps.size()) {
            return;
        }
        showAppOptionsDialog(this.apps.get(pos));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$onCreate$6(AdapterView p, View v, int pos, long id) {
        if (pos < 0 || pos >= this.apps.size()) {
            return true;
        }
        toggleFavorite(this.apps.get(pos).packageName);
        return true;
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        if (this.receiverRegistered) {
            try {
                unregisterReceiver(this.uninstallReceiver);
            } catch (Exception e) {
            }
            this.receiverRegistered = false;
        }
    }

    private static String virtualPackageKey(String name) {
        return "virtual_" + Base64.encodeToString(name.getBytes(StandardCharsets.UTF_8), 11);
    }

    private void migrateVirtualFavoriteKeys() {
        boolean changed = false;
        for (String name : this.virtualAirports) {
            String oldKey = "virtual_" + name.hashCode();
            String newKey = virtualPackageKey(name);
            if (this.favoritePackages.remove(oldKey)) {
                this.favoritePackages.add(newKey);
                changed = true;
            }
        }
        if (changed) {
            saveSets();
        }
    }

    private void loadMarks() {
        this.appMarks.clear();
        try {
            JSONObject jsonObject = new JSONObject(this.prefs.getString(KEY_MARKS, "{}"));
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                this.appMarks.put(key, jsonObject.getString(key));
            }
        } catch (Exception e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveSets() {
        this.prefs.edit().putStringSet(KEY_FAV, new HashSet(this.favoritePackages)).putStringSet(KEY_CUSTOM, new HashSet(this.customVpnPackages)).putStringSet(KEY_VIRTUAL, new HashSet(this.virtualAirports)).putStringSet(KEY_UNINSTALLED, new HashSet(this.uninstalledHistory)).putString(KEY_MARKS, new JSONObject(this.appMarks).toString()).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAddAirportDialog$7(EditText input, DialogInterface dialog, int which) {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            return;
        }
        if (this.virtualAirports.contains(name)) {
            Toast.makeText(this, "已存在", 0).show();
            return;
        }
        this.virtualAirports.add(name);
        saveSets();
        scanApps();
    }

    private void showAddAirportDialog() {
        final EditText input = new EditText(this);
        input.setInputType(1);
        input.setHint("例如：一元机场");
        new AlertDialog.Builder(this).setTitle("添加自有机场").setView(input).setPositiveButton("添加", new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda21
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showAddAirportDialog$7(input, dialogInterface, i);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showRenameAirportDialog$8(EditText input, AppInfo app, DialogInterface dialog, int which) {
        String newName = input.getText().toString().trim();
        if (newName.isEmpty() || newName.equals(app.label)) {
            return;
        }
        if (this.virtualAirports.contains(newName)) {
            Toast.makeText(this, "已存在", 0).show();
            return;
        }
        String oldKey = app.packageName;
        String newKey = virtualPackageKey(newName);
        if (this.favoritePackages.remove(oldKey)) {
            this.favoritePackages.add(newKey);
        }
        this.virtualAirports.remove(app.label);
        this.virtualAirports.add(newName);
        saveSets();
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: showRenameAirportDialog, reason: merged with bridge method [inline-methods] */
    public void lambda$showAppOptionsDialog$10(final AppInfo app) {
        final EditText input = new EditText(this);
        input.setInputType(1);
        input.setText(app.label);
        new AlertDialog.Builder(this).setTitle("修改名称").setView(input).setPositiveButton("保存", new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda0
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showRenameAirportDialog$8(input, app, dialogInterface, i);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: deleteAirport, reason: merged with bridge method [inline-methods] */
    public void lambda$showAppOptionsDialog$11(final AppInfo app) {
        new AlertDialog.Builder(this).setTitle("删除").setMessage("确定删除？").setPositiveButton("删除", new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda22
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$deleteAirport$9(app, dialogInterface, i);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$deleteAirport$9(AppInfo app, DialogInterface dialog, int which) {
        this.virtualAirports.remove(app.label);
        this.favoritePackages.remove(app.packageName);
        saveSets();
        scanApps();
    }

    private void showAppOptionsDialog(final AppInfo app) {
        List<String> options = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (app.isVirtual) {
            options.add("📝 修改名称");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda25
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$10(app);
                }
            });
            options.add("🗑️ 删除机场");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda26
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$11(app);
                }
            });
        } else {
            options.add("🚀 打开应用");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$12(app);
                }
            });
            options.add("🏷️ 标签");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$13(app);
                }
            });
            options.add("📋 复制包名");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$14(app);
                }
            });
            options.add("⚙️ 应用信息");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda4
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$15(app);
                }
            });
            if (app.isCustom) {
                options.add("❌ 取消收编");
                actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$showAppOptionsDialog$16(app);
                    }
                });
            }
            if (app.isReinstalled) {
                options.add("✨ 清除曾卸载标记");
                actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda6
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$showAppOptionsDialog$17(app);
                    }
                });
            }
            options.add("🗑️ 卸载");
            actions.add(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda7
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$showAppOptionsDialog$18(app);
                }
            });
        }
        new AlertDialog.Builder(this).setTitle(app.label).setItems((CharSequence[]) options.toArray(new String[0]), new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda8
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                ((Runnable) actions.get(i)).run();
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppOptionsDialog$12(AppInfo app) {
        if (app.launchIntent != null) {
            try {
                startActivity(app.launchIntent);
            } catch (Exception e) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppOptionsDialog$14(AppInfo app) {
        copyPackage(app.packageName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppOptionsDialog$15(AppInfo app) {
        jumpToSettings(app.packageName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppOptionsDialog$16(AppInfo app) {
        unmarkVpn(app.packageName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showAppOptionsDialog$17(AppInfo app) {
        this.uninstalledHistory.remove(app.packageName);
        saveSets();
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: showMarkDialog, reason: merged with bridge method [inline-methods] */
    public void lambda$showAppOptionsDialog$13(final AppInfo app) {
        final String[] marks = {"最低收费", "最高收费", "支持支/微", "稳定", "下载快", "免费好用", "清除标签"};
        new AlertDialog.Builder(this).setTitle("标签").setItems(marks, new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda19
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showMarkDialog$21(app, marks, dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMarkDialog$21(final AppInfo app, final String[] marks, DialogInterface dialog, final int which) {
        if (which != 6) {
            if (which == 0 || which == 1) {
                final EditText input = new EditText(this);
                input.setInputType(1);
                input.setHint("如: 15元/月");
                new AlertDialog.Builder(this).setTitle(marks[which]).setView(input).setPositiveButton("确定", new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda11
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.lambda$showMarkDialog$20(input, marks, which, app, dialogInterface, i);
                    }
                }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
                return;
            }
            applyMark(app.packageName, marks[which]);
            return;
        }
        this.appMarks.remove(app.packageName);
        saveSets();
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMarkDialog$20(EditText input, String[] marks, int which, AppInfo app, DialogInterface d, int w) {
        String amt = input.getText().toString().trim();
        String res = marks[which] + (amt.isEmpty() ? "" : ": " + amt);
        applyMark(app.packageName, res);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyMark(String p, String m) {
        this.appMarks.put(p, m);
        this.customVpnPackages.add(p);
        saveSets();
        scanApps();
    }

    private void unmarkVpn(String p) {
        this.customVpnPackages.remove(p);
        this.appMarks.remove(p);
        saveSets();
        scanApps();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleFavorite(String p) {
        if (this.favoritePackages.contains(p)) {
            this.favoritePackages.remove(p);
        } else {
            this.favoritePackages.add(p);
        }
        saveSets();
        for (AppInfo a : this.allApps) {
            a.isFavorite = this.favoritePackages.contains(a.packageName);
        }
        int f = this.listView.getFirstVisiblePosition();
        View v = this.listView.getChildAt(0);
        int t = v != null ? v.getTop() : 0;
        sortAndAssignIndex();
        filterApps(this.searchBox.getText().toString());
        this.listView.setSelectionFromTop(f, t);
    }

    private void copyPackage(String p) {
        ClipboardManager c = (ClipboardManager) getSystemService("clipboard");
        if (c != null) {
            c.setPrimaryClip(ClipData.newPlainText("pkg", p));
            Toast.makeText(this, "已复制", 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: confirmUninstall, reason: merged with bridge method [inline-methods] */
    public void lambda$showAppOptionsDialog$18(AppInfo a) {
        try {
            startActivity(new Intent("android.intent.action.DELETE", Uri.parse("package:" + a.packageName)));
        } catch (Exception e) {
            jumpToSettings(a.packageName);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showThemeDialog$22(DialogInterface d, int w) {
        applyTheme(w, true);
        d.dismiss();
    }

    private void showThemeDialog() {
        int s = this.prefs.getInt(KEY_THEME, 0);
        if (s < 0 || s >= THEME_NAMES.length) {
            s = 0;
        }
        new AlertDialog.Builder(this).setTitle("主题").setSingleChoiceItems(THEME_NAMES, s, new DialogInterface.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda20
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showThemeDialog$22(dialogInterface, i);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    private void applyTheme(int w, boolean p) {
        if (w < 0 || w >= ThemeColors.THEMES.length) {
            w = 0;
        }
        this.currentTheme = ThemeColors.THEMES[w];
        this.rootLayout.setBackground(new ThemeBackgroundDrawable(this.currentTheme));
        this.titleText.setTextColor(this.currentTheme.text);
        if (this.subtitleText != null) {
            this.subtitleText.setTextColor(this.currentTheme.secondary);
        }
        this.showAllCheckbox.setTextColor(this.currentTheme.text);
        this.tvHelp.setTextColor(-1);
        this.tvHelp.setBackground(circleDrawable(this.currentTheme.accent));
        this.emptyView.setTextColor(this.currentTheme.secondary);
        this.searchBox.setTextColor(this.currentTheme.text);
        this.searchBox.setHintTextColor(this.currentTheme.secondary);
        this.showAllCheckbox.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[0]}, new int[]{this.currentTheme.accent, this.currentTheme.secondary}));
        this.btnSettings.setTextColor(this.currentTheme.text);
        this.btnSettings.setBackground(roundDrawable(this.currentTheme.card, this.currentTheme.cardStroke, dp(16.0f)));
        this.btnClearSearch.setTextColor(this.currentTheme.secondary);
        if (p) {
            this.prefs.edit().putInt(KEY_THEME, w).apply();
        }
        if (this.adapter != null) {
            this.adapter.notifyDataSetChanged();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public GradientDrawable roundDrawable(int c, int s, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(r);
        if (s != 0) {
            d.setStroke(dp(1.0f), s);
        }
        return d;
    }

    private GradientDrawable circleDrawable(int c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(1);
        d.setColor(c);
        return d;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(float v) {
        return (int) ((getResources().getDisplayMetrics().density * v) + 0.5f);
    }

    private void jumpToSettings(String p) {
        try {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + p)));
        } catch (Exception e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scanApps() {
        final int gen = this.scanGeneration + 1;
        this.scanGeneration = gen;
        final Set<String> fs = new HashSet<>(this.favoritePackages);
        final Set<String> cs = new HashSet<>(this.customVpnPackages);
        final Set<String> vs = new HashSet<>(this.virtualAirports);
        final Set<String> uh = new HashSet<>(this.uninstalledHistory);
        final Map<String, String> ms = new HashMap<>(this.appMarks);
        final boolean sa = this.isShowAll;
        final String self = getPackageName();
        this.emptyView.setText("正在扫描…");
        final int f = this.listView.getFirstVisiblePosition();
        View v = this.listView.getChildAt(0);
        final int t = v == null ? 0 : v.getTop();
        new Thread(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$scanApps$25(self, ms, cs, fs, uh, sa, vs, gen, f, t);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scanApps$25(String self, Map ms, Set cs, Set fs, Set uh, final boolean sa, Set vs, final int gen, final int f, final int t) {
        String err = null;
        String lb;
        boolean isExcluded;
        Drawable i;
        Set<String> vp;
        final List<AppInfo> fd = new ArrayList<>();
        String lb2 = null;
        try {
            PackageManager pm = getPackageManager();
            Set<String> vp2 = new HashSet<>();
            int i2 = 0;
            List<ResolveInfo> s = pm.queryIntentServices(new Intent(VPN_SERVICE_ACTION), 0);
            if (s != null) {
                try {
                    for (ResolveInfo ri : s) {
                        if (ri.serviceInfo != null) {
                            vp2.add(ri.serviceInfo.packageName);
                        }
                    }
                } catch (Exception e) {
                    err = e.getClass().getSimpleName();
                    final String error = err;
                    runOnUiThread(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda24
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$scanApps$24(gen, fd, f, t, error, sa);
                        }
                    });
                }
            }
            Intent li = new Intent("android.intent.action.MAIN");
            li.addCategory("android.intent.category.LAUNCHER");
            List<ResolveInfo> as = pm.queryIntentActivities(li, 0);
            Set<String> sn = new HashSet<>();
            if (as != null) {
                Iterator<ResolveInfo> it = as.iterator();
                while (it.hasNext()) {
                    ResolveInfo ri2 = it.next();
                    if (ri2.activityInfo != null) {
                        String p = ri2.activityInfo.packageName;
                        if (!sn.add(p)) {
                            it = it;
                            i2 = 0;
                        } else if (self.equals(p)) {
                            continue;
                        } else {
                            try {
                                ApplicationInfo ai = pm.getApplicationInfo(p, i2);
                                boolean z = true;
                                if ((ai.flags & 1) != 0) {
                                    i2 = 0;
                                } else {
                                    Intent l = pm.getLaunchIntentForPackage(p);
                                    if (l == null) {
                                        i2 = 0;
                                    } else {
                                        String lb3 = pm.getApplicationLabel(ai).toString();
                                        String[] strArr = EXCLUDE_KEYWORDS;
                                        String err2 = lb2;
                                        try {
                                            int length = strArr.length;
                                            List<ResolveInfo> s2 = s;
                                            int i3 = 0;
                                            while (true) {
                                                if (i3 >= length) {
                                                    lb = lb3;
                                                    isExcluded = false;
                                                    break;
                                                }
                                                String ex = strArr[i3];
                                                int i4 = length;
                                                lb = lb3;
                                                String[] strArr2 = strArr;
                                                if (lb.contains(ex)) {
                                                    isExcluded = true;
                                                    break;
                                                }
                                                i3++;
                                                lb3 = lb;
                                                length = i4;
                                                strArr = strArr2;
                                            }
                                            if (isExcluded) {
                                                lb2 = err2;
                                                s = s2;
                                                i2 = 0;
                                            } else {
                                                Iterator<ResolveInfo> it2 = it;
                                                String mk = (String) ms.get(p);
                                                if (!vp2.contains(p) && !matchesKeyword(lb, p)) {
                                                    z = false;
                                                }
                                                boolean ia = z;
                                                boolean ic = cs.contains(p);
                                                boolean ifv = fs.contains(p);
                                                try {
                                                    boolean isRe = uh.contains(p);
                                                    if (sa || ia || ic || ifv || mk != null) {
                                                        Drawable i5 = this.iconCache.get(p);
                                                        if (i5 == null) {
                                                            try {
                                                                i = pm.getApplicationIcon(ai);
                                                            } catch (Exception e2) {
                                                                i = pm.getDefaultActivityIcon();
                                                            }
                                                            vp = vp2;
                                                            this.iconCache.put(p, i);
                                                        } else {
                                                            vp = vp2;
                                                            i = i5;
                                                        }
                                                        fd.add(new AppInfo(lb, p, i, l, ia, ic, ifv, mk, formatSize(ai), false, isRe));
                                                        it = it2;
                                                        lb2 = err2;
                                                        s = s2;
                                                        vp2 = vp;
                                                        i2 = 0;
                                                    } else {
                                                        it = it2;
                                                        lb2 = err2;
                                                        s = s2;
                                                        i2 = 0;
                                                    }
                                                } catch (Exception e3) {
                                                    err = e3.getClass().getSimpleName();
                                                    final String error2 = err;
                                                    runOnUiThread(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda24
                                                        @Override // java.lang.Runnable
                                                        public final void run() {
                                                            MainActivity.this.lambda$scanApps$24(gen, fd, f, t, error2, sa);
                                                        }
                                                    });
                                                }
                                            }
                                        } catch (Exception e4) {
                                            err = e4.getClass().getSimpleName();
                                            final String error22 = err;
                                            runOnUiThread(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda24
                                                @Override // java.lang.Runnable
                                                public final void run() {
                                                    MainActivity.this.lambda$scanApps$24(gen, fd, f, t, error22, sa);
                                                }
                                            });
                                        }
                                    }
                                }
                            } catch (Exception e5) {
                                String err3 = lb2;
                                it = it;
                                lb2 = err3;
                                s = s;
                                vp2 = vp2;
                                i2 = 0;
                            }
                        }
                    }
                }
            }
            err = lb2;
            Iterator it3 = vs.iterator();
            while (it3.hasNext()) {
                String an = (String) it3.next();
                fd.add(new AppInfo(an, virtualPackageKey(an), pm.getDefaultActivityIcon(), null, false, true, fs.contains(virtualPackageKey(an)), "自建", "自有机场", true, false));
            }
        } catch (Exception e6) {
            // ignore
        }
        final String error222 = err;
        runOnUiThread(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda24
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$scanApps$24(gen, fd, f, t, error222, sa);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scanApps$24(int gen, List result, final int f, final int t, String error, boolean sa) {
        TextView textView;
        String str;
        if (gen != this.scanGeneration || isFinishing() || isDestroyed() || this.listView.getAdapter() == null) {
            return;
        }
        this.allApps.clear();
        this.allApps.addAll(result);
        sortAndAssignIndex();
        filterApps(this.searchBox.getText().toString());
        this.listView.post(new Runnable() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda23
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$scanApps$23(f, t);
            }
        });
        if (error != null) {
            textView = this.emptyView;
            str = "扫描失败";
        } else if (result.isEmpty()) {
            textView = this.emptyView;
            str = sa ? "没有应用" : "没有识别到 VPN\n勾选「显示全部」可收编";
        } else {
            if (!this.apps.isEmpty()) {
                return;
            }
            textView = this.emptyView;
            str = "无匹配";
        }
        textView.setText(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$scanApps$23(int f, int t) {
        this.listView.setSelectionFromTop(f, t);
    }

    private static boolean matchesKeyword(String l, String p) {
        String ll = l.toLowerCase(Locale.ROOT);
        String lp = p.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (ll.contains(k) || lp.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String formatSize(ApplicationInfo ai) {
        long s = 0;
        try {
            if (ai.publicSourceDir != null) {
                s = 0 + new File(ai.publicSourceDir).length();
            }
        } catch (Exception e) {
        }
        return String.format(Locale.getDefault(), "%.1f MB", Double.valueOf(s / 1048576.0d));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ int lambda$sortAndAssignIndex$26(AppInfo a, AppInfo b) {
        return a.isFavorite != b.isFavorite ? a.isFavorite ? -1 : 1 : a.isDetected() != b.isDetected() ? a.isDetected() ? -1 : 1 : Collator.getInstance().compare(a.label, b.label);
    }

    private void sortAndAssignIndex() {
        Collections.sort(this.allApps, new Comparator() { // from class: com.example.vpnlauncher.MainActivity$$ExternalSyntheticLambda17
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return MainActivity.lambda$sortAndAssignIndex$26((MainActivity.AppInfo) obj, (MainActivity.AppInfo) obj2);
            }
        });
        for (int i = 0; i < this.allApps.size(); i++) {
            this.allApps.get(i).absoluteIndex = i + 1;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void filterApps(String q) {
        this.apps.clear();
        if (q == null || q.isEmpty()) {
            this.apps.addAll(this.allApps);
        } else {
            String lq = q.toLowerCase(Locale.ROOT);
            for (AppInfo a : this.allApps) {
                if (a.label.toLowerCase(Locale.ROOT).contains(lq) || a.packageName.toLowerCase(Locale.ROOT).contains(lq)) {
                    this.apps.add(a);
                }
            }
        }
        this.adapter.notifyDataSetChanged();
        if (!this.apps.isEmpty() || this.allApps.size() <= 0) {
            return;
        }
        this.emptyView.setText("无匹配");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes2.dex */
    public static class AppInfo {
        int absoluteIndex;
        final Drawable icon;
        final boolean isAutoDetected;
        boolean isCustom;
        boolean isFavorite;
        boolean isReinstalled;
        final boolean isVirtual;
        final String label;
        final Intent launchIntent;
        String mark;
        final String packageName;
        final String sizeStr;

        AppInfo(String l, String p, Drawable i, Intent in, boolean a, boolean c, boolean f, String m, String s, boolean v, boolean re) {
            this.label = l;
            this.packageName = p;
            this.icon = i;
            this.launchIntent = in;
            this.isAutoDetected = a;
            this.isCustom = c;
            this.isFavorite = f;
            this.mark = m;
            this.sizeStr = s;
            this.isVirtual = v;
            this.isReinstalled = re;
        }

        boolean isDetected() {
            return this.isAutoDetected || this.isCustom || this.mark != null || this.isVirtual;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes2.dex */
    public class AppAdapter extends BaseAdapter {
        private AppAdapter() {
        }

        @Override // android.widget.Adapter
        public int getCount() {
            return MainActivity.this.apps.size();
        }

        @Override // android.widget.Adapter
        public Object getItem(int p) {
            return MainActivity.this.apps.get(p);
        }

        @Override // android.widget.Adapter
        public long getItemId(int p) {
            return p;
        }

        @Override // android.widget.Adapter
        public View getView(int p, View c, ViewGroup pr) {
            ViewHolder h;
            if (c == null) {
                c = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_app, pr, false);
                h = new ViewHolder();
                h.root = (LinearLayout) c.findViewById(R.id.row_root);
                h.index = (TextView) c.findViewById(R.id.tv_index);
                h.icon = (ImageView) c.findViewById(R.id.app_icon);
                h.name = (TextView) c.findViewById(R.id.app_name);
                h.mark = (TextView) c.findViewById(R.id.tv_custom_mark);
                h.reinstallMark = (TextView) c.findViewById(R.id.tv_reinstalled);
                h.check = (ImageView) c.findViewById(R.id.check_mark);
                h.fav = (TextView) c.findViewById(R.id.tv_favorite);
                h.size = (TextView) c.findViewById(R.id.tv_size);
                h.plus = (TextView) c.findViewById(R.id.tv_mark_vpn);
                h.arrow = (TextView) c.findViewById(R.id.tv_arrow);
                c.setTag(h);
            } else {
                h = (ViewHolder) c.getTag();
            }
            final AppInfo a = (AppInfo) MainActivity.this.apps.get(p);
            ThemeColors t = MainActivity.this.currentTheme;
            h.index.setText(String.valueOf(a.absoluteIndex));
            h.index.setTextColor(t.secondary);
            h.index.setBackground(MainActivity.this.roundDrawable(t.indexBackground, 0, MainActivity.this.dp(10.0f)));
            if (a.isVirtual) {
                h.icon.setImageResource(android.R.drawable.ic_menu_compass);
            } else {
                h.icon.setImageDrawable(a.icon);
            }
            h.name.setText(a.label);
            h.name.setTextColor(t.text);
            h.size.setText(a.sizeStr);
            h.size.setTextColor(t.secondary);
            h.fav.setVisibility(a.isFavorite ? 0 : 8);
            h.arrow.setTextColor(t.secondary);
            if (a.mark == null || a.mark.isEmpty()) {
                h.mark.setVisibility(8);
            } else {
                h.mark.setText(a.mark);
                h.mark.setVisibility(0);
            }
            h.reinstallMark.setVisibility(a.isReinstalled ? 0 : 8);
            h.root.setBackground(MainActivity.this.roundDrawable(t.card, t.cardStroke, MainActivity.this.dp(18.0f)));
            if (a.isDetected()) {
                h.check.setVisibility(0);
                h.plus.setVisibility(8);
                h.plus.setOnClickListener(null);
            } else if (MainActivity.this.isShowAll) {
                h.check.setVisibility(8);
                h.plus.setTextColor(t.accent);
                h.plus.setVisibility(0);
                h.plus.setOnClickListener(new View.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$AppAdapter$$ExternalSyntheticLambda0
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.AppAdapter.this.lambda$getView$0(a, view);
                    }
                });
            } else {
                h.check.setVisibility(8);
                h.plus.setVisibility(8);
                h.plus.setOnClickListener(null);
            }
            h.fav.setOnClickListener(new View.OnClickListener() { // from class: com.example.vpnlauncher.MainActivity$AppAdapter$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.AppAdapter.this.lambda$getView$1(a, view);
                }
            });
            return c;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$getView$0(AppInfo a, View v) {
            MainActivity.this.applyMark(a.packageName, "手动收编");
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$getView$1(AppInfo a, View v) {
            MainActivity.this.toggleFavorite(a.packageName);
        }
    }

    /* loaded from: classes2.dex */
    private static class ViewHolder {
        TextView arrow;
        ImageView check;
        TextView fav;
        ImageView icon;
        TextView index;
        TextView mark;
        TextView name;
        TextView plus;
        TextView reinstallMark;
        LinearLayout root;
        TextView size;

        private ViewHolder() {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes2.dex */
    private static class ThemeBackgroundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeColors theme;
        private int drawableAlpha = 255;
        private ColorFilter colorFilter;

        ThemeBackgroundDrawable(ThemeColors theme) {
            this.theme = theme;
        }

        private int withAlpha(int color) {
            int alpha = Color.alpha(color) * drawableAlpha / 255;
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            if (width <= 0 || height <= 0) return;

            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);

            // 主背景渐变。
            paint.setShader(new LinearGradient(
                    0, 0, width, height,
                    theme.backgroundStart,
                    theme.backgroundEnd,
                    Shader.TileMode.CLAMP
            ));
            paint.setColor(Color.WHITE);
            paint.setAlpha(drawableAlpha);
            paint.setColorFilter(colorFilter);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);

            // 顶部柔和斜向光带。
            paint.setColor(withAlpha(
                    theme.dark ? 0x1C91B8FF : 0x35FFFFFF));
            paint.setColorFilter(colorFilter);

            Path topRibbon = new Path();
            topRibbon.moveTo(width * 0.34f, 0);
            topRibbon.cubicTo(
                    width * 0.50f, height * 0.02f,
                    width * 0.57f, height * 0.09f,
                    width * 0.49f, height * 0.17f
            );
            topRibbon.cubicTo(
                    width * 0.43f, height * 0.24f,
                    width * 0.48f, height * 0.30f,
                    width * 0.66f, height * 0.36f
            );
            topRibbon.cubicTo(
                    width * 0.50f, height * 0.31f,
                    width * 0.34f, height * 0.24f,
                    width * 0.37f, height * 0.15f
            );
            topRibbon.cubicTo(
                    width * 0.40f, height * 0.08f,
                    width * 0.28f, height * 0.04f,
                    width * 0.34f, 0
            );
            topRibbon.close();
            canvas.drawPath(topRibbon, paint);

            // 右上方弧形光影。
            paint.setColor(withAlpha(
                    theme.dark ? 0x174A74C7 : 0x24FFFFFF));

            Path upperRight = new Path();
            upperRight.moveTo(width * 0.67f, 0);
            upperRight.cubicTo(
                    width * 0.91f, height * 0.02f,
                    width * 0.98f, height * 0.11f,
                    width * 0.82f, height * 0.20f
            );
            upperRight.cubicTo(
                    width * 0.72f, height * 0.26f,
                    width * 0.70f, height * 0.31f,
                    width * 0.82f, height * 0.39f
            );
            upperRight.cubicTo(
                    width * 0.63f, height * 0.31f,
                    width * 0.56f, height * 0.22f,
                    width * 0.67f, height * 0.13f
            );
            upperRight.cubicTo(
                    width * 0.76f, height * 0.07f,
                    width * 0.72f, height * 0.03f,
                    width * 0.67f, 0
            );
            upperRight.close();
            canvas.drawPath(upperRight, paint);

            // 底部斜向装饰。
            paint.setColor(withAlpha(
                    theme.dark ? 0x203C68B7 : 0x25FFFFFF));

            Path bottomRibbon = new Path();
            bottomRibbon.moveTo(width * 0.58f, height);
            bottomRibbon.cubicTo(
                    width * 0.72f, height * 0.91f,
                    width * 0.85f, height * 0.91f,
                    width, height * 0.80f
            );
            bottomRibbon.lineTo(width, height);
            bottomRibbon.close();
            canvas.drawPath(bottomRibbon, paint);

            // 左下角淡淡的弧形装饰。
            paint.setColor(withAlpha(
                    theme.dark ? 0x164C77D2 : 0x20FFFFFF));

            Path lowerLeft = new Path();
            lowerLeft.moveTo(0, height * 0.80f);
            lowerLeft.cubicTo(
                    width * 0.12f, height * 0.73f,
                    width * 0.19f, height * 0.78f,
                    width * 0.23f, height
            );
            lowerLeft.lineTo(0, height);
            lowerLeft.close();
            canvas.drawPath(lowerLeft, paint);

            canvas.restoreToCount(save);
            paint.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class ThemeColors {
        final int backgroundStart, backgroundEnd, card, cardStroke, text, secondary, accent, searchBackground, indexBackground;
        final boolean dark;

        ThemeColors(int s, int e, int c, int st, int t, int sc, int a, int se, int i, boolean d) {
            backgroundStart = s; backgroundEnd = e; card = c; cardStroke = st;
            text = t; secondary = sc; accent = a; searchBackground = se;
            indexBackground = i; dark = d;
        }

        // 颜色已调整：背景稍微加深，卡片不再纯白（更柔和）
        static final ThemeColors[] THEMES = {
            // 蓝（默认）
            new ThemeColors(0xFFA8C8F0, 0xFF7BA8E0, 0xF5FFFFFF, 0xFF7AA3D9, 0xFF172B4D, 0xFF5C6D87, 0xFF2878E8, 0xF8FFFFFF, 0xFFE0ECFF, false),
            // 深色
            new ThemeColors(0xFF141A2E, 0xFF0A0E17, 0xFF1E263D, 0xFF405477, 0xFFF1F5FF, 0xFF8A9BB8, 0xFF72A7FF, 0xFF24314B, 0xFF2D3C56, true),
            // 绿
            new ThemeColors(0xFFC8E6CC, 0xFFB0D6B5, 0xF5FFFFFF, 0xFF85B98F, 0xFF2D4532, 0xFF5D7562, 0xFF548B5B, 0xF8FFFFFF, 0xFFD4E8D0, false),
            // 紫
            new ThemeColors(0xFFD4C4F0, 0xFFBCA8E8, 0xF5FFFFFF, 0xFFA48DD4, 0xFF3A2D52, 0xFF736789, 0xFF8062C9, 0xF8FFFFFF, 0xFFE4D6FF, false),
            // 橙
            new ThemeColors(0xFFF5DFC4, 0xFFE8C89A, 0xF5FFFFFF, 0xFFD4A86E, 0xFF513D29, 0xFF806B53, 0xFFC17A32, 0xF8FFFFFF, 0xFFFFE4C4, false),
            // 青
            new ThemeColors(0xFFC8EBE4, 0xFFA8D9CF, 0xF5FFFFFF, 0xFF75B8A9, 0xFF234B41, 0xFF5E857C, 0xFF278F82, 0xF8FFFFFF, 0xFFCAE6DF, false)
        };
    }
}
