package com.thkim.noltalk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private ValueCallback<Uri[]> fileCb;
    private static final int FILE_REQ = 7031;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ) {
            if (fileCb != null) fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }
    private static final String HOME = "https://thkim3589-web.github.io/noltalk/";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " NoltalkApp");

        web.addJavascriptInterface(new Bridge(), "NoltalkApp");
        web.setWebChromeClient(new WebChromeClient() { // alert/confirm/prompt + 갤러리에서 사진 고르기
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(params.createIntent(), FILE_REQ); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u.toString().startsWith(HOME) || "thkim3589-web.github.io".equals(u.getHost())) return false;
                // 외부 링크는 기본 브라우저로
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) v.loadUrl("file:///android_asset/offline.html");
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(urlFrom(getIntent()));
    }

    private static String roomExtra(Intent i) {
        String r = i != null ? i.getStringExtra("room") : null;
        return (r != null && r.matches("[A-Za-z0-9]+")) ? r : null;
    }

    private String urlFrom(Intent i) {
        String rid = roomExtra(i);
        if (rid != null) return HOME + "?room=" + rid;
        Uri d = i != null ? i.getData() : null;
        return (d != null && d.toString().startsWith(HOME)) ? d.toString() : HOME;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String rid = roomExtra(intent);
        if (rid != null) web.evaluateJavascript("window.openRoomFromNotif && openRoomFromNotif('" + rid + "')", null);
        else if (intent.getData() != null) web.loadUrl(urlFrom(intent));
    }

    // 앱이 화면에 보이는지 웹에 알려줌 (안 보일 때만 알림 표시)
    private void tellVisibility(boolean v) {
        if (web != null) web.evaluateJavascript("window.__appVisible=" + v + ";window.dispatchEvent(new Event('appvis'))", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tellVisibility(true);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll(); } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        tellVisibility(false);
    }

    private void showNotification(String roomId, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("chat") == null)
            nm.createNotificationChannel(new NotificationChannel("chat", "채팅 메시지", NotificationManager.IMPORTANCE_HIGH));
        Intent i = new Intent(this, MainActivity.class).putExtra("room", roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, roomId.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "chat") : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_chat)
         .setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setAutoCancel(true)
         .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        nm.notify(roomId.hashCode(), b.build());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // 웹 화면이 처리(게임창 닫기, 방 나가기)하고, 처리할 게 없으면 앱 종료
        web.evaluateJavascript("(window.noltalkBack && window.noltalkBack()) ? 'y' : 'n'", r -> {
            if (!"\"y\"".equals(r)) {
                if (web.canGoBack() && web.getUrl() != null && web.getUrl().startsWith("file:")) web.goBack();
                else finish();
            }
        });
    }

    class Bridge {
        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "놀톡 초대하기"));
            });
        }
        @JavascriptInterface
        public void reload() { runOnUiThread(() -> web.loadUrl(HOME)); }

        @JavascriptInterface
        public int versionCode() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
            } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public void requestNotify() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            });
        }

        @JavascriptInterface
        public void notifyMsg(String roomId, String title, String body) {
            if (roomId == null || !roomId.matches("[A-Za-z0-9]+")) return;
            runOnUiThread(() -> showNotification(roomId, title == null ? "놀톡" : title, body == null ? "" : body));
        }

        // 웹에서 NoltalkApp.googleSignIn(웹클라이언트ID) 호출 → 구글 계정 선택 → ID 토큰을 웹으로 돌려줌
        @JavascriptInterface
        public void googleSignIn(String webClientId) {
            runOnUiThread(() -> startGoogleSignIn(webClientId));
        }
    }

    private void sendGoogleResult(String token, String error) {
        String js = "window.onNativeGoogle && window.onNativeGoogle(" +
                (token == null ? "null" : JSONObject.quote(token)) + "," +
                (error == null ? "null" : JSONObject.quote(error)) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private void startGoogleSignIn(String webClientId) {
        try {
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
            CredentialManager.create(this).getCredentialAsync(this, request, null, this::runOnUiThread,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential c = result.getCredential();
                        if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(c.getData());
                                sendGoogleResult(g.getIdToken(), null);
                            } catch (Exception e) { sendGoogleResult(null, "구글 정보를 읽지 못했어요"); }
                        } else sendGoogleResult(null, "지원하지 않는 로그인 방식이에요");
                    }
                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) sendGoogleResult(null, e.getMessage() == null ? "취소했어요" : "취소됨: " + e.getMessage());
                        else sendGoogleResult(null, "구글 로그인 실패: " + e.getClass().getSimpleName() + " " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            sendGoogleResult(null, "구글 로그인을 시작하지 못했어요: " + e.getMessage());
        }
    }

}
