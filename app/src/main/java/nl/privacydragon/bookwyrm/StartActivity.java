package nl.privacydragon.bookwyrm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StartActivity extends AppCompatActivity {
    WebView myWebView;
    ProgressBar LoadIndicator;
    public ValueCallback<Uri[]> omhooglader;
    String putje = "";
    String sessie = "";
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        LoadIndicator = (ProgressBar) findViewById(R.id.progressBar3);
        ActivityResultLauncher<Intent> voodooLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        if (omhooglader == null)
                            return;
                        omhooglader.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), data));
                    }
                    else {
                        omhooglader.onReceiveValue(null);
                    }
                });
        myWebView = (WebView) findViewById(R.id.webview);
        myWebView.setVisibility(View.GONE);
        myWebView.getSettings().setUserAgentString(getString(R.string.gebruikersagent));
        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.addJavascriptInterface(new Object()
        {
            @JavascriptInterface           // For API 17+
            public void performClick(String what)
            {
                if (!what.contains("[object Window]")) { //For some reason the function has to be called when the event listener is attached to the button. So, by adding in 'this', it is possible to make sure to only act when the thing that called the function is NOT the window, but the button.
                    ScanBarCode();
                }

            }
        }, "scan");
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (omhooglader != null) {
                    //omhooglader.onReceiveValue(null);
                    omhooglader = null;
                }
                omhooglader = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    voodooLauncher.launch(intent);
                } catch (ActivityNotFoundException grrr){
                    omhooglader = null;
                    return false;
                }
                return true;
            }
        });
        //The user credentials are stored in the shared preferences, so first they have to be read from there.
        String defaultValue = "none";
        SharedPreferences sharedPref = StartActivity.this.getSharedPreferences(getString(R.string.server), Context.MODE_PRIVATE);
        String server = sharedPref.getString(getString(R.string.server), defaultValue);
        SharedPreferences sharedPrefName = StartActivity.this.getSharedPreferences(getString(R.string.name), Context.MODE_PRIVATE);
        String name = sharedPrefName.getString(getString(R.string.name), defaultValue);
        SharedPreferences sharedPrefPass = StartActivity.this.getSharedPreferences(getString(R.string.pw), Context.MODE_PRIVATE);
        String pass = sharedPrefPass.getString(getString(R.string.pw), defaultValue);
        SharedPreferences sharedPrefMagic = StartActivity.this.getSharedPreferences(getString(R.string.q), Context.MODE_PRIVATE);
        String codeMagic = sharedPrefMagic.getString(getString(R.string.q), defaultValue);
        //Then all the decryption stuff has to happen. There are a lot of try-catch stuff, because apparently that seems to be needed.
        //First get the keystore thing.
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        //Then, load it. or something. To make sure that it can be used.
        try {
            keyStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //Next, retrieve the key to be used for the decryption.
        Key DragonLikeKey = null;
        try {
            DragonLikeKey = keyStore.getKey("BookWyrm", null);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        //Do something with getting the/a cipher or something.
        Cipher c = null;
        try {
            c = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        //And then initiating the cipher, so it can be used.
        try {
            assert c != null;
            c.init(Cipher.DECRYPT_MODE, DragonLikeKey, new GCMParameterSpec(128, codeMagic.getBytes()));
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
        }
        //Decrypt the password!
        byte[] truePass = null;
        try {
            truePass = c.doFinal(Base64.decode(pass, Base64.DEFAULT));
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        //Convert the decrypted password back to a string.
        String passw = new String(truePass, StandardCharsets.UTF_8);
        //String wacht = passw.replaceAll("'", "\\\\'");

        //A webviewclient thing is needed for some stuff. To automatically log in, the credentials are put in the form by the javascript that is loaded once the page is fully loaded. Then it is automatically submitted if the current page is the login page.
        myWebView.setWebViewClient(new MyWebViewClient(){
            public void onPageFinished(WebView view, String url) {
                LoadIndicator.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                view.loadUrl("javascript:(function() { " +
                        "if (document.querySelectorAll(\"[data-modal-open]\")[0]) {" +
                            "let ISBN_Button = document.querySelectorAll(\"[data-modal-open]\")[0];" +
                            "ISBN_Button.replaceWith(ISBN_Button.cloneNode(true));" +
                            "document.querySelectorAll(\"[data-modal-open]\")[0].addEventListener('click', () => {" +
                                "scan.performClick(this);" +
                            "});" +
                        "} else {" +
                            "let ISBN = document.createElement(\"div\");" +
                            "ISBN.class = 'control';" +
                            "ISBN.innerHTML = '<button class=\"button\" type=\"button\" onclick=\"scan.performClick(this)\"><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\" aria-hidden=\"true\"><path fill=\"none\" d=\"M0 0h24v24H0z\"/><path d=\"M4 5v14h16V5H4zM3 3h18a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zm3 4h3v10H6V7zm4 0h2v10h-2V7zm3 0h1v10h-1V7zm2 0h3v10h-3V7z\"/></svg><span class=\"is-sr-only\">Search</span></button>';" +
                            "nav = document.getElementsByClassName(\"field has-addons\")[0];" +
                            "nav.appendChild(ISBN);" +
                            "}" +
                        ";})()"); //This lines replace the ISBN-scan button event listener with one that points to the on-device scanning implementation, if it is available on the instance. If not, the button is added.

            }
        });
            CookieManager oven = CookieManager.getInstance();
            String koek = oven.getCookie("https://" + server);
            if (koek != null) {
                if (koek.indexOf("sessionid") != -1) {
                    myWebView.loadUrl("https://" + server);
                } else {
                    //This should get the login page, retreive the csrf-middlewaretoken, and then log the user in using a POST-request.
                    try {
                        getMiddleWareTokenAndLogIn(server, name, passw);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                //This should get the login page, retreive the csrf-middlewaretoken, and then log the user in using a POST-request.
                try {
                    getMiddleWareTokenAndLogIn(server, name, passw);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


    }

    public void getMiddleWareTokenAndLogIn(String server, String name, String passw) throws IOException {
        //Het idee is dat deze functie de loginpagina van de server laadt en dan de 'csrfmiddlewaretoken' uit het inlogformulier haalt,
        //Zodat dat dan gebruikt kan worden bij het inloggen.
        //Becuase network operations cannot be done on the main/ui thread, create a new thread for this complete function. Yay!
        Thread draadje = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //Load the login page, and do not forget to take some cookies.
                    Security.insertProviderAt(Conscrypt.newProvider(), 1);
                    String speculaas = "";
                    String speculaasBeslag = "";
                    //The login page loading is done using OkHttpClient.
                    OkHttpClient client = new OkHttpClient();
                    Request aanvraag = new Request.Builder()
                            .url("https://" + server + "/")
                            .header("User-Agent", getString(R.string.gebruikersagent))
                            .build();
                    //Get an answer!
                    try (Response antwoord = client.newCall(aanvraag).execute()) {
                        if (!antwoord.isSuccessful()) throw new IOException("Unexpected code " + antwoord);
                        //Search the headers for the 'set-cookie' header so we can eat a cookie!
                        Headers cenna = antwoord.headers();
                        for (int i = 0; i < cenna.size(); i++) {
                            if (cenna.name(i).equals("set-cookie")) {
                                speculaas = cenna.value(i);
                                speculaasBeslag = speculaas.split(";")[0];
                            }
                        }
                        //And then get the HTML body.
                        assert antwoord.body() != null;
                        String zooi = antwoord.body().string();
                        //Very easy to get the token by taking the text that it is preceded by in the raw html as the regex for a split() function!
                        String[] opgebroken = zooi.split("name=\"csrfmiddlewaretoken\" value=\"");
                        //For that gives as second element the token, followed by all the following html code. Then strip that code off, using the immediately following characters as regex.
                        String[] breukjes = opgebroken[1].split("\">");
                        //Of course, the token is then the first element in our array.
                        String token = breukjes[0];
                        //Log.d("botbreuk", token);
                        String gegevens = null;
                        //And then set the data string up for use in the POST request, with the csrf middleware token, the username, and the password.
                        try {
                            gegevens = "csrfmiddlewaretoken=" + URLEncoder.encode(token, "UTF-8") + "&localname=" + URLEncoder.encode(name, "UTF-8") + "&password=" + URLEncoder.encode(passw, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException(e);
                        }
                        RequestBody keurslijf = new FormBody.Builder()
                                .add("csrfmiddlewaretoken", token)
                                .add("localname", name)
                                .add("password", passw)
                                .build();

                        String finalGegevens = gegevens;
                        //Log.d("gegevens", finalGegevens);
                        //Log.d("beslag", speculaasBeslag);
                        String finalSpeculaas = speculaas;
                        //Log in using a POST request, and shove the resulting web-page into a public string. (putje)
                        //For this, we have to give the log-in function the servername, the request body, and the value of the csrf cookie.
                        logInAndGetHTML(server, keurslijf, speculaasBeslag);
                        //Then we have to run a bit of code on the main (UI) thread. To be able to work with the webview...
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //First we have to get the cookie manager of the webview, so we can hand it the csrf cookie.
                                //Without being fed the correct csrf cookie, the Wyrm will refuse our request. The wyrm is a very picky eater!
                                CookieManager oven = CookieManager.getInstance();
                                //Bake the cookie into the webview.
                                oven.setCookie("https://" + server, finalSpeculaas);
                                //And bake the session cookie as well.
                                oven.setCookie("https://" + server, sessie);
                                //And then finally it is time to load everything into the webview.
                                myWebView.loadDataWithBaseURL("https://" + server, putje, null, null, "https://" + server + "/login");
                            }
                        });

                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        //^Here ends all that new Thread() code.
        //⇓Run all the code in the thread.
        draadje.start();
    }
    public void logInAndGetHTML(String server, RequestBody lichaam, String speculoos) throws IOException {
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
        //Create a client using CookieMonster, so we can retrieve cookies from the redirect after sending the log-in data.
        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new CookieMonster())
                .build();
        //Create the POST request.
        Request verzoek = new Request.Builder()
                .url("https://" + server + "/login?next=/")
                .header("User-Agent", getString(R.string.gebruikersagent))
                .addHeader("origin", "https://" + server)
                .addHeader("cookie", speculoos)
                .post(lichaam)
                .build();
        //And then get the response.
        try (Response reactie = client.newCall(verzoek).execute()) {
            if (!reactie.isSuccessful())
                throw new IOException("Unexpected code " + reactie);
            assert reactie.body() != null;
            //Shove the response body into the public string 'putje', to be used for sending the body to the webview.
            putje = reactie.body().string();
        }
    }
    private final ActivityResultLauncher<ScanOptions> barcodeLanceerder = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() == null) {
                    Toast.makeText(StartActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(StartActivity.this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
                    myWebView.loadUrl("Javascript:(function() {" +
                            "try {" +
                            "document.getElementById('tour-search').value = " + result.getContents() + ";" +
                            "} catch {" +
                            "document.getElementById('search_input').value = " + result.getContents() + ";" +
                            "}" +
                            "document.getElementsByTagName('form')[0].submit();" +
                            ";})()");
                    LoadIndicator.setVisibility(View.VISIBLE);
                }
            });

    public void ScanBarCode() {
        String permission = Manifest.permission.CAMERA;
        int grant = ContextCompat.checkSelfPermission(StartActivity.this, permission);
        if (grant != PackageManager.PERMISSION_GRANTED) {
            String[] permission_list = new String[1];
            permission_list[0] = permission;
            ActivityCompat.requestPermissions(StartActivity.this, permission_list, 1);
        }
        ScanOptions eisen = new ScanOptions();
        eisen.setDesiredBarcodeFormats(ScanOptions.EAN_13);
        eisen.setBeepEnabled(true);
        eisen.setCameraId(0);
        eisen.setPrompt("SCAN ISBN");
        eisen.setBarcodeImageEnabled(false);
        barcodeLanceerder.launch(eisen);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyUp(keyCode, event);
    }
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }
    final class CookieMonster implements Interceptor {
        @Override public Response intercept(Interceptor.Chain chain) throws IOException {
            //Om ingelogd te blijven moeten we het sessiekoekje aan kunnen bieden.
            //Die moeten we dan wel eerst uit het koekblik pakken!
            Request eersteVerzoek = chain.request();
            //Eerst moeten we controleren of er al een sessiekoekje is. Als dat niet zo is, dan is dit het echte eerste verzoek.
            if (sessie.isEmpty()) {
                //In dat geval halen we de reactie op om het koekje te kunnen pakken!
                Response eersteReactie = chain.proceed(chain.request());
                Headers hoofden = eersteReactie.headers();
                for (int i = 0; i < hoofden.size(); i++) {
                    if (hoofden.name(i).equals("set-cookie") && hoofden.value(i).startsWith("session")) {
                        sessie = hoofden.value(i);
                    }
                }
                //Nadat we het koekje hebben moet de reactie doorgebriefd worden aan de 'client',
                //die dan het volgende verzoek zal gaan doen vanwege de 302-redirect bij het inloggen.
                return eersteReactie;
            }
            //Het koekje is er! Hoera!
            //Het nieuwe verzoek moet wel met het sessiekoekje verzonden worden, anders zijn we alsnog niet ingelogd!
            Request nieuwVerzoek = eersteVerzoek.newBuilder()
                    .addHeader("cookie", sessie)
                    .build();
            return chain.proceed(nieuwVerzoek);
        }
    }
    //Here is code to make sure that links of the bookwyrm server are handled within the webview client, instead of having it open in the default browser.
    //Yes, I used the web for this too.
    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            SharedPreferences sharedPref = StartActivity.this.getSharedPreferences(getString(R.string.server), Context.MODE_PRIVATE);
            String defaultValue = "none";
            String server = sharedPref.getString(getString(R.string.server), defaultValue);
            if (server.equals(request.getUrl().getHost())) {
                //If the server is the same as the bookwyrm, load it in the webview.
                return false;
            }
            // Otherwise, it should go to the default browser instead.
            Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            LoadIndicator.setVisibility(View.VISIBLE);
        }
    }
}