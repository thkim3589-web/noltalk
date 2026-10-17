package com.thkim.noltalk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
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
        web.setWebChromeClient(new WebChromeClient()); // alert/confirm/prompt 사용
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

    private String urlFrom(Intent i) {
        Uri d = i != null ? i.getData() : null;
        return (d != null && d.toString().startsWith(HOME)) ? d.toString() : HOME;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) web.loadUrl(urlFrom(intent));
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
                        if (e instanceof GetCredentialCancellationException) sendGoogleResult(null, "취소했어요");
                        else sendGoogleResult(null, "구글 로그인 실패: " + e.getClass().getSimpleName() + " " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            sendGoogleResult(null, "구글 로그인을 시작하지 못했어요: " + e.getMessage());
        }
    }

}
