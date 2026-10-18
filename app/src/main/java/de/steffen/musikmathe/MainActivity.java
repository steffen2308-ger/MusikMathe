package de.steffen.musikmathe;

import android.Manifest;
import android.app.Activity;
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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int FILE_PICK = 10, WEB_PERMISSION = 11, DOWNLOAD_PERMISSION = 12, SAVE_VIDEO = 20;
    private static final int INK = 0xff234a43, PAPER = 0xfff5f7f8;
    static final class Tab {
        String url, title;
        boolean desktop;
        WebView view;
        Bundle state;
        final VideoCatalog videos=new VideoCatalog();
        volatile int videoPage;
        Tab(String url, String title, boolean desktop) { this.url = url; this.title = title; this.desktop = desktop; }
    }
    private final List<Tab> tabs = new ArrayList<>();
    private int selected;
    private BrowserStore store;
    private LinearLayout shell, top, bottom;
    private FrameLayout root, pages;
    private EditText address;
    private TextView siteState;
    private ProgressBar progress;
    private BrowserIcon back, forward, tabButton, videoButton;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private ValueCallback<Uri[]> upload;
    private Runnable permissionResult, permissionCancel;
    private Tab permissionTab;
    private String permissionOrigin;
    private int permissionGeneration;
    private boolean runtimePermissionPending;
    private AlertDialog permissionDialog;
    private String[] pendingDownload;
    private boolean destroyed;
    private boolean closingPrivate;
    private VideoCatalog.Item pendingVideo;
    private boolean pendingVideoOriginal;
    private long pendingVideoClip;
    private boolean isPrivateMode() { return this instanceof PrivateActivity; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (isPrivateMode() && !BrowserApplication.privateReady) {
            new AlertDialog.Builder(this).setMessage("Der getrennte private Browser konnte nicht gestartet werden.").setCancelable(false).setPositiveButton("Schließen", (d,w) -> finish()).show();
            return;
        }
        if (isPrivateMode()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new BrowserStore(this, isPrivateMode()); buildInterface();
        if (!isPrivateMode()) PrivateSession.cleanIfStopped(this);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        for (BrowserStore.Entry row : store.read("tabs")) tabs.add(new Tab(row.url, row.title, row.desktop));
        if (tabs.isEmpty()) tabs.add(new Tab(Address.HOME, "Neuer Tab", false));
        selected = Math.max(0, Math.min(store.selected(), tabs.size() - 1));
        switchTab(selected);
        if (saved == null) acceptIntent(getIntent());
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(INK); return view;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private BrowserIcon icon(LinearLayout row, String kind, String label, Runnable action) {
        BrowserIcon icon = new BrowserIcon(this, kind, label);
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48))); icon.setOnClickListener(v -> action.run()); return icon;
    }
    private void buildInterface() {
        root = new FrameLayout(this); root.setBackgroundColor(isPrivateMode() ? 0xffede5f4 : PAPER);
        shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
            });
        }
        if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(6), dp(4), dp(6), dp(4));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout omnibox = new LinearLayout(this); omnibox.setGravity(Gravity.CENTER_VERTICAL); omnibox.setBackground(rounded(0xffe5eeea, 24));
        top.addView(omnibox, new LinearLayout.LayoutParams(0, dp(48), 1));
        siteState = text("⊕", 20); siteState.setGravity(Gravity.CENTER); siteState.setContentDescription("Informationen zur Seite");
        omnibox.addView(siteState, new LinearLayout.LayoutParams(dp(44), -1)); siteState.setOnClickListener(v -> siteInfo());
        address = new EditText(this); address.setSingleLine(true); address.setSelectAllOnFocus(true); address.setTextSize(16); address.setTextColor(INK);
        address.setHint("Adresse oder Suche"); address.setHintTextColor(0xff647e75); address.setContentDescription("Adresse oder Suchbegriff"); address.setBackgroundColor(Color.TRANSPARENT);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI | (isPrivateMode() && Build.VERSION.SDK_INT >= 26 ? EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING : 0));
        address.setPadding(0,0,dp(8),0); omnibox.addView(address, new LinearLayout.LayoutParams(0,-1,1));
        address.setOnKeyListener((v,key,event) -> {
            if (key != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) navigateTyped(address.getText().toString());
            return true;
        });
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                navigateTyped(address.getText().toString()); return true;
            } return false;
        });
        BrowserIcon reload = icon(top, "reload", "Neu laden oder Laden stoppen", () -> { WebView w = activeView(); if(w != null) { if(w.getProgress() < 100)w.stopLoading();else w.reload(); } });
        reload.setOnLongClickListener(v -> { WebView w=activeView();if(w!=null){w.clearCache(false);w.reload();}return true; });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff247e6d));
        shell.addView(progress, new LinearLayout.LayoutParams(-1, dp(2))); progress.setVisibility(View.INVISIBLE);
        pages = new FrameLayout(this); pages.setBackgroundColor(Color.WHITE); shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(bottom, new LinearLayout.LayoutParams(-1, dp(50)));
        back = icon(bottom, "back", "Zurück", this::goBack);
        forward = icon(bottom, "forward", "Vorwärts", () -> { if(activeView()!=null && activeView().canGoForward())activeView().goForward(); });
        icon(bottom, "home", "Startseite", () -> navigate(Address.HOME));
        videoButton=icon(bottom,"video","Videos auf dieser Seite",this::showVideos);
        View space = new View(this); bottom.addView(space, new LinearLayout.LayoutParams(0,1,1));
        tabButton = icon(bottom, "tabs", "Tabs anzeigen", this::showTabs);
        tabButton.setOnLongClickListener(v -> { newTab(Address.HOME, true); return true; });
        icon(bottom, "menu", "Menü", this::menu);
    }
    private Tab active() { return tabs.isEmpty() ? null : tabs.get(selected); }
    private WebView activeView() { Tab tab=active(); return tab==null ? null : tab.view; }
    private void hideKeyboard() {
        address.clearFocus(); pages.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
    }
    private void navigateTyped(String input) {
        String url = Address.resolve(input, store.engine());
        if(url==null) { toast("Bitte eine gültige http- oder https-Adresse eingeben."); return; }
        navigate(url);
    }
    private void navigate(String url) {
        if (!Address.isWeb(url) && !Address.HOME.equals(url)) return;
        hideKeyboard(); WebView web=activeView(); if(web!=null) { web.loadUrl(url); active().url=url; updateToolbar(); persist(); }
    }
    private void newTab(String url, boolean focus) {
        if(tabs.size() >= BrowserStore.MAX_TABS) { toast("Bitte zuerst einen Tab schließen (maximal 20)."); return; }
        tabs.add(new Tab(url, Address.HOME.equals(url)?"Neuer Tab":url, false));
        if(focus) switchTab(tabs.size()-1); else updateToolbar(); persist();
    }
    private void switchTab(int index) {
        if(index<0 || index>=tabs.size()) return;
        cancelPermission(); cancelUpload();
        WebView old=activeView(); if(old!=null) { old.onPause(); pages.removeView(old); }
        hideFullscreen(); selected=index; Tab tab=active();
        if(tab.view==null) tab.view=createWebView(tab);
        if(tab.view.getParent() instanceof ViewGroup)((ViewGroup)tab.view.getParent()).removeView(tab.view);
        pages.removeAllViews(); pages.addView(tab.view,new FrameLayout.LayoutParams(-1,-1)); tab.view.onResume();
        updateToolbar(); persist();
    }
    private void closeTab(int index) {
        if(index<0 || index>=tabs.size()) return;
        if(isPrivateMode() && tabs.size()==1 && VideoDownloadService.busy()){endPrivate();return;}
        cancelPermission(); cancelUpload(); hideFullscreen();
        Tab removed=tabs.remove(index); if(removed.view!=null) { pages.removeView(removed.view); removed.view.stopLoading(); removed.view.destroy(); }
        if(tabs.isEmpty() && isPrivateMode()) { endPrivate(); return; }
        if(tabs.isEmpty()) tabs.add(new Tab(Address.HOME,"Neuer Tab",false));
        if(index<selected)selected--; selected=Math.min(selected,tabs.size()-1); switchTab(selected);
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private WebView createWebView(Tab tab) {
        WebView web=new WebView(this);
        if (isPrivateMode()) {
            web.setSaveEnabled(false);
            if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        WebSettings settings=web.getSettings();
        if (isPrivateMode()) { settings.setCacheMode(WebSettings.LOAD_NO_CACHE); settings.setSaveFormData(false); settings.setSavePassword(false); } settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true); settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if(Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
        applyDesktop(web, tab.desktop);
        YouTubeBridge.attach(this,web,data -> acceptYouTube(tab,data));
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && WikiTop.URL.equals(request.getUrl().toString())) return WikiTop.response();
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
                String old=YouTubeSources.videoId(tab.url),next=YouTubeSources.videoId(url);
                if(!next.isEmpty()&&!next.equals(old))tab.videos.clear();
                tab.url=url;if(active()==tab)updateToolbar();
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon) {
                tab.videoPage++;tab.videos.clear();tab.url=url; cancelPermissionFor(tab); if(active()==tab)updateToolbar();
            }
            @Override public void onPageFinished(WebView view,String url) {
                tab.url=url; tab.title=view.getTitle()==null?url:view.getTitle();
                store.visit(url,tab.title); if(active()==tab)updateToolbar(); persist();
                view.evaluateJavascript(VideoCatalog.HOOKS,null);
                if(YouTubeSources.originAllowed(url))view.evaluateJavascript(YouTubeSources.script(MainActivity.this),null);
                int page=tab.videoPage;view.postDelayed(() -> {if(!destroyed && tab.videoPage==page && tab.view==view)scanVideos(tab,()->{});},1000);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame() && active()==tab)toast("Seite konnte nicht geladen werden. Internetverbindung und Adresse prüfen.");
            }
            @Override public boolean onRenderProcessGone(WebView view,android.webkit.RenderProcessGoneDetail detail) {
                pages.removeView(view); view.destroy(); tab.view=null; tab.state=null;
                if(active()==tab) { toast("Die Seite wurde beendet. Sie wird neu geladen."); pages.post(() -> { if(!destroyed && tabs.contains(tab))switchTab(tabs.indexOf(tab)); }); }
                return true;
            }
            // The platform rejects invalid TLS certificates and unsafe browsing hits; no bypass.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) { if(active()==tab) { progress.setProgress(value); progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE); back.setEnabled(view.canGoBack()); forward.setEnabled(view.canGoForward()); } }
            @Override public void onReceivedTitle(WebView view,String title) { tab.title=title; if(active()==tab)updateToolbar(); }
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,android.os.Message message) {
                if(!userGesture || active()!=tab || tabs.size()>=BrowserStore.MAX_TABS)return false;
                newTab("about:blank",true); WebView child=activeView();
                ((WebView.WebViewTransport)message.obj).setWebView(child); message.sendToTarget(); return true;
            }
            @Override public void onCloseWindow(WebView window) { if(tabs.contains(tab))closeTab(tabs.indexOf(tab)); }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(active()!=tab)return false; cancelUpload(); upload=callback;
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE);
                ArrayList<String> types=new ArrayList<>(); for(String type:params.getAcceptTypes())if(type!=null && type.contains("/"))types.add(type);
                pick.setType(types.size()==1?types.get(0):"*/*"); if(types.size()>1)pick.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(pick,FILE_PICK); } catch(ActivityNotFoundException e) { cancelUpload(); toast("Keine Dateiauswahl verfügbar."); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> requestWebsitePermission(tab,request)); }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) { cancelPermissionFor(tab); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback) { requestLocation(tab,origin,callback); }
            @Override public void onGeolocationPermissionsHidePrompt() { cancelPermissionFor(tab); }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(active()!=tab || customView!=null) { callback.onCustomViewHidden(); return; }
                customView=view; customCallback=callback; shell.setVisibility(View.GONE); root.addView(view,new FrameLayout.LayoutParams(-1,-1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() { hideFullscreen(); }
        });
        web.setDownloadListener((url,userAgent,disposition,mime,length) -> { if(active()!=tab)return;if(Address.isWeb(url))confirmDownload(url,userAgent,disposition,mime);else toast("Dieser Downloadtyp wird noch nicht unterstützt."); });
        web.setOnLongClickListener(v -> {
            WebView.HitTestResult hit=web.getHitTestResult(); if(hit==null || hit.getExtra()==null)return false;
            String target=hit.getExtra(); if(!Address.isWeb(target))return false;
            new AlertDialog.Builder(this).setTitle(target).setItems(new String[]{"In neuem Tab öffnen","Link kopieren","Link teilen"},(dialog,which) -> { if(which==0)newTab(target,true);else if(which==1)copy(target);else share(target); }).show(); return true;
        });
        if((tab.state==null || web.restoreState(tab.state)==null) && !"about:blank".equals(tab.url))web.loadUrl(Address.isWeb(tab.url)?tab.url:Address.HOME);
        tab.state=null; return web;
    }
    private void applyDesktop(WebView view,boolean desktop) {
        String ua=WebSettings.getDefaultUserAgent(this);
        if(desktop)ua=ua.replace("; wv","").replace("Android","X11; Linux x86_64").replace("Mobile ","");
        view.getSettings().setUserAgentString(ua); view.getSettings().setUseWideViewPort(true);
    }
    private boolean routeLink(Tab tab,String url,boolean gesture) {
        if(Address.isWeb(url) || Address.HOME.equals(url) || "about:blank".equals(url))return false;
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
        videoButton.count=tab.videos.list().size();if(videoButton.count==0&&(!YouTubeSources.videoId(tab.url).isEmpty()||tab.videos.youtube()!=null))videoButton.count=1;videoButton.setContentDescription("Videos auf dieser Seite · "+videoButton.count+" erkannt");videoButton.invalidate();
        tabButton.count=tabs.size(); tabButton.setContentDescription(tabs.size()+" Tabs. Tabs anzeigen"); tabButton.invalidate();
        int value=tab.view==null?100:tab.view.getProgress(); progress.setProgress(value); progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);
    }
    private void menu() {
        PopupMenu popup=new PopupMenu(this,bottom.getChildAt(bottom.getChildCount()-1));
        String[] actions={"Neuer Tab",store.bookmarked(active().url)?"Lesezeichen entfernen":"Lesezeichen hinzufügen","Lesezeichen","Verlauf","Downloads","Seite teilen","Link kopieren","Auf Seite suchen",active().desktop?"Mobile Ansicht":"Desktopansicht","Suchmaschine","Browserdaten löschen","Über MusikMathe"};
        for(int i=0;i<actions.length;i++)popup.getMenu().add(0,i,i,actions[i]);
        popup.getMenu().add(0,15,0,"Videos erkennen und speichern");
        popup.getMenu().add(0,12,0,"Neuer privater Tab");
        popup.getMenu().add(0,13,1,isPrivateMode()?"Zu normalen Tabs":"Zu privaten Tabs");
        if (isPrivateMode()) {
            popup.getMenu().add(0,14,2,"Alle privaten Tabs schließen");
            popup.getMenu().findItem(1).setEnabled(false);
        }
        popup.setOnMenuItemClickListener(item -> {
            Tab tab=active();
            switch(item.getItemId()) {
                case 0: newTab(Address.HOME,true); break;
                case 1: if(Address.isWeb(tab.url))toast(store.toggleBookmark(tab.url,tab.title)?"Lesezeichen gespeichert":"Lesezeichen entfernt");else toast("Zuerst eine Webseite öffnen.");break;
                case 2: showLibrary("bookmarks","Lesezeichen");break;
                case 3: showLibrary("history","Verlauf");break;
                case 4: try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(ActivityNotFoundException e){toast("Keine Downloadübersicht verfügbar.");}break;
                case 5: if(Address.isWeb(tab.url))share(tab.url);break;
                case 6: if(Address.isWeb(tab.url))copy(tab.url);break;
                case 7: findInPage();break;
                case 8: tab.desktop=!tab.desktop;applyDesktop(tab.view,tab.desktop);tab.view.reload();persist();break;
                case 9: chooseEngine();break;
                case 10: clearData();break;
                case 11: new AlertDialog.Builder(this).setTitle("MusikMathe · 0.3.1").setMessage("Android-Browser mit Chromium-WebView.\n\nTabs, Lesezeichen, Verlauf, Downloads und Dateiuploads. Browserdaten bleiben auf diesem Gerät.\n\nPrivate Tabs haben getrennte Website-Daten und werden nicht wiederhergestellt. Keine Chrome-Synchronisierung oder Erweiterungen. DRM- und Anmeldefunktionen hängen von der jeweiligen Website und WebView ab.").setPositiveButton("OK",null).show();break;
                case 12: if (isPrivateMode()) newTab(Address.HOME,true); else openPrivate(true); break;
                case 13: if (isPrivateMode()) openNormal(); else openPrivate(false); break;
                case 14: endPrivate(); break;
                case 15: showVideos(); break;
            } return true;
        }); popup.show();
    }
    private void showTabs() {
        hideKeyboard(); LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(8),dp(8),dp(8));
        ScrollView scroll=new ScrollView(this);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle((isPrivateMode()?"Private Tabs · ":"Tabs · ")+tabs.size()).setView(scroll).setPositiveButton("Neuer Tab",(d,w)->newTab(Address.HOME,true)).setNegativeButton("Fertig",null).create();
        for(int i=0;i<tabs.size();i++) {
            final int index=i; Tab tab=tabs.get(i); LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(8),0,dp(8));
            if(i==selected)row.setBackground(rounded(0xffe5eeea,12));
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
                if(checked[0]) { store.write("history",Collections.emptyList());for(Tab tab:tabs){tab.state=null;if(tab.view!=null)tab.view.clearHistory();} }
                if(checked[1]) { CookieManager.getInstance().removeAllCookies(value->CookieManager.getInstance().flush());android.webkit.WebStorage.getInstance().deleteAllData();GeolocationPermissions.getInstance().clearAll(); }
                if(checked[2] && activeView()!=null)activeView().clearCache(true);
                toast("Ausgewählte Browserdaten gelöscht.");
            }).show();
    }
    private void siteInfo() {
        String url=active().url;String info=WikiTop.URL.equals(url)?"Lokale Wikipedia-Rangliste. Die Daten werden verschlüsselt von Wikimedia Analytics geladen.":Address.HOME.equals(url)?"Lokale Startseite":url.startsWith("https://")?"Diese Adresse nutzt HTTPS. Die Zertifikatsprüfung übernimmt Android WebView.":"Diese Seite verwendet keine verschlüsselte HTTPS-Verbindung.";
        new AlertDialog.Builder(this).setTitle("Seiteninformationen").setMessage(info+"\n\n"+Address.display(url)).setPositiveButton("OK",null).show();
    }
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
        if(runtimePermissionPending || active()!=tab || !"https".equalsIgnoreCase(request.getOrigin().getScheme())){request.deny();return;}
        cancelPermission();ArrayList<String> runtime=new ArrayList<>(),resources=new ArrayList<>();
        for(String resource:request.getResources()){
            if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)){runtime.add(Manifest.permission.RECORD_AUDIO);resources.add(resource);}
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)){runtime.add(Manifest.permission.CAMERA);resources.add(resource);}
        }
        if(resources.isEmpty()){request.deny();return;}
        permissionTab=tab;permissionOrigin=origin(request.getOrigin().toString());permissionCancel=request::deny;
        int version=permissionGeneration;
        permissionResult=()->{ArrayList<String> allowed=new ArrayList<>();for(int i=0;i<runtime.size();i++)if(checkSelfPermission(runtime.get(i))==PackageManager.PERMISSION_GRANTED)allowed.add(resources.get(i));if(allowed.isEmpty())request.deny();else request.grant(allowed.toArray(new String[0]));};
        permissionDialog=new AlertDialog.Builder(this).setTitle("Website-Berechtigung").setMessage(Address.host(request.getOrigin().toString())+" möchte "+(resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)?"die Kamera":"")+(resources.size()==2?" und ":"")+(resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)?"das Mikrofon":"")+" verwenden.")
            .setNegativeButton("Ablehnen",(d,w)->cancelPermission()).setOnCancelListener(d->cancelPermission()).setPositiveButton("Erlauben",(d,w)->{
                if(permissionTab!=tab || version!=permissionGeneration)return;runtimePermissionPending=true;requestPermissions(runtime.toArray(new String[0]),WEB_PERMISSION);
            }).show();
    }
    private void requestLocation(Tab tab,String requestedOrigin,GeolocationPermissions.Callback callback) {
        if(runtimePermissionPending || active()!=tab || !requestedOrigin.startsWith("https://")){callback.invoke(requestedOrigin,false,false);return;}
        cancelPermission();permissionTab=tab;permissionOrigin=origin(requestedOrigin);
        int version=permissionGeneration;
        permissionCancel=()->callback.invoke(requestedOrigin,false,false);
        permissionResult=()->callback.invoke(requestedOrigin,checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED,false);
        permissionDialog=new AlertDialog.Builder(this).setTitle("Standort teilen?").setMessage(Address.host(requestedOrigin)+" möchte deinen Standort verwenden.").setNegativeButton("Ablehnen",(d,w)->cancelPermission()).setOnCancelListener(d->cancelPermission()).setPositiveButton("Erlauben",(d,w)->{
            if(permissionTab==tab && version==permissionGeneration){runtimePermissionPending=true;requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},WEB_PERMISSION);}
        }).show();
    }
    private String origin(String url) {
        Uri uri=Uri.parse(url);return uri.getScheme()+"://"+uri.getHost()+":"+(uri.getPort()<0?443:uri.getPort());
    }
    private void cancelPermissionFor(Tab tab) { if(permissionTab==tab)cancelPermission(); }
    private void cancelPermission() {
        permissionGeneration++;if(permissionDialog!=null){permissionDialog.dismiss();permissionDialog=null;}Runnable cancel=permissionCancel;permissionCancel=null;permissionResult=null;permissionTab=null;permissionOrigin=null;if(cancel!=null)cancel.run();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==DOWNLOAD_PERMISSION){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)download();else{pendingDownload=null;toast("Download benötigt Zugriff auf den Downloadordner.");}}
        if(code==WEB_PERMISSION){
            runtimePermissionPending=false;
            Runnable result=permissionResult;
            if(result!=null && active()==permissionTab && origin(active().url).equals(permissionOrigin)){
                permissionResult=null;permissionCancel=null;permissionTab=null;permissionOrigin=null;result.run();
            }else cancelPermission();
        }
    }
    private void cancelUpload(){ValueCallback<Uri[]> callback=upload;upload=null;if(callback!=null)callback.onReceiveValue(null);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==SAVE_VIDEO){
            VideoCatalog.Item item=pendingVideo;pendingVideo=null;
            if(result!=RESULT_OK || data==null || data.getData()==null)return;
            Uri destination=data.getData();
            if(item==null || !"content".equals(destination.getScheme())){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}return;}
            try{
                if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0 && (data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(destination,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Intent download=new Intent(this,isPrivateMode()?PrivateVideoDownloadService.class:VideoDownloadService.class).putExtra("url",item.url).putExtra("type",item.type).putExtra("audio",item.audioUrl).putExtra("headers",new org.json.JSONObject(item.headers).toString()).putExtra("destination",destination.toString()).putExtra("original",pendingVideoOriginal).putExtra("clip",pendingVideoClip);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(download);else startService(download);
                if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);
                toast("Video wird gespeichert. Fortschritt und Abbruch unter Videos oder in der Benachrichtigung.");
            }catch(Exception error){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}toast("Videospeicherung konnte nicht gestartet werden.");}
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
        getWindow().getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        WebChromeClient.CustomViewCallback callback=customCallback;customCallback=null;if(callback!=null)callback.onCustomViewHidden();
    }
    private void goBack(){if(customView!=null){hideFullscreen();return;}WebView web=activeView();if(web!=null && web.canGoBack())web.goBack();else if(tabs.size()>1)closeTab(selected);else if(isPrivateMode())endPrivate();else moveTaskToBack(true);}
    @Override public void onBackPressed(){goBack();}
    private void share(String url){try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url),"Seite teilen"));}catch(ActivityNotFoundException e){toast("Keine passende App verfügbar.");}}
    private void copy(String url){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Webadresse",url));toast("Link kopiert.");}
    private void toast(String message){if(!destroyed)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void persist(){
        if(store==null || isPrivateMode())return;ArrayList<BrowserStore.Entry> rows=new ArrayList<>();for(Tab tab:tabs)rows.add(new BrowserStore.Entry("about:blank".equals(tab.url)?Address.HOME:tab.url,tab.title,0,tab.desktop));store.write("tabs",rows);store.selected(selected);
    }
    private void acceptIntent(Intent intent){if(intent!=null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData()!=null){String url=intent.getData().toString();if(Address.isWeb(url))newTab(url,true);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptIntent(intent);if(isPrivateMode() && intent.getBooleanExtra("new_private_tab",false))newTab(Address.HOME,true);}
    private void openPrivate(boolean newTab) {
        if (Build.VERSION.SDK_INT < 28) { toast("Private Tabs benötigen Android 9 oder neuer."); return; }
        startActivity(new Intent(this,PrivateActivity.class).putExtra("new_private_tab",newTab).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private void openNormal() {
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private void endPrivate() {
        if (!isPrivateMode()) return;
        if(VideoDownloadService.busy()){
            new AlertDialog.Builder(this).setMessage("Im privaten Bereich läuft ein Videoexport. Abbrechen und alle privaten Tabs schließen?").setNegativeButton("Weiter speichern",null).setPositiveButton("Abbrechen und schließen",(d,w)->{VideoDownloadService.cancelCurrent();endPrivate();}).show();return;
        }
        closingPrivate=true;openNormal();finish();
    }
    @Override protected void onSaveInstanceState(Bundle state){persist();super.onSaveInstanceState(state);}
    @Override protected void onPause(){persist();if(store!=null)CookieManager.getInstance().flush();if(activeView()!=null)activeView().onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(store==null)return;if(activeView()!=null)activeView().onResume();if(!isPrivateMode()){
        PrivateSession.cleanIfStopped(this);
        root.postDelayed(() -> { if(!destroyed)PrivateSession.cleanIfStopped(this); },1000);
    }}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){for(Tab tab:tabs)if(tab!=active() && tab.view!=null){tab.state=new Bundle();tab.view.saveState(tab.state);tab.view.destroy();tab.view=null;}}
    }
    @Override protected void onDestroy(){
        destroyed=true;cancelPermission();cancelUpload();hideFullscreen();if(pages!=null)pages.removeAllViews();for(Tab tab:tabs)if(tab.view!=null){tab.view.stopLoading();tab.view.destroy();}super.onDestroy();
        if (isPrivateMode() && !isChangingConfigurations()) {
            VideoDownloadService.cancelCurrent();
            try { if(Build.VERSION.SDK_INT>=28)PrivateSession.wipe(this); } catch(java.io.IOException ignored) { }
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
        Tab tab=active();scanYouTube(tab,()->scanVideos(tab,()->{
            if(active()!=tab || destroyed)return;
            java.util.List<VideoCatalog.Item> videos=tab.videos.list();
            if(videos.isEmpty()&&!YouTubeSources.videoId(tab.url).isEmpty()){
                YouTubeSources.Result yt=tab.videos.youtube();String title=yt==null?tab.title:yt.title;
                String reason=yt==null||yt.reason.isEmpty()?"Der Player hat noch keine direkt speicherbare Videoquelle bereitgestellt. Starte das Video und wähle danach erneut Suchen.":yt.reason;
                new AlertDialog.Builder(this).setTitle("YouTube-Video erkannt").setMessage(title+"\n\n"+reason).setPositiveButton("Erneut suchen",(d,w)->showVideos()).setNegativeButton("Schließen",null).show();return;
            }
            if(videos.isEmpty()){
                AlertDialog.Builder empty=new AlertDialog.Builder(this).setTitle("Videos auf dieser Seite").setMessage("Noch kein speicherbarer Stream erkannt. Starte das Video auf der Webseite und öffne diese Liste erneut.\n\nDirekte Videodateien sowie HLS-/DASH-Adressen werden erkannt. Manche Player verbergen die Quelle oder verwenden Kopierschutz."+(VideoDownloadService.status.isEmpty()?"":"\n\n"+VideoDownloadService.status)).setPositiveButton("OK",null).setNegativeButton("Erneut suchen",(d,w)->showVideos());
                if(VideoDownloadService.busy())empty.setNeutralButton("Export abbrechen",(d,w)->VideoDownloadService.cancelCurrent());empty.show();return;
            }
            String[] labels=new String[videos.size()];for(int i=0;i<labels.length;i++)labels[i]=videos.get(i).label();
            AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Videos · "+videos.size()).setItems(labels,(d,i)->videoOptions(videos.get(i))).setNegativeButton("Schließen",null).setPositiveButton("Neu suchen",(d,w)->showVideos());
            if(VideoDownloadService.busy())dialog.setNeutralButton("Export abbrechen",(d,w)->VideoDownloadService.cancelCurrent());
            else if(!VideoDownloadService.status.isEmpty())dialog.setNeutralButton("Letzter Export",(d,w)->new AlertDialog.Builder(this).setMessage(VideoDownloadService.status).setPositiveButton("OK",null).show());
            dialog.show();
        }));
    }
    private void videoOptions(VideoCatalog.Item item){
        if(VideoDownloadService.busy()){new AlertDialog.Builder(this).setMessage(VideoDownloadService.status+"\n\nEs läuft bereits ein Videoexport.").setNegativeButton("Weiter speichern",null).setPositiveButton("Export abbrechen",(d,w)->VideoDownloadService.cancelCurrent()).show();return;}
        boolean stream="HLS".equals(item.type)||"DASH".equals(item.type)||!item.audioUrl.isEmpty();
        String[] choices=stream?new String[]{"Als MP4 speichern","MP4-Ausschnitt: 1 Minute","MP4-Ausschnitt: 5 Minuten","MP4-Ausschnitt: 10 Minuten"}:new String[]{"Als MP4 speichern","Originaldatei speichern"};
        new AlertDialog.Builder(this).setTitle(item.type+" speichern").setItems(choices,(d,i)->{
            boolean original=!stream && i==1;long clip=stream&&i>0?new long[]{0,60000,300000,600000}[i]:0;
            String info="Speicherort wählen. HLS und DASH werden mit Bild und Ton nach MP4 exportiert. Je nach Videoformat kann das dauern.\n\nLive-Streams brauchen einen begrenzten Ausschnitt; nicht jede Live-Quelle wird unterstützt. Kopiergeschützte oder abgelaufene Quellen können nicht exportiert werden.";
            if(isPrivateMode())info+="\n\nDie Datei bleibt nach Ende der privaten Sitzung erhalten. Während des Exports den privaten Bereich geöffnet lassen.";
            new AlertDialog.Builder(this).setMessage(info).setNegativeButton("Abbrechen",null).setPositiveButton("Speicherort wählen",(dialog,which)->chooseVideoFile(item,original,clip)).show();
        }).setNegativeButton("Abbrechen",null).show();
    }
    private void chooseVideoFile(VideoCatalog.Item item,boolean original,long clip){
        pendingVideo=item;pendingVideoOriginal=original;pendingVideoClip=clip;
        String guessed=URLUtil.guessFileName(item.url,null,null);String name=active().title==null?"Video":active().title;
        name=name.replaceAll("[\\\\/\\p{Cntrl}:*?\"<>|]","_");if(name.length()>80)name=name.substring(0,80);
        if(original){name=guessed;if(name.endsWith(".bin")){if("MP4".equals(item.type))name="Video.mp4";else if("WebM".equals(item.type))name="Video.webm";}}else name+=".mp4";
        String originalMime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(name).toLowerCase(java.util.Locale.ROOT));
        Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(original?(originalMime==null?"application/octet-stream":originalMime):"video/mp4").putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(create,SAVE_VIDEO);}catch(ActivityNotFoundException error){pendingVideo=null;toast("Keine Dateiauswahl verfügbar.");}
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
