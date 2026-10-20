package de.steffen.musikmathe;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_PICK = 10, WEB_PERMISSION = 11, DOWNLOAD_PERMISSION = 12, PICK_DOWNLOAD_FOLDER = 20;
    private int ink(){return getColor(R.color.browser_ink);}
    static final class Tab {
        volatile String url;
        String title;
        boolean desktop;
        WebView view;
        Bundle state;
        final VideoCatalog videos=new VideoCatalog();
        volatile int videoPage;
        int historyPage=-1;
        boolean protectedMedia;
        final java.util.concurrent.atomic.AtomicInteger blockedRequests=new java.util.concurrent.atomic.AtomicInteger();
        androidx.webkit.ScriptHandler adBlockScript;
        Tab(String url, String title, boolean desktop) { this.url = url; this.title = title; this.desktop = desktop; }
    }
    private final List<Tab> tabs = new ArrayList<>();
    private int selected;
    private BrowserStore store;
    private PrivateAccess privateAccess;
    private LinkPreloader linkPreloader;
    private boolean browserResumed;
    private volatile boolean adBlockingEnabled;
    private volatile java.util.Set<String> adBlockExceptions=Collections.emptySet();
    private boolean adBlockUpdating;
    private PasswordManager passwords;
    private LinearLayout shell, top, bottom;
    private FrameLayout root, pages;
    private EditText address;
    private AddressSuggestions addressSuggestions;
    private BrowserMenu browserMenu;
    private TextView siteState;
    private ProgressBar progress;
    private BrowserIcon back, forward, tabButton, videoButton;
    private View customView;
    private boolean browserFullscreen;
    private WebChromeClient.CustomViewCallback customCallback;
    private ValueCallback<Uri[]> upload;
    private Runnable permissionResult, permissionCancel;
    private Tab permissionTab;
    private String permissionOrigin;
    private int permissionGeneration;
    private boolean runtimePermissionPending;
    private AlertDialog permissionDialog;
    private PermissionRequest websitePermissionRequest;
    private String[] pendingDownload;
    private boolean destroyed;
    private final java.util.concurrent.ExecutorService videoWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private YouTubeDownloadResolver videoResolver;
    private AlertDialog videoSearch;
    private VideoDownloadsDialog videoDownloads;
    private BrowserCast browserCast;
    private boolean closingPrivate;
    private boolean discardPrivateTabs;
    private VideoCatalog.Item pendingVideo;
    private boolean pendingVideoOriginal;
    private long pendingVideoClip;
    private String pendingVideoTitle;
    private boolean isPrivateMode() { return this instanceof PrivateActivity; }

    @Override public void onCreate(Bundle saved) {
        BrowserAppearance.sync(this);
        super.onCreate(saved);
        if(isPrivateMode()&&Build.VERSION.SDK_INT<28){finish();return;}
        if (isPrivateMode()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){goBack();}
        });
        if(isPrivateMode()){
            privateAccess=new PrivateAccess(this,()->{if(store==null)initializeBrowser(null);else{if(activeView()!=null)activeView().onResume();refreshHomeTabs();applyAppearance();}},this::endPrivate);
            privateAccess.show();return;
        }
        initializeBrowser(saved);
    }
    private boolean privateLocked(){return privateAccess!=null&&privateAccess.locked();}
    private void initializeBrowser(Bundle saved){
        store = new BrowserStore(this, isPrivateMode());
        if(!isPrivateMode())linkPreloader=new LinkPreloader(this,store::linkPreloadingEnabled,url->adBlockingEnabled&&active()!=null&&!AdBlocker.excepted(active().url,adBlockExceptions)&&AdBlocker.shouldBlock(url,false,true));
        browserFullscreen = saved != null && saved.getBoolean("browser_fullscreen", false);
        adBlockingEnabled = store.adBlockingEnabled();
        adBlockExceptions = Collections.unmodifiableSet(store.adBlockExceptions());
        AdBlockResources.preload(this);
        if(!isPrivateMode())passwords=new PasswordManager(this,this::activeView);
        buildInterface();
        if (!isPrivateMode()) PrivateSession.cleanIfStopped(this);
        for (BrowserStore.Entry row : store.read("tabs")) tabs.add(new Tab(row.url, row.title, row.desktop));
        if (tabs.isEmpty()) tabs.add(new Tab(Address.HOME, "Neuer Tab", false));
        selected = Math.max(0, Math.min(store.selected(), tabs.size() - 1));
        switchTab(selected);
        if (saved == null) acceptIntent(getIntent());
        if(isPrivateMode()&&getIntent().getBooleanExtra("new_private_tab",false)&&tabs.size()>0&&!Address.HOME.equals(active().url))newTab(Address.HOME,true);
        getIntent().removeExtra("new_private_tab");
        applyFullscreenUi();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(ink()); return view;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private BrowserIcon icon(LinearLayout row, String kind, String label, Runnable action) {
        return icon(row, kind, label, action, 48);
    }
    private BrowserIcon icon(LinearLayout row, String kind, String label, Runnable action, int height) {
        BrowserIcon icon = new BrowserIcon(this, kind, label, height < 48);
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(height))); icon.setOnClickListener(v -> action.run()); return icon;
    }
    private void buildInterface() {
        root = new FrameLayout(this); root.setBackgroundColor(getColor(isPrivateMode()?R.color.browser_private_paper:R.color.browser_paper));
        shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.ime() | (browserFullscreen || customView != null ? 0 : WindowInsets.Type.displayCutout() | WindowInsets.Type.systemBars()));
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
            });
        }
        top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(6), dp(2), dp(6), dp(2));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout omnibox = new LinearLayout(this); omnibox.setGravity(Gravity.CENTER_VERTICAL); omnibox.setBackground(rounded(getColor(R.color.browser_field), 12));
        top.addView(omnibox, new LinearLayout.LayoutParams(0, dp(24), 1));
        siteState = text("⊕", 18); siteState.setIncludeFontPadding(false); siteState.setGravity(Gravity.CENTER); siteState.setContentDescription("Informationen zur Seite");
        omnibox.addView(siteState, new LinearLayout.LayoutParams(dp(44), -1)); siteState.setOnClickListener(v -> siteInfo());
        address = new EditText(this); address.setSingleLine(true); address.setSelectAllOnFocus(true); address.setTextSize(16); address.setTextColor(ink());
        address.setHint("Adresse oder Suche"); address.setHintTextColor(getColor(R.color.browser_hint)); address.setContentDescription("Adresse oder Suchbegriff"); address.setBackgroundColor(Color.TRANSPARENT);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI | (isPrivateMode() && Build.VERSION.SDK_INT >= 26 ? EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING : 0));
        address.setMinimumHeight(0); address.setMinHeight(0); address.setIncludeFontPadding(false); address.setGravity(Gravity.CENTER_VERTICAL);
        address.setPadding(0,0,dp(8),0); omnibox.addView(address, new LinearLayout.LayoutParams(0,-1,1));
        addressSuggestions=new AddressSuggestions(address,omnibox,store,this::navigate);
        address.setOnKeyListener((v,key,event) -> {
            if(addressSuggestions.arrow(key,event))return true;
            if (key != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !addressSuggestions.completeSelection()) navigateTyped(address.getText().toString());
            return true;
        });
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                if(!addressSuggestions.completeSelection())navigateTyped(address.getText().toString()); return true;
            } return false;
        });
        BrowserIcon reload = icon(top, "reload", "Neu laden oder Laden stoppen", () -> { WebView w = activeView(); if(w != null) { if(w.getProgress() < 100)w.stopLoading();else w.reload(); } }, 24);
        reload.setOnLongClickListener(v -> { WebView w=activeView();if(w!=null){w.clearCache(false);w.reload();}return true; });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.browser_accent)));
        shell.addView(progress, new LinearLayout.LayoutParams(-1, dp(2))); progress.setVisibility(View.INVISIBLE);
        pages = new FrameLayout(this); pages.setBackgroundColor(getColor(R.color.browser_page)); shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(bottom, new LinearLayout.LayoutParams(-1, dp(25)));
        back = icon(bottom, "back", "Zurück", this::goBack, 25);
        forward = icon(bottom, "forward", "Vorwärts", () -> { if(activeView()!=null && activeView().canGoForward())activeView().goForward(); }, 25);
        icon(bottom, "home", "Startseite", () -> navigate(Address.HOME), 25);
        videoButton=icon(bottom,"video","Videos auf dieser Seite",this::showVideos,25);
        View space = new View(this); bottom.addView(space, new LinearLayout.LayoutParams(0,1,1));
        tabButton = icon(bottom, "tabs", "Tabs anzeigen", this::showTabs, 25);
        tabButton.setOnLongClickListener(v -> { newTab(Address.HOME, true); return true; });
        icon(bottom, "menu", "Menü", this::menu, 25);
        FreeSpaceGesture.attachBlankView(top,()->setBrowserFullscreen(!browserFullscreen));
        FreeSpaceGesture.attachBlankView(bottom,()->setBrowserFullscreen(!browserFullscreen));
    }
    private Tab active() { return tabs.isEmpty() ? null : tabs.get(selected); }
    private WebView activeView() { Tab tab=active(); return tab==null ? null : tab.view; }
    private void hideKeyboard() {
        addressSuggestions.dismiss();address.clearFocus(); pages.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
    }
    private void navigateTyped(String input) {
        String url = Address.resolve(input, store.engine());
        if(url==null) { toast("Bitte eine gültige http- oder https-Adresse eingeben."); return; }
        navigate(url);
    }
    private void navigate(String url) {
        if (!Address.isWeb(url) && !Address.HOME.equals(url)) return;
        hideKeyboard(); WebView web=activeView(); if(web!=null) { if(linkPreloader!=null)linkPreloader.navigation(url);web.loadUrl(url); active().url=url; updateToolbar(); persist(); }
    }
    private void newTab(String url, boolean focus) {
        if(tabs.size() >= BrowserStore.MAX_TABS) { toast("Bitte zuerst einen Tab schließen (maximal 20)."); return; }
        tabs.add(new Tab(url, Address.HOME.equals(url)?"Neuer Tab":url, false));
        if(focus) switchTab(tabs.size()-1); else updateToolbar(); persist();
    }
    private void switchTab(int index) {
        if(index<0 || index>=tabs.size()) return;
        if(linkPreloader!=null)linkPreloader.clear();
        addressSuggestions.dismiss();cancelPermission(); cancelUpload();if(passwords!=null)passwords.tabChanged();
        WebView old=activeView(); if(old!=null) { old.onPause(); pages.removeView(old); }
        hideFullscreen(); selected=index; Tab tab=active();
        if(tab.view==null) tab.view=createWebView(tab);
        if(tab.view.getParent() instanceof ViewGroup)((ViewGroup)tab.view.getParent()).removeView(tab.view);
        pages.removeAllViews(); pages.addView(tab.view,new FrameLayout.LayoutParams(-1,-1)); tab.view.onResume();
        updateHome(tab);
        refreshPreloading();
        updateToolbar(); persist();
    }
    private void closeTab(int index) {
        if(index<0 || index>=tabs.size()) return;
        if(isPrivateMode() && tabs.size()==1 && VideoDownloadService.busy()){endPrivate();return;}
        cancelPermission(); cancelUpload(); hideFullscreen();if(passwords!=null)passwords.tabChanged();
        Tab removed=tabs.remove(index); clearAdBlockScript(removed); if(removed.view!=null) { pages.removeView(removed.view); removed.view.stopLoading(); removed.view.destroy(); }
        if(tabs.isEmpty() && isPrivateMode()) { endPrivate(); return; }
        if(tabs.isEmpty()) tabs.add(new Tab(Address.HOME,"Neuer Tab",false));
        if(index<selected)selected--; selected=Math.min(selected,tabs.size()-1); switchTab(selected);
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private WebView createWebView(Tab tab) {
        WebView web=new WebView(this);
        BrowserWebAppearance.apply(web,BrowserAppearance.night(this));
        web.setSaveEnabled(false);
        if(Build.VERSION.SDK_INT>=26)web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (isPrivateMode()) {
            web.setSaveEnabled(false);
            if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        WebSettings settings=web.getSettings();
        settings.setSaveFormData(false);settings.setSavePassword(false);
        if (isPrivateMode()) { settings.setCacheMode(WebSettings.LOAD_NO_CACHE); } settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true); settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if(Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
        applyDesktop(web, tab.desktop);
        if(passwords!=null)passwords.attach(web);
        YouTubeBridge.attach(this,web,data -> acceptYouTube(tab,data));
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && WikiTop.URL.equals(request.getUrl().toString())) return WikiTop.response();
                if(adBlockingEnabled&&!request.isForMainFrame()&&!AdBlocker.excepted(tab.url,adBlockExceptions))AdBlockResources.ensureLoaded(MainActivity.this);
                if (AdBlocker.shouldBlock(request.getUrl().toString(), request.isForMainFrame(), adBlockingEnabled&&!AdBlocker.excepted(tab.url,adBlockExceptions))) {
                    tab.blockedRequests.incrementAndGet();
                    return BlockedResource.response();
                }
                if ("GET".equals(request.getMethod())) tab.videos.observe(request.getUrl().toString(),request.getRequestHeaders());
                if ("GET".equals(request.getMethod())) recordVideo(tab,tab.videoPage,request.getUrl().toString(),"",request.getRequestHeaders());
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if(!request.isForMainFrame())return false;
                return routeLink(tab,request.getUrl().toString(),request.hasGesture());
            }
            @SuppressWarnings("deprecation") @Override public boolean shouldOverrideUrlLoading(WebView view,String url) { return routeLink(tab,url,false); }
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){
                boolean changed=!java.util.Objects.equals(tab.url,url);
                String old=YouTubeSources.videoId(tab.url),next=YouTubeSources.videoId(url);
                if(!next.isEmpty()&&!next.equals(old))tab.videos.clear();
                tab.url=url;if(active()==tab){updateToolbar();if(changed&&linkPreloader!=null){linkPreloader.navigation(url);root.postDelayed(()->{if(!destroyed)refreshPreloading();},500);}}
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon) {
                if(active()==tab&&linkPreloader!=null)linkPreloader.navigation(url);
                if(passwords!=null)passwords.pageStarted(view);
                tab.videoPage++;tab.protectedMedia=false;tab.videos.clear();tab.url=url; cancelPermissionFor(tab); if(active()==tab)updateToolbar();
                tab.blockedRequests.set(0);
                if(adBlockingEnabled)view.evaluateJavascript(AdBlockResources.cosmeticScript(MainActivity.this,adBlockExceptions),null);
            }
            @Override public void onPageFinished(WebView view,String url) {
                tab.url=url; tab.title=view.getTitle()==null?url:view.getTitle();
                if(Address.isWeb(url)&&tab.historyPage!=tab.videoPage){store.visit(url,tab.title);tab.historyPage=tab.videoPage;}
                refreshHomeTabs();if(active()==tab)updateToolbar(); persist();
                BrowserWebAppearance.pageFinished(view,BrowserAppearance.night(MainActivity.this));
                if(passwords!=null)passwords.pageFinished(view);
                if(adBlockingEnabled)view.evaluateJavascript(AdBlockResources.cosmeticScript(MainActivity.this,adBlockExceptions),null);
                view.evaluateJavascript(VideoCatalog.HOOKS,null);
                if(YouTubeSources.originAllowed(url))view.evaluateJavascript(YouTubeSources.script(MainActivity.this),null);
                int page=tab.videoPage;view.postDelayed(() -> {if(!destroyed && tab.videoPage==page && tab.view==view)scanVideos(tab,()->{});},1000);
                if(active()==tab)refreshPreloading();
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame() && active()==tab)toast("Seite konnte nicht geladen werden. Internetverbindung und Adresse prüfen.");
            }
            @Override public boolean onRenderProcessGone(WebView view,android.webkit.RenderProcessGoneDetail detail) {
                if(active()==tab&&linkPreloader!=null)linkPreloader.pause();
                pages.removeView(view); clearAdBlockScript(tab); view.destroy(); tab.view=null; tab.state=null;
                if(active()==tab) { toast("Die Seite wurde beendet. Sie wird neu geladen."); pages.post(() -> { if(!destroyed && tabs.contains(tab))switchTab(tabs.indexOf(tab)); }); }
                return true;
            }
            // The platform rejects invalid TLS certificates and unsafe browsing hits; no bypass.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView v,String u,String m,android.webkit.JsResult r){if(privateLocked()){r.cancel();return true;}return false;}
            @Override public boolean onJsConfirm(WebView v,String u,String m,android.webkit.JsResult r){if(privateLocked()){r.cancel();return true;}return false;}
            @Override public boolean onJsPrompt(WebView v,String u,String m,String d,android.webkit.JsPromptResult r){if(privateLocked()){r.cancel();return true;}return false;}
            @Override public void onProgressChanged(WebView view,int value) { if(active()==tab) { if(value==100)view.post(()->{if(!destroyed&&active()==tab)refreshPreloading();});progress.setProgress(value); progress.setVisibility(browserFullscreen?View.GONE:value<100?View.VISIBLE:View.INVISIBLE); back.setEnabled(view.canGoBack()); forward.setEnabled(view.canGoForward()); } }
            @Override public void onReceivedTitle(WebView view,String title) { tab.title=title; if(active()==tab)updateToolbar(); }
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,android.os.Message message) {
                if(privateLocked() || !userGesture || active()!=tab || tabs.size()>=BrowserStore.MAX_TABS)return false;
                newTab("about:blank",true); WebView child=activeView();
                ((WebView.WebViewTransport)message.obj).setWebView(child); message.sendToTarget(); return true;
            }
            @Override public void onCloseWindow(WebView window) { if(tabs.contains(tab))closeTab(tabs.indexOf(tab)); }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(privateLocked()||active()!=tab)return false; cancelUpload(); upload=callback;
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE);
                ArrayList<String> types=new ArrayList<>(); for(String type:params.getAcceptTypes())if(type!=null && type.contains("/"))types.add(type);
                pick.setType(types.size()==1?types.get(0):"*/*"); if(types.size()>1)pick.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(pick,FILE_PICK); } catch(ActivityNotFoundException e) { cancelUpload(); toast("Keine Dateiauswahl verfügbar."); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> requestWebsitePermission(tab,request)); }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) { runOnUiThread(()->{if(websitePermissionRequest==request)cancelPermission();}); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback) { requestLocation(tab,origin,callback); }
            @Override public void onGeolocationPermissionsHidePrompt() { cancelPermissionFor(tab); }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(privateLocked() || active()!=tab || customView!=null) { callback.onCustomViewHidden(); return; }
                customView=view; customCallback=callback; shell.setVisibility(View.GONE); root.addView(view,new FrameLayout.LayoutParams(-1,-1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                applyFullscreenUi();
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
        });
        web.setDownloadListener((url,userAgent,disposition,mime,length) -> { if(privateLocked()||active()!=tab)return;if(Address.isWeb(url))confirmDownload(url,userAgent,disposition,mime);else toast("Dieser Downloadtyp wird noch nicht unterstützt."); });
        web.setOnLongClickListener(v -> {
            WebView.HitTestResult hit=web.getHitTestResult(); if(hit==null || hit.getExtra()==null)return false;
            String target=hit.getExtra(); if(!Address.isWeb(target))return false;
            new AlertDialog.Builder(this).setTitle(target).setItems(new String[]{"In neuem Tab öffnen","Link kopieren","Link teilen"},(dialog,which) -> { if(which==0)newTab(target,true);else if(which==1)copy(target);else share(target); }).show(); return true;
        });
        FreeSpaceGesture.attach(web,()->!destroyed&&activeView()==web&&customView==null,()->tab.videoPage,()->setBrowserFullscreen(!browserFullscreen),event->{if(passwords!=null)passwords.touch(web,event);});
        configureAdBlocking(tab,web);
        if((tab.state==null || web.restoreState(tab.state)==null) && !"about:blank".equals(tab.url))web.loadUrl(Address.isWeb(tab.url)?tab.url:Address.HOME);
        tab.state=null; return web;
    }
    private void applyDesktop(WebView view,boolean desktop) {
        String ua=WebSettings.getDefaultUserAgent(this);
        if(desktop)ua=ua.replace("; wv","").replace("Android","X11; Linux x86_64").replace("Mobile ","");
        view.getSettings().setUserAgentString(ua); view.getSettings().setUseWideViewPort(true);
    }
    private boolean routeLink(Tab tab,String url,boolean gesture) {
        if(Address.isWeb(url) || Address.HOME.equals(url) || "about:blank".equals(url)){if(active()==tab&&linkPreloader!=null)linkPreloader.navigation(url);return false;}
        if(!gesture || active()!=tab)return true;
        Uri uri=Uri.parse(url); String scheme=uri.getScheme();
        // External app links are only offered after a user gesture and explicit confirmation.
        if("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme) || "sms".equalsIgnoreCase(scheme) || "geo".equalsIgnoreCase(scheme) || "market".equalsIgnoreCase(scheme)) {
            new AlertDialog.Builder(this).setMessage("Link in einer anderen App öffnen?\n"+url).setNegativeButton("Abbrechen",null).setPositiveButton("Öffnen",(d,w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)); } catch(ActivityNotFoundException e) { toast("Keine passende App installiert."); }
            }).show();
        } else if("intent".equalsIgnoreCase(scheme)) {
            try { Intent intent=Intent.parseUri(url,Intent.URI_INTENT_SCHEME); String fallback=intent.getStringExtra("browser_fallback_url"); if(Address.isWeb(fallback))navigate(fallback);else toast("Bitte die Web-Version dieses Angebots verwenden."); }
            catch(Exception e) { toast("Dieser Link kann nicht geöffnet werden."); }
        } else toast("Dieser Linktyp wird nicht unterstützt.");
        return true;
    }
    private void updateToolbar() {
        Tab tab=active(); if(tab==null)return;
        if(!address.hasFocus()) address.setText(Address.display(tab.url));
        boolean secure=tab.url!=null && tab.url.startsWith("https://") && !WikiTop.URL.equals(tab.url);
        siteState.setText(isPrivateMode()?"P":Address.HOME.equals(tab.url)?"⊕":secure?"≡":"!");
        siteState.setContentDescription(isPrivateMode()?"Privater Tab. Seiteninformationen":secure?"HTTPS-Verbindung. Seiteninformationen":"Seiteninformationen");
        address.setHint(isPrivateMode()?"Privat · Adresse oder Suche":"Adresse oder Suche");
        back.setEnabled(tab.view!=null && tab.view.canGoBack()); forward.setEnabled(tab.view!=null && tab.view.canGoForward());
        videoButton.count=tab.videos.list().size();videoButton.setContentDescription("Videos auf dieser Seite · "+videoButton.count+" erkannt");videoButton.invalidate();
        tabButton.count=tabs.size(); tabButton.setContentDescription(tabs.size()+" Tabs. Tabs anzeigen"); tabButton.invalidate();
        int value=tab.view==null?100:tab.view.getProgress(); progress.setProgress(value); progress.setVisibility(browserFullscreen?View.GONE:value<100?View.VISIBLE:View.INVISIBLE);
    }
    private void menu() {
        hideKeyboard();if(browserMenu!=null)browserMenu.dismiss();
        Tab tab=active();if(tab==null)return;
        WebView web=tab.view;boolean webpage=Address.isWeb(tab.url),bookmarked=store.bookmarked(tab.url);
        browserMenu=new BrowserMenu(this,bottom.getChildAt(bottom.getChildCount()-1),this::menuAction)
            .quick(21,"forward","Vorwärts",web!=null&&web.canGoForward())
            .quick(1,bookmarked?"star-filled":"star",bookmarked?"Lesezeichen entfernen":"Lesezeichen hinzufügen",webpage)
            .quick(5,"share","Seite teilen",webpage)
            .quick(22,web!=null&&web.getProgress()<100?"stop":"reload",web!=null&&web.getProgress()<100?"Laden stoppen":"Neu laden",web!=null)
            .item(0,"plus","Neuer Tab")
            .item(12,"private","Neuer privater Tab")
            .item(13,"switch-tabs",isPrivateMode()?"Zu normalen Tabs":"Zu privaten Tabs");
        if(isPrivateMode())browserMenu.item(25,"key","Privaten Bereich sperren").item(14,"close","Alle privaten Tabs schließen");
        browserMenu.divider().item(2,"bookmarks","Lesezeichen").item(3,"history","Verlauf").item(4,"download","Downloads");
        if(passwords!=null)browserMenu.item(16,"key","Passwörter");
        browserMenu.item(15,"video","Videos erkennen und speichern");
        if(!isPrivateMode())browserMenu.item(24,"cast","Auf Fernseher übertragen");
        browserMenu.divider().item(7,"find","Auf Seite suchen").item(6,"copy","Link kopieren")
            .checked(8,"desktop","Desktopwebsite",tab.desktop).item(20,"fullscreen","Vollbild")
            .divider().checked(23,"night","Nachtmodus",BrowserAppearance.night(this))
            .status(18,"shield","Werbeblocker",adBlockingEnabled?"Ein":"Aus").item(19,"vpn","VPN");
        browserMenu.show();
    }
    private void menuAction(int action) {
        Tab tab=active();if(tab==null)return;
        switch(action) {
                case 0: newTab(Address.HOME,true); break;
                case 1: if(Address.isWeb(tab.url))toast(store.toggleBookmark(tab.url,tab.title)?"Lesezeichen gespeichert":"Lesezeichen entfernt");else toast("Zuerst eine Webseite öffnen.");break;
                case 2: showLibrary("bookmarks","Lesezeichen");break;
                case 3: showLibrary("history","Verlauf");break;
                case 4: try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(ActivityNotFoundException e){toast("Keine Downloadübersicht verfügbar.");}break;
                case 5: if(Address.isWeb(tab.url))share(tab.url);break;
                case 6: if(Address.isWeb(tab.url))copy(tab.url);break;
                case 7: findInPage();break;
                case 8: if(linkPreloader!=null)linkPreloader.clear();tab.desktop=!tab.desktop;applyDesktop(tab.view,tab.desktop);tab.view.reload();persist();break;
                case 9: chooseEngine();break;
                case 10: clearData();break;
                case 11: new AlertDialog.Builder(this).setTitle("BestBrowser · 0.4.19").setMessage("Android-Browser mit Chromium-WebView.\n\nTabs, Lesezeichen, Verlauf, Downloads und Dateiuploads. Werbeblocker mit HaGeZi-Serverliste und EasyList-Werbefiltern (GPLv3), Website-Ausnahmen und Ein-/Ausschalter. Browserdaten bleiben auf diesem Gerät.\n\nPasswortgeschützte private Bereiche speichern Verlauf, Tabs, Website-Daten und Einstellungen getrennt und verschlüsselt. Videos in gewählten Android-Ordnern sind separat zugänglich. Video-Extractor: NewPipeExtractor (GPLv3), Quellcode und Lizenzen: github.com/Steffen2308-ger/MusikMathe.\n\nKeine Chrome-Synchronisierung oder Erweiterungen. DRM- und Anmeldefunktionen hängen von der jeweiligen Website und WebView ab.").setPositiveButton("OK",null).show();break;
                case 12: if (isPrivateMode()) newTab(Address.HOME,true); else openPrivate(true); break;
                case 13: if (isPrivateMode()) openNormal(); else openPrivate(false); break;
                case 14: discardPrivateTabs=true;endPrivate(); break;
                case 25: endPrivate();break;
                case 15: showVideos(); break;
                case 16: if(passwords!=null)passwords.menu(); break;
                case 17: settings(); break;
                case 18: showAdBlockingSettings(); break;
                case 19: showVpn(); break;
                case 20: setBrowserFullscreen(true); break;
                case 23: setNightMode(!BrowserAppearance.night(this));break;
                case 24: showCast();break;
                case 21: if(tab.view!=null&&tab.view.canGoForward())tab.view.goForward();break;
                case 22: if(tab.view!=null){if(tab.view.getProgress()<100)tab.view.stopLoading();else tab.view.reload();}break;
        }
    }
    private void showTabs() {
        hideKeyboard(); LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(8),dp(8),dp(8));
        ScrollView scroll=new ScrollView(this);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle((isPrivateMode()?"Private Tabs · ":"Tabs · ")+tabs.size()).setView(scroll).setPositiveButton("Neuer Tab",(d,w)->newTab(Address.HOME,true)).setNegativeButton("Fertig",null).create();
        for(int i=0;i<tabs.size();i++) {
            final int index=i; Tab tab=tabs.get(i); LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(8),0,dp(8));
            if(i==selected)row.setBackground(rounded(getColor(R.color.browser_field),12));
            TextView label=text((i==selected?"●  ":"")+tab.title+"\n"+(Address.HOME.equals(tab.url)?"Startseite":Address.host(tab.url)),15);label.setMaxLines(3);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(66),1));label.setOnClickListener(v->{dialog.dismiss();switchTab(index);});
            icon(row,"stop","Tab schließen",()->{dialog.dismiss();closeTab(index);if(!closingPrivate)showTabs();});list.addView(row);
        } dialog.show();
    }
    private void showLibrary(String key,String title) {
        List<BrowserStore.Entry> rows=store.read(key);
        if(rows.isEmpty()){toast(title+" ist leer.");return;}
        String[] labels=new String[rows.size()];for(int i=0;i<rows.size();i++)labels[i]=rows.get(i).title+"\n"+Address.host(rows.get(i).url);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->navigate(rows.get(i).url)).setNegativeButton("Schließen",null).create();dialog.show();
        dialog.getListView().setOnItemLongClickListener((parent,view,position,id)->{
            BrowserStore.Entry entry=rows.get(position);
            new AlertDialog.Builder(this).setTitle(entry.title).setItems(new String[]{"In neuem Tab öffnen","Eintrag löschen"},(d,i)->{
                dialog.dismiss();if(i==0)newTab(entry.url,true);else{rows.remove(position);store.write(key,rows);showLibrary(key,title);}
            }).show();return true;
        });
    }
    private void findInPage() {
        WebView web=activeView(); if(web==null)return; EditText input=new EditText(this);input.setSingleLine();input.setHint("Suchtext");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Auf Seite suchen").setView(input).setPositiveButton("Weiter",null).setNeutralButton("Zurück",null).setNegativeButton("Fertig",null).create();
        dialog.setOnDismissListener(d->{web.clearMatches();web.setFindListener(null);});
        dialog.setOnShowListener(d->{
            web.setFindListener((index,count,done)->{if(done)dialog.setTitle(count==0?"Keine Treffer":(index+1)+" / "+count+" Treffer");});
            input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){web.findAllAsync(s.toString());}public void afterTextChanged(android.text.Editable e){}});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->web.findNext(true));dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->web.findNext(false));
        });dialog.show();input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void chooseEngine() {
        String[] keys={"google","duckduckgo","bing"};int checked=0;for(int i=0;i<keys.length;i++)if(keys[i].equals(store.engine()))checked=i;
        new AlertDialog.Builder(this).setTitle("Suchmaschine für die Adressleiste").setSingleChoiceItems(new String[]{"Google","DuckDuckGo","Bing"},checked,(d,i)->{store.engine(keys[i]);d.dismiss();}).setNegativeButton("Abbrechen",null).show();
    }
    private void clearData() {
        boolean[] checked={true,false,false};
        new AlertDialog.Builder(this).setTitle("Browserdaten löschen").setMultiChoiceItems(new String[]{"Verlauf","Cookies & Website-Daten (meldet dich ab)","Zwischengespeicherte Bilder und Dateien"},checked,(d,i,on)->checked[i]=on)
            .setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{
                if(linkPreloader!=null)linkPreloader.clear();
                if(checked[0]) { addressSuggestions.dismiss();store.write("history",Collections.emptyList());for(Tab tab:tabs){tab.state=null;if(tab.view!=null)tab.view.clearHistory();}refreshHomeTabs(); }
                if(checked[1]) { CookieManager.getInstance().removeAllCookies(value->CookieManager.getInstance().flush());android.webkit.WebStorage.getInstance().deleteAllData();GeolocationPermissions.getInstance().clearAll(); }
                if(checked[2] && activeView()!=null)activeView().clearCache(true);
                toast("Ausgewählte Browserdaten gelöscht.");
            }).show();
    }
    private void siteInfo() {
        String url=active().url;String info=WikiTop.URL.equals(url)?"Lokale Wikipedia-Rangliste. Die Daten werden verschlüsselt von Wikimedia Analytics geladen.":Address.HOME.equals(url)?"Lokale Startseite":url.startsWith("https://")?"Diese Adresse nutzt HTTPS. Die Zertifikatsprüfung übernimmt Android WebView.":"Diese Seite verwendet keine verschlüsselte HTTPS-Verbindung.";
        new AlertDialog.Builder(this).setTitle("Seiteninformationen").setMessage(info+"\n\n"+Address.display(url)).setPositiveButton("OK",null).show();
    }
    private void updateHome(Tab tab){
        if(store==null||tab.view==null||!Address.HOME.equals(tab.url)||!Address.HOME.equals(tab.view.getUrl()))return;
        tab.view.evaluateJavascript(HomePage.update(store.frequentSites(),isPrivateMode()&&store.profile==null),null);
    }
    private void refreshHomeTabs(){for(Tab tab:tabs)updateHome(tab);}
    private void confirmDownload(String url,String ua,String disposition,String mime) {
        String name=URLUtil.guessFileName(url,disposition,mime);
        if (isPrivateMode()) name += "\n\nHeruntergeladene Dateien bleiben nach dem Schließen privater Tabs erhalten.";
        new AlertDialog.Builder(this).setTitle("Datei herunterladen?").setMessage(name+"\n\n"+Address.host(url)).setNegativeButton("Abbrechen",null).setPositiveButton("Herunterladen",(d,w)->{
            pendingDownload=new String[]{url,ua,disposition,mime};
            if(Build.VERSION.SDK_INT<=28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},DOWNLOAD_PERMISSION);else download();
        }).show();
    }
    private void download() {
        String[] data=pendingDownload;pendingDownload=null;if(data==null)return;
        try {
            String name=URLUtil.guessFileName(data[0],data[2],data[3]).replaceAll("[\\\\/\\p{Cntrl}]","_");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(data[0]));request.setTitle(name);request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
            if(data[1]!=null)request.addRequestHeader("User-Agent",data[1]);
            String cookie=CookieManager.getInstance().getCookie(data[0]);if(cookie!=null)request.addRequestHeader("Cookie",cookie);
            if(data[3]!=null)request.setMimeType(data[3]);
            ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);toast("Download gestartet. Siehe Menü → Downloads.");
        }catch(Exception e){toast("Download konnte nicht gestartet werden.");}
    }
    private void requestWebsitePermission(Tab tab,PermissionRequest request) {
        if(privateLocked() || runtimePermissionPending || active()!=tab || !"https".equalsIgnoreCase(request.getOrigin().getScheme()) || !origin(tab.url).equals(origin(request.getOrigin().toString()))){request.deny();return;}
        cancelPermission();ArrayList<String> runtime=new ArrayList<>(),resources=new ArrayList<>(),purposes=new ArrayList<>();
        for(String resource:request.getResources()){
            if(resources.contains(resource))continue;
            if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)){runtime.add(Manifest.permission.RECORD_AUDIO);resources.add(resource);purposes.add("das Mikrofon verwenden");}
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)){runtime.add(Manifest.permission.CAMERA);resources.add(resource);purposes.add("die Kamera verwenden");}
            if(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)){tab.protectedMedia=true;resources.add(resource);purposes.add("geschützte Videos und Musik wiedergeben");}
        }
        if(resources.isEmpty()){request.deny();return;}
        permissionTab=tab;permissionOrigin=origin(request.getOrigin().toString());permissionCancel=request::deny;websitePermissionRequest=request;
        int version=permissionGeneration;
        permissionResult=()->{
            ArrayList<String> allowed=new ArrayList<>();
            for(String resource:resources)if(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)
                ||(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
                ||(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED))allowed.add(resource);
            if(allowed.isEmpty())request.deny();else request.grant(allowed.toArray(new String[0]));
        };
        boolean protectedMedia=resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
        String message=Address.host(request.getOrigin().toString())+" möchte:\n\n• "+android.text.TextUtils.join("\n• ",purposes);
        if(protectedMedia)message+="\n\nDafür darf die Website eine Gerätekennung zur Lizenzprüfung verwenden. Die Freigabe gilt nur für diese Website in diesem Tab.";
        permissionDialog=new AlertDialog.Builder(this).setTitle(protectedMedia&&resources.size()==1?"Geschützte Inhalte":"Website-Berechtigung").setMessage(message)
            .setNegativeButton("Ablehnen",(d,w)->cancelPermission()).setOnCancelListener(d->cancelPermission()).setPositiveButton("Erlauben",(d,w)->{
                if(permissionTab!=tab || version!=permissionGeneration)return;
                if(runtime.isEmpty())completeWebsitePermission();else{runtimePermissionPending=true;requestPermissions(runtime.toArray(new String[0]),WEB_PERMISSION);}
            }).show();
    }
    private void requestLocation(Tab tab,String requestedOrigin,GeolocationPermissions.Callback callback) {
        if(privateLocked() || runtimePermissionPending || active()!=tab || !requestedOrigin.startsWith("https://")){callback.invoke(requestedOrigin,false,false);return;}
        cancelPermission();permissionTab=tab;permissionOrigin=origin(requestedOrigin);
        int version=permissionGeneration;
        permissionCancel=()->callback.invoke(requestedOrigin,false,false);
        permissionResult=()->callback.invoke(requestedOrigin,checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED,false);
        permissionDialog=new AlertDialog.Builder(this).setTitle("Standort teilen?").setMessage(Address.host(requestedOrigin)+" möchte deinen Standort verwenden.").setNegativeButton("Ablehnen",(d,w)->cancelPermission()).setOnCancelListener(d->cancelPermission()).setPositiveButton("Erlauben",(d,w)->{
            if(permissionTab==tab && version==permissionGeneration){runtimePermissionPending=true;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},WEB_PERMISSION);}
        }).show();
    }
    private String origin(String url) {
        return VideoCatalog.origin(url);
    }
    private void cancelPermissionFor(Tab tab) { if(permissionTab==tab)cancelPermission(); }
    private void cancelPermission() {
        permissionGeneration++;if(permissionDialog!=null){permissionDialog.dismiss();permissionDialog=null;}Runnable cancel=permissionCancel;permissionCancel=null;permissionResult=null;permissionTab=null;permissionOrigin=null;websitePermissionRequest=null;if(cancel!=null)cancel.run();
    }
    private void completeWebsitePermission(){
        Runnable result=permissionResult;
        if(result!=null && active()==permissionTab && origin(active().url).equals(permissionOrigin)){
            permissionResult=null;permissionCancel=null;permissionTab=null;permissionOrigin=null;websitePermissionRequest=null;permissionDialog=null;result.run();
        }else cancelPermission();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==DOWNLOAD_PERMISSION){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)download();else{pendingDownload=null;toast("Download benötigt Zugriff auf den Downloadordner.");}}
        if(code==WEB_PERMISSION){
            runtimePermissionPending=false;
            completeWebsitePermission();
        }
    }
    private void cancelUpload(){ValueCallback<Uri[]> callback=upload;upload=null;if(callback!=null)callback.onReceiveValue(null);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(privateLocked()){privateAccess.defer(()->handleActivityResult(request,result,data));return;}
        handleActivityResult(request,result,data);
    }
    private void handleActivityResult(int request,int result,Intent data){
        if(request==PICK_DOWNLOAD_FOLDER){
            VideoCatalog.Item item=pendingVideo;pendingVideo=null;
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            try{
                DownloadFolder.remember(this,data.getData(),data.getFlags());
                if(item!=null)saveVideoInFolder(item,pendingVideoOriginal,pendingVideoClip,pendingVideoTitle);
                else toast("Video-Downloadordner gespeichert.");
            }catch(Exception error){toast("Der Ordner konnte nicht dauerhaft freigegeben werden. Bitte erneut wählen.");}
        }
        if(request==FILE_PICK){
            ValueCallback<Uri[]> callback=upload;upload=null;if(callback==null)return;
            ArrayList<Uri> selectedFiles=new ArrayList<>();
            if(result==RESULT_OK && data!=null){
                ClipData clips=data.getClipData();
                if(clips!=null)for(int i=0;i<clips.getItemCount();i++){Uri uri=clips.getItemAt(i).getUri();if(uri!=null && "content".equals(uri.getScheme()))selectedFiles.add(uri);}
                else if(data.getData()!=null && "content".equals(data.getData().getScheme()))selectedFiles.add(data.getData());
            }
            callback.onReceiveValue(selectedFiles.isEmpty()?null:selectedFiles.toArray(new Uri[0]));
        }
    }
    private void hideFullscreen(){
        if(customView==null)return;root.removeView(customView);customView=null;shell.setVisibility(View.VISIBLE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreenUi();
        WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();
    }
    private void setBrowserFullscreen(boolean enabled){
        if(browserFullscreen==enabled)return;
        if(enabled)hideKeyboard();browserFullscreen=enabled;applyFullscreenUi();
    }
    private void applyFullscreenUi(){
        if(root==null)return;
        top.setVisibility(browserFullscreen?View.GONE:View.VISIBLE);bottom.setVisibility(browserFullscreen?View.GONE:View.VISIBLE);
        progress.setVisibility(browserFullscreen?View.GONE:progress.getProgress()<100?View.VISIBLE:View.INVISIBLE);
        boolean fullscreen=browserFullscreen||customView!=null;
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams layout=getWindow().getAttributes();
            int cutout=fullscreen?(Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES):WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            if(layout.layoutInDisplayCutoutMode!=cutout){layout.layoutInDisplayCutoutMode=cutout;getWindow().setAttributes(layout);}
        }
        if(Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                int light=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(BrowserAppearance.night(this)?0:light,light);
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(fullscreen)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());
            }
            root.requestApplyInsets();
        }else{
            int flags=BrowserAppearance.night(this)?0:Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(fullscreen)flags|=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&(browserFullscreen||customView!=null))applyFullscreenUi();}
    private void goBack(){if(privateLocked()){endPrivate();return;}if(browserMenu!=null&&browserMenu.dismiss())return;if(addressSuggestions!=null&&addressSuggestions.dismissIfShowing())return;if(customView!=null){hideFullscreen();return;}if(browserFullscreen){setBrowserFullscreen(false);return;}WebView web=activeView();if(web!=null && web.canGoBack())web.goBack();else if(tabs.size()>1)closeTab(selected);else if(isPrivateMode())endPrivate();else moveTaskToBack(true);}
    private void share(String url){try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url),"Seite teilen"));}catch(ActivityNotFoundException e){toast("Keine passende App verfügbar.");}}
    private void copy(String url){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Webadresse",url));toast("Link kopiert.");}
    private void toast(String message){if(!destroyed&&!privateLocked())Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void persist(){
        if(store==null)return;ArrayList<BrowserStore.Entry> rows=new ArrayList<>();for(Tab tab:tabs)rows.add(new BrowserStore.Entry("about:blank".equals(tab.url)?Address.HOME:tab.url,tab.title,0,tab.desktop));store.write("tabs",rows);store.selected(selected);
    }
    private void acceptIntent(Intent intent){if(intent!=null&&VideoDownloadService.SHOW_DOWNLOADS.equals(intent.getAction())){showVideoQueue();return;}if(intent!=null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData()!=null){String url=intent.getData().toString();if(Address.isWeb(url))newTab(url,true);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(store==null)return;if(privateLocked()){privateAccess.defer(()->acceptIntent(intent));return;}acceptIntent(intent);if(isPrivateMode()&&!privateLocked()&&intent.getBooleanExtra("new_private_tab",false))newTab(Address.HOME,true);}
    private void openPrivate(boolean newTab) {
        if (Build.VERSION.SDK_INT < 28) { toast("Private Tabs benötigen Android 9 oder neuer."); return; }
        startActivity(new Intent(this,PrivateActivity.class).putExtra("new_private_tab",newTab).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private void openNormal() {
        if(isPrivateMode()&&!closingPrivate){endPrivate();return;}
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private void endPrivate() {
        if (!isPrivateMode()) return;
        if(VideoDownloadService.busy()){
            new AlertDialog.Builder(this).setMessage("Im privaten Bereich werden Videos gespeichert oder warten auf den Download. Alle abbrechen und die privaten Tabs schließen?").setNegativeButton("Weiter speichern",(d,w)->discardPrivateTabs=false).setOnCancelListener(d->discardPrivateTabs=false).setPositiveButton("Abbrechen und schließen",(d,w)->{VideoDownloadService.cancelAll();endPrivate();}).show();return;
        }
        if(discardPrivateTabs){if(pages!=null)pages.removeAllViews();for(Tab tab:tabs)if(tab.view!=null){clearAdBlockScript(tab);tab.view.stopLoading();tab.view.destroy();}tabs.clear();selected=0;}
        persist();if(store!=null&&store.profile!=null&&!store.profile.flush()){new AlertDialog.Builder(this).setMessage(store.profile.failure).setPositiveButton("OK",null).show();return;}
        closingPrivate=true;openNormal();finish();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("browser_fullscreen",browserFullscreen);persist();super.onSaveInstanceState(state);}
    @Override protected void onPause(){browserResumed=false;if(linkPreloader!=null)linkPreloader.pause();if(browserMenu!=null)browserMenu.dismiss();if(addressSuggestions!=null)addressSuggestions.dismiss();if(videoDownloads!=null)videoDownloads.pause();if(passwords!=null)passwords.pause();persist();if(store!=null)CookieManager.getInstance().flush();if(activeView()!=null)activeView().onPause();super.onPause();if(isPrivateMode()&&!closingPrivate&&!isFinishing()&&!isChangingConfigurations()&&privateAccess!=null){cancelPermission();hideFullscreen();browserFullscreen=false;applyFullscreenUi();if(videoSearch!=null)videoSearch.dismiss();if(videoDownloads!=null)videoDownloads.dismiss();privateAccess.background();}}
    @Override protected void onResume(){super.onResume();browserResumed=true;BrowserAppearance.sync(this);applyAppearance();if(videoDownloads!=null)videoDownloads.resume();if(passwords!=null)passwords.resume();if(store==null)return;refreshHomeTabs();if(activeView()!=null&&!privateLocked())activeView().onResume();refreshPreloading();if(!isPrivateMode()){
        PrivateSession.cleanIfStopped(this);
        root.postDelayed(() -> { if(!destroyed)PrivateSession.cleanIfStopped(this); },1000);
    }}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);if(browserMenu!=null)browserMenu.dismiss();applyAppearance();}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){for(Tab tab:tabs)if(tab!=active() && tab.view!=null){tab.state=new Bundle();tab.view.saveState(tab.state);clearAdBlockScript(tab);tab.view.destroy();tab.view=null;}}
    }
    @Override protected void onDestroy(){
        if(privateAccess!=null)privateAccess.destroy();
        if(linkPreloader!=null)linkPreloader.destroy();
        if(browserCast!=null)browserCast.destroy();
        destroyed=true;if(browserMenu!=null)browserMenu.dismiss();if(addressSuggestions!=null)addressSuggestions.destroy();if(videoDownloads!=null)videoDownloads.dismiss();if(passwords!=null)passwords.destroy();if(videoResolver!=null)videoResolver.cancel();if(videoSearch!=null)videoSearch.dismiss();videoWorker.shutdownNow();cancelPermission();cancelUpload();hideFullscreen();if(pages!=null)pages.removeAllViews();for(Tab tab:tabs){clearAdBlockScript(tab);if(tab.view!=null){tab.view.stopLoading();tab.view.destroy();}}super.onDestroy();
        if (isPrivateMode() && !isChangingConfigurations()) {
            VideoDownloadService.cancelAll();
            if(PrivateProfiles.active!=null)PrivateProfiles.active.close();
            // The normal process archives only after this process has really stopped.
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
    private void recordVideo(Tab tab,int page,String url,String mime,java.util.Map<String,String> headers){
        if(VideoCatalog.classify(url,mime,headers)==null)return;
        java.util.Map<String,String> copy=VideoCatalog.safeHeaders(headers);
        runOnUiThread(()->{if(!destroyed && tabs.contains(tab) && tab.videoPage==page){boolean added=tab.videos.add(url,mime,copy);if(added && active()==tab)updateToolbar();}});
    }
    private void scanVideos(Tab tab,Runnable completed){
        if(tab==null || tab.view==null){completed.run();return;}
        int page=tab.videoPage;String referer=tab.url,userAgent=tab.view.getSettings().getUserAgentString();
        String script="(function(){var a=[],seen={};function add(u,t){if(u&&!seen[u]&&a.length<180){seen[u]=1;a.push({url:u,type:t||''});}}function scan(w,depth){try{var d=w.document;(w.__mmVideos||[]).forEach(function(e){add(e.url,e.type);});d.querySelectorAll('video,video source').forEach(function(v){add(v.currentSrc||v.src,v.type||'video/unknown');});w.performance.getEntriesByType('resource').slice(-400).forEach(function(e){add(e.name,'');});if(depth<2)for(var i=0;i<Math.min(w.frames.length,8);i++)scan(w.frames[i],depth+1);}catch(e){}}scan(window,0);return JSON.stringify(a);})()";
        tab.view.evaluateJavascript(script,value->{
            if(destroyed || !tabs.contains(tab) || page!=tab.videoPage)return;
            try{Object decoded=new org.json.JSONTokener(value).nextValue();org.json.JSONArray rows=new org.json.JSONArray(String.valueOf(decoded));
                java.util.Map<String,String> headers=new java.util.LinkedHashMap<>();if(Address.isWeb(referer))headers.put("Referer",referer);if(userAgent!=null)headers.put("User-Agent",userAgent);
                for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);String url=row.optString("url");java.util.Map<String,String> scoped=new java.util.LinkedHashMap<>(headers);
                    if(Address.isWeb(referer)&&Address.isWeb(url)&&!VideoCatalog.origin(referer).equals(VideoCatalog.origin(url)))scoped.put("Referer",Uri.parse(referer).buildUpon().path("/").clearQuery().fragment(null).build().toString());
                    recordVideo(tab,page,url,row.optString("type"),scoped);}
            }catch(Exception ignored){}completed.run();
        });
    }
    private void showVideos(){
        Tab tab=active();if(tab==null||videoResolver!=null)return;
        scanYouTube(tab,()->scanVideos(tab,()->{
            if(active()!=tab||destroyed)return;
            String id=YouTubeSources.videoId(tab.url);
            if(!id.isEmpty()&&!tab.videos.hasResolved(id))resolveYouTube(tab,id);
            else showVideoList(tab,"");
        }));
    }
    private void resolveYouTube(Tab tab,String id){
        YouTubeDownloadResolver resolver=new YouTubeDownloadResolver();videoResolver=resolver;int page=tab.videoPage;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("YouTube herunterladen").setMessage("Downloadquelle wird gesucht und geprüft …").setNegativeButton("Abbrechen",(d,w)->{resolver.cancel();if(videoResolver==resolver)videoResolver=null;}).create();
        videoSearch=dialog;dialog.setOnCancelListener(d->{resolver.cancel();if(videoResolver==resolver)videoResolver=null;});dialog.show();
        videoWorker.execute(()->{
            YouTubeDownloadResolver.Result result=null;String error="";
            try{result=resolver.resolve("https://www.youtube.com/watch?v="+id);}catch(Exception failure){error=YouTubeDownloadResolver.errorMessage(failure);}
            final YouTubeDownloadResolver.Result found=result;final String message=error;
            runOnUiThread(()->{
                if(videoResolver!=resolver)return;videoResolver=null;videoSearch=null;dialog.dismiss();
                if(destroyed||active()!=tab||page!=tab.videoPage||!id.equals(YouTubeSources.videoId(tab.url)))return;
                if(found!=null){tab.videos.resolved(found);updateToolbar();}showVideoList(tab,message);
            });
        });
    }
    private void showVideoList(Tab tab,String error){
        if(destroyed||privateLocked()||active()!=tab)return;
        java.util.List<VideoCatalog.Item> videos=tab.videos.list();
        if(videos.isEmpty()){
            boolean youtube=!YouTubeSources.videoId(tab.url).isEmpty();
            String message=youtube?(error.isEmpty()?"Für dieses YouTube-Video wurde keine erreichbare Downloadquelle gefunden.":error):"Noch kein speicherbarer Stream erkannt. Starte das Video und suche erneut.";
            AlertDialog.Builder empty=new AlertDialog.Builder(this).setTitle(youtube?"YouTube-Download nicht verfügbar":"Videos auf dieser Seite").setMessage(message).setNegativeButton("Schließen",null).setPositiveButton("Erneut suchen",(d,w)->showVideos());
            empty.setNeutralButton("Download-Fortschritt",(d,w)->showVideoQueue());empty.show();return;
        }
        String[] labels=new String[videos.size()];for(int i=0;i<labels.length;i++)labels[i]=videos.get(i).label();
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Videos · "+videos.size()).setItems(labels,(d,i)->videoOptions(videos.get(i))).setNegativeButton("Schließen",null).setPositiveButton("Neu suchen",(d,w)->{tab.videos.clearResolved();showVideos();});
        dialog.setNeutralButton("Download-Fortschritt",(d,w)->showVideoQueue());dialog.show();
    }
    private void showVideoQueue(){
        if(destroyed||privateLocked())return;if(videoDownloads!=null)videoDownloads.dismiss();videoDownloads=new VideoDownloadsDialog(this);videoDownloads.show();
    }
    private void videoOptions(VideoCatalog.Item item){
        Tab sourceTab=active();int sourcePage=sourceTab==null?-1:sourceTab.videoPage;String sourceUrl=sourceTab==null?"":sourceTab.url;
        boolean stream="HLS".equals(item.type)||"DASH".equals(item.type)||!item.audioUrl.isEmpty();
        String[] choices=stream?new String[]{"Als MP4 speichern","MP4-Ausschnitt: 1 Minute","MP4-Ausschnitt: 5 Minuten","MP4-Ausschnitt: 10 Minuten"}:new String[]{"Als MP4 speichern","Originaldatei speichern"};
        int castIndex=choices.length;if(!isPrivateMode()){choices=java.util.Arrays.copyOf(choices,choices.length+1);choices[castIndex]="Auf Fernseher übertragen";}
        new AlertDialog.Builder(this).setTitle(item.type+" · Videoaktionen").setItems(choices,(d,i)->{
            if(i==castIndex){castVideo(sourceTab,sourcePage,sourceUrl,item);return;}
            boolean original=!stream && i==1;long clip=stream&&i>0?new long[]{0,60000,300000,600000}[i]:0;
            chooseVideoFile(item,original,clip);
        }).setNegativeButton("Abbrechen",null).show();
    }
    private void showCast(){
        if(isPrivateMode()){toast("Übertragen ist nur in normalen Tabs verfügbar.");return;}
        if(browserCast==null)browserCast=new BrowserCast(this,this::chooseCastVideo);
        browserCast.show();
    }
    private void chooseCastVideo(){
        Tab tab=active();if(tab==null||isPrivateMode()||destroyed)return;
        int page=tab.videoPage;String url=tab.url;
        scanVideos(tab,()->{
            if(destroyed||active()!=tab||tab.videoPage!=page||!java.util.Objects.equals(url,tab.url))return;
            ArrayList<VideoCatalog.Item> candidates=new ArrayList<>();String reason=CastVideo.problem(null,url,tab.protectedMedia,false);
            for(VideoCatalog.Item item:tab.videos.list()){
                String problem=CastVideo.problem(item,url,tab.protectedMedia,false);
                if(problem.isEmpty())candidates.add(item);else reason=problem;
            }
            if(candidates.isEmpty()){new AlertDialog.Builder(this).setTitle("Kein übertragbares Video").setMessage(reason).setPositiveButton("OK",null).show();return;}
            String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).label();
            new AlertDialog.Builder(this).setTitle("Video zum Übertragen auswählen").setItems(labels,(d,i)->castVideo(tab,page,url,candidates.get(i))).setNegativeButton("Abbrechen",null).show();
        });
    }
    private void castVideo(Tab tab,int page,String url,VideoCatalog.Item item){
        if(destroyed||tab==null||active()!=tab||tab.videoPage!=page||!java.util.Objects.equals(url,tab.url)){toast("Die Seite wurde gewechselt. Bitte wähle das Video erneut aus.");return;}
        String problem=CastVideo.problem(item,tab.url,tab.protectedMedia,isPrivateMode());
        if(!problem.isEmpty()){new AlertDialog.Builder(this).setTitle("Video nicht übertragbar").setMessage(problem).setPositiveButton("OK",null).show();return;}
        showCast();if(browserCast==null)return;
        browserCast.select(CastVideo.from(item,tab.title),()->!destroyed&&active()==tab&&tab.videoPage==page&&java.util.Objects.equals(url,tab.url)&&CastVideo.problem(item,tab.url,tab.protectedMedia,isPrivateMode()).isEmpty(),()->{if(tab.view!=null)tab.view.evaluateJavascript("(function pause(w,n){try{w.document.querySelectorAll(\"video\").forEach(function(v){v.pause();});if(n<2)for(var i=0;i<Math.min(w.frames.length,8);i++)pause(w.frames[i],n+1);}catch(e){}})(window,0)",null);});
    }
    private void setNightMode(boolean enabled){
        BrowserAppearance.saveNight(this,enabled);BrowserAppearance.sync(this);applyAppearance();
    }
    private void tintToolbar(View view){
        if(view instanceof BrowserIcon){((BrowserIcon)view).tint=ink();view.invalidate();}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)tintToolbar(group.getChildAt(i));}
    }
    private void applyAppearance(){
        if(root==null)return;
        root.setBackgroundColor(getColor(isPrivateMode()?R.color.browser_private_paper:R.color.browser_paper));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getColor(R.color.browser_surface)));
        getWindow().setStatusBarColor(getColor(R.color.browser_paper));getWindow().setNavigationBarColor(getColor(R.color.browser_paper));
        siteState.setTextColor(ink());address.setTextColor(ink());address.setHintTextColor(getColor(R.color.browser_hint));
        ((View)address.getParent()).setBackground(rounded(getColor(R.color.browser_field),12));
        pages.setBackgroundColor(getColor(R.color.browser_page));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.browser_accent)));
        tintToolbar(top);tintToolbar(bottom);addressSuggestions.appearanceChanged();
        for(Tab tab:tabs)if(tab.view!=null){BrowserWebAppearance.apply(tab.view,BrowserAppearance.night(this));BrowserWebAppearance.pageFinished(tab.view,BrowserAppearance.night(this));}
        applyFullscreenUi();
    }
    private void settings(){
        new AlertDialog.Builder(this).setTitle("Einstellungen").setItems(new String[]{"Video-Downloadordner: "+DownloadFolder.label(this),"Suchmaschine","Werbeblocker: "+(adBlockingEnabled?"Ein":"Aus"),"Video-Downloads · Fortschritt und Warteschlange","Browserdaten löschen","Über BestBrowser","Nachtmodus: "+(BrowserAppearance.night(this)?"Ein":"Aus"),"Links im WLAN vorladen: "+(store.linkPreloadingEnabled()?"Ein":"Aus")},(d,which)->{
            if(which==0){pendingVideo=null;pickDownloadFolder();}else if(which==1)chooseEngine();else if(which==2)showAdBlockingSettings();else if(which==3)showVideoQueue();else if(which==4)clearData();else if(which==5)menuAction(11);else if(which==6)setNightMode(!BrowserAppearance.night(this));else showPreloadingSettings();
        }).setNegativeButton("Schließen",null).show();
    }
    private void refreshPreloading(){
        if(linkPreloader==null)return;
        Tab tab=active();if(!browserResumed||destroyed||tab==null||tab.view==null||tab.view.getProgress()<100){linkPreloader.pause();return;}
        linkPreloader.page(tab.view,tab.url);
    }
    private void setPreloadingEnabled(boolean enabled){
        store.linkPreloadingEnabled(enabled);if(linkPreloader!=null){if(!enabled)linkPreloader.clear();refreshPreloading();}
    }
    private void showPreloadingSettings(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(8),dp(24),dp(16));
        Switch toggle=new Switch(this);toggle.setText("Links im WLAN vorladen");toggle.setTextColor(ink());toggle.setTextSize(16);toggle.setMinHeight(dp(48));toggle.setChecked(store.linkPreloadingEnabled());toggle.setEnabled(!isPrivateMode());body.addView(toggle);
        TextView details=text("Bereitet geeignete HTTPS-Seitenlinks im Hintergrund vor. Nur im WLAN ohne Datenbegrenzung und solange die Seite geöffnet ist. Verbraucht zusätzliche Daten und Akkuleistung.\n\nDownloads und erkennbare Aktionslinks werden ausgelassen. Es werden keine verlinkten Seiten ausgeführt. Bei sehr großen Seiten werden bis zu 512 Links berücksichtigt.\n\nIn privaten Tabs ist diese Funktion ausgeschaltet.",14);details.setPadding(0,dp(12),0,dp(12));body.addView(details);
        TextView status=text(linkPreloader==null?"Im privaten Bereich nicht aktiv":linkPreloader.status(),14);body.addView(status);
        toggle.setOnCheckedChangeListener((button,enabled)->{setPreloadingEnabled(enabled);status.setText(linkPreloader==null?"Im privaten Bereich nicht aktiv":linkPreloader.status());});
        ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle("Vorladen im WLAN").setView(scroll).setPositiveButton("Fertig",null).show();
    }
    private void showAdBlockingSettings(){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),dp(8));
        Switch toggle=new Switch(this);toggle.setText("Werbeblocker");toggle.setTextColor(ink());toggle.setTextSize(16);toggle.setMinHeight(dp(48));toggle.setChecked(adBlockingEnabled);
        content.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        String host=active()==null?"":AdBlocker.host(active().url);
        if(!host.isEmpty()){
            String inherited=AdBlocker.exceptionHost(active().url,adBlockExceptions);final String settingHost=inherited.isEmpty()?host:inherited;
            Switch site=new Switch(this);site.setText("Auf "+settingHost+" blockieren");site.setTextColor(ink());site.setTextSize(15);site.setMinHeight(dp(48));
            site.setChecked(!AdBlocker.excepted(active().url,adBlockExceptions));site.setEnabled(adBlockingEnabled);
            site.setOnCheckedChangeListener((button,enabled)->setAdBlockSiteException(settingHost,!enabled));
            content.addView(site,new LinearLayout.LayoutParams(-1,-2));
            toggle.setOnCheckedChangeListener((button,enabled)->{setAdBlockingEnabled(enabled);site.setEnabled(enabled);});
        }else toggle.setOnCheckedChangeListener((button,enabled)->setAdBlockingEnabled(enabled));
        String message="Blockiert Werbe- und Trackingserver und blendet Werbeflächen aus. Die aktuelle Seite wird nach Änderungen neu geladen. YouTube-Videoanzeigen werden nicht zuverlässig entfernt.\n\n"+AdBlockResources.status();
        if(active()!=null&&!host.isEmpty())message+="\n"+active().blockedRequests.get()+" Anfragen auf dieser Seite blockiert";
        if(isPrivateMode())message+="\n\nDie Änderung gilt nur für diese private Sitzung.";
        TextView explanation=text(message,14);explanation.setPadding(0,dp(12),0,dp(8));content.addView(explanation);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Werbeblocker").setView(scroll).setPositiveButton("Fertig",null);
        if(!isPrivateMode())dialog.setNeutralButton(adBlockUpdating?"Aktualisierung läuft …":"Serverliste aktualisieren",(d,w)->updateAdBlockFilters());
        dialog.show();
    }
    private void setAdBlockingEnabled(boolean enabled){
        if(adBlockingEnabled==enabled)return;
        store.adBlockingEnabled(enabled);adBlockingEnabled=enabled;
        refreshAdBlocking();
        toast("Werbeblocker "+(enabled?"eingeschaltet":"ausgeschaltet")+(isPrivateMode()?" · nur für diese private Sitzung":""));
    }
    private void setAdBlockSiteException(String host,boolean excepted){
        store.adBlockException(host,excepted);adBlockExceptions=Collections.unmodifiableSet(store.adBlockExceptions());refreshAdBlocking();
    }
    private void clearAdBlockScript(Tab tab){
        if(tab.adBlockScript!=null){try{tab.adBlockScript.remove();}catch(RuntimeException ignored){}tab.adBlockScript=null;}
    }
    private void configureAdBlocking(Tab tab,WebView web){
        clearAdBlockScript(tab);
        String script=adBlockingEnabled?AdBlockResources.cosmeticScript(this,adBlockExceptions):AdBlockResources.CLEANUP_SCRIPT;
        try{
            if(adBlockingEnabled&&androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT))
                tab.adBlockScript=androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web,script,Collections.singleton("*"));
        }catch(RuntimeException|LinkageError ignored){ /* Page callbacks still apply cosmetic filtering. */ }
        web.evaluateJavascript(script,null);
    }
    private void refreshAdBlocking(){
        if(linkPreloader!=null)linkPreloader.clear();
        for(Tab tab:tabs)if(tab.view!=null)configureAdBlocking(tab,tab.view);
        WebView view=activeView();if(view!=null)view.reload();
    }
    private void updateAdBlockFilters(){
        if(adBlockUpdating)return;adBlockUpdating=true;toast("Werbefilter werden aktualisiert …");
        Context app=getApplicationContext();
        Thread worker=new Thread(()->{
            String error="";try{AdBlockResources.update(app);}catch(java.io.IOException|RuntimeException failure){error="Filter konnten nicht aktualisiert werden. Die bisherigen Filter bleiben aktiv.";}
            final String result=error;
            runOnUiThread(()->{adBlockUpdating=false;if(destroyed)return;if(result.isEmpty()){WebView view=activeView();if(view!=null)view.reload();toast("Werbefilter aktualisiert.");}else toast(result);});
        },"adblock-update");worker.setDaemon(true);worker.start();
    }
    private void showVpn(){
        String message=VpnSupport.statusText(this)+"\n\nDie VPN-Verbindung wird von einer VPN-App aufgebaut. BestBrowser nutzt diese Android-Verbindung. Prüfe in der VPN-App, dass BestBrowser nicht vom VPN ausgeschlossen ist. Der erkannte Status allein bestätigt weder die Erreichbarkeit eines Servers noch eine geänderte öffentliche IP-Adresse.";
        new AlertDialog.Builder(this).setTitle("VPN").setMessage(message).setPositiveButton("Auswählen",(d,w)->{
            new AlertDialog.Builder(this).setTitle("VPN auswählen").setItems(new String[]{"Kostenloses VPN · Proton VPN","Vorhandener VPN-Zugang","VPN-Status aktualisieren","Öffentliche IP prüfen"},(dialog,which)->{
                if(which==0)new AlertDialog.Builder(this).setTitle("Proton VPN Free").setMessage("Proton VPN bietet einen kostenlosen Tarif ohne Datenlimit. Ein Proton-Konto und die separate Proton-VPN-App sind nötig. Anmeldung, Serverwahl und Verbinden erfolgen dort. BestBrowser betreibt selbst keinen VPN-Server und aktiviert keine kostenpflichtige Option.").setPositiveButton("Proton VPN öffnen / installieren",(a,b)->openVpnClient(VpnSupport.PROTON_PACKAGE,"https://protonvpn.com/free-vpn/android")).setNegativeButton("Abbrechen",null).show();
                else if(which==1)showOwnVpn();else if(which==2)showVpn();else newTab("https://ipleak.net/",true);
            }).setNegativeButton("Schließen",null).show();
        }).setNegativeButton("Schließen",null).show();
    }
    private void showOwnVpn(){
        new AlertDialog.Builder(this).setTitle("Vorhandener VPN-Zugang").setItems(new String[]{"WireGuard-App öffnen / installieren","OpenVPN-Connect-App öffnen / installieren","Android-VPN-Einstellungen"},(d,which)->{
            if(which==0)openVpnClient(VpnSupport.WIREGUARD_PACKAGE,"https://www.wireguard.com/install/");
            else if(which==1)openVpnClient(VpnSupport.OPENVPN_PACKAGE,"https://openvpn.net/client/");
            else try{startActivity(VpnSupport.settings());}catch(ActivityNotFoundException error){toast("Die Android-VPN-Einstellungen sind auf diesem Gerät nicht verfügbar.");}
        }).setNegativeButton("Zurück",(d,w)->showVpn()).show();
    }
    private void openVpnClient(String packageName,String fallback){
        try{startActivity(VpnSupport.openClient(this,packageName));}
        catch(ActivityNotFoundException error){newTab(fallback,true);}
    }
    private void pickDownloadFolder(){
        Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri previous=DownloadFolder.selected(this);if(Build.VERSION.SDK_INT>=26&&previous!=null)choose.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,previous);
        try{startActivityForResult(choose,PICK_DOWNLOAD_FOLDER);}catch(ActivityNotFoundException error){pendingVideo=null;toast("Keine Ordnerauswahl verfügbar.");}
    }
    private void chooseVideoFile(VideoCatalog.Item item,boolean original,long clip){
        String title=active().title;
        if(DownloadFolder.selected(this)!=null){saveVideoInFolder(item,original,clip,title);return;}
        pendingVideo=item;pendingVideoOriginal=original;pendingVideoClip=clip;pendingVideoTitle=title;
        String message="Wähle einmal den Ordner für deine Videos. Weitere Videos werden automatisch dort gespeichert. Ändern kannst du ihn unter Menü → Einstellungen.";
        if(isPrivateMode())message+="\n\nGespeicherte Dateien bleiben nach dem Schließen privater Tabs erhalten.";
        new AlertDialog.Builder(this).setTitle("Downloadordner wählen").setMessage(message).setNegativeButton("Abbrechen",(d,w)->pendingVideo=null).setPositiveButton("Ordner wählen",(d,w)->pickDownloadFolder()).setOnCancelListener(d->pendingVideo=null).show();
    }
    private void saveVideoInFolder(VideoCatalog.Item item,boolean original,long clip,String title){
        Uri folder=DownloadFolder.selected(this);
        String name=title==null||title.trim().isEmpty()?"Video":title;
        if(original){name=URLUtil.guessFileName(item.url,null,null);if(name.endsWith(".bin")){if("MP4".equals(item.type))name="Video.mp4";else if("WebM".equals(item.type))name="Video.webm";}}
        else if(!name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4"))name+=".mp4";
        final String filename=DownloadFolder.safeName(name);
        String guessedMime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(filename).toLowerCase(java.util.Locale.ROOT));
        final String mime=original?(guessedMime==null?"application/octet-stream":guessedMime):"video/mp4";
        videoWorker.execute(()->{
            Uri destination;
            try{destination=DownloadFolder.create(this,folder,mime,filename);}catch(Exception error){runOnUiThread(()->{
                if(destroyed)return;
                new AlertDialog.Builder(this).setMessage("Der gewählte Videoordner ist nicht mehr beschreibbar oder erreichbar. Bitte prüfe die Verbindung oder wähle einen anderen Ordner.").setNegativeButton("Schließen",null).setPositiveButton("Ordner ändern",(d,w)->{
                    pendingVideo=item;pendingVideoOriginal=original;pendingVideoClip=clip;pendingVideoTitle=title;pickDownloadFolder();
                }).show();
            });return;}
            runOnUiThread(()->{
                if(destroyed){videoWorkerCleanup(destination);return;}
                try{
                    Intent download=new Intent(this,isPrivateMode()?PrivateVideoDownloadService.class:VideoDownloadService.class).putExtra("title",filename).putExtra("url",item.url).putExtra("type",item.type).putExtra("audio",item.audioUrl).putExtra("headers",new org.json.JSONObject(item.headers).toString()).putExtra("destination",destination.toString()).putExtra("original",original).putExtra("clip",clip);
                    if(Build.VERSION.SDK_INT>=26)startForegroundService(download);else startService(download);
                    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);
                    toast("Video hinzugefügt. Fortschritt unter Einstellungen → Video-Downloads.");
                }catch(Exception error){videoWorkerCleanup(destination);toast("Videospeicherung konnte nicht gestartet werden.");}
            });
        });
    }
    private void videoWorkerCleanup(Uri destination){
        new Thread(()->DownloadFolder.delete(getApplicationContext(),destination),"download-cleanup").start();
    }
    private void acceptYouTube(Tab tab,org.json.JSONObject data){
        if(destroyed||!tabs.contains(tab))return;
        String current=YouTubeSources.videoId(tab.url),reported=data.optString("videoId");
        if(!current.isEmpty()&&!current.equals(reported))return;
        try{
            YouTubeSources.Result before=tab.videos.youtube();if(before!=null&&!before.videoId.equals(reported))tab.videos.clear();
            java.util.Map<String,String> headers=new java.util.LinkedHashMap<>();
            headers.put("User-Agent",tab.view.getSettings().getUserAgentString());headers.put("Referer","https://www.youtube.com/");
            YouTubeSources.Result result=YouTubeSources.parse(data,headers,tab.videos.observed());tab.videos.youtube(result);
            if(active()==tab)updateToolbar();
        }catch(Exception ignored){}
    }
    private void scanYouTube(Tab tab,Runnable complete){
        if(tab==null||tab.view==null||!YouTubeSources.originAllowed(tab.url)){complete.run();return;}
        int page=tab.videoPage;tab.view.evaluateJavascript(YouTubeSources.script(this),value->{
            if(!destroyed&&page==tab.videoPage&&tabs.contains(tab))try{Object decoded=new org.json.JSONTokener(value).nextValue();acceptYouTube(tab,new org.json.JSONObject(String.valueOf(decoded)));}catch(Exception ignored){}
            complete.run();
        });
    }
}

