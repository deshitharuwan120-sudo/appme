package lk.droidart.nayapotha;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.telephony.SmsManager;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net/";
    private static final int SMS_PERMISSION=10, EXPORT=20, IMPORT=21;
    private WebView web;
    private SharedPreferences prefs;
    private boolean unlocked=false, pinShowing=false, pageLoaded=false;
    private String pendingPhone, pendingMessage, pendingBackup, importedBackup;
    private final Set<BroadcastReceiver> receivers=new HashSet<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(0xff123e35);
        getWindow().setNavigationBarColor(0xfff5f5ef);
        prefs=getSharedPreferences("security",MODE_PRIVATE);
        web=new WebView(this); web.setVisibility(View.INVISIBLE); setContentView(web);
        web.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();
        });
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView v,String u,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("හරි",(d,w)->result.confirm()).setCancelable(false).show();return true;}
            @Override public boolean onJsConfirm(WebView v,String u,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("හරි",(d,w)->result.confirm()).setNegativeButton("අවලංගු කරන්න",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return !request.getUrl().toString().equals(ORIGIN+"index.html");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if(!"appassets.androidplatform.net".equals(uri.getHost())||!"https".equals(uri.getScheme()))return blocked();
                if(!Arrays.asList("/index.html","/app.js").contains(path))return blocked();
                try{return new WebResourceResponse(path.endsWith(".js")?"application/javascript":"text/html","UTF-8",getAssets().open(path.substring(1)));}catch(IOException e){return blocked();}
            }
            @Override public void onPageFinished(WebView view,String url){pageLoaded=true;deliverImport();}
        });
        web.addJavascriptInterface(new Bridge(),"AndroidApp");
        web.loadUrl(ORIGIN+"index.html");
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    @Override protected void onResume(){super.onResume();if(!unlocked&&!pinShowing)showPin();}
    @Override protected void onStop(){super.onStop();unlocked=false;web.setVisibility(View.INVISIBLE);}
    private byte[] hash(String pin,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,100000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private void showPin(){
        pinShowing=true;boolean setup=!prefs.contains("pin");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(40,12,40,12);
        EditText first=new EditText(this);first.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);first.setHint("PIN (අංක 4–6)");box.addView(first);
        EditText second=new EditText(this);if(setup){second.setInputType(first.getInputType());second.setHint("PIN නැවත ඇතුළත් කරන්න");box.addView(second);}
        TextView note=new TextView(this);note.setText(setup?"PIN එක මතක තබාගන්න. PIN අමතක වුණොත් app දත්ත මැකීමට සිදු වේ; backup එකක් තබාගන්න.":"ඔබේ ණය පොත විවෘත කිරීමට PIN ඇතුළත් කරන්න.");box.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(setup?"PIN එකක් සකස් කරන්න":"ණය පොත • Login").setView(box).setPositiveButton(setup?"සුරකින්න":"විවෘත කරන්න",null).setNegativeButton("පිටවන්න",(d,w)->{pinShowing=false;finish();}).setCancelable(false).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long until=prefs.getLong("blockedUntil",0);if(System.currentTimeMillis()<until){note.setText("තත්පර "+((until-System.currentTimeMillis())/1000+1)+" කින් නැවත උත්සාහ කරන්න.");return;}
            String pin=first.getText().toString();if(!pin.matches("[0-9]{4,6}")){first.setError("අංක 4–6 ක් ඇතුළත් කරන්න");return;}
            if(setup&&!pin.equals(second.getText().toString())){second.setError("PIN දෙක ගැළපෙන්නේ නැහැ");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(()->{try{
                boolean ok; if(setup){byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);prefs.edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pin",Base64.encodeToString(hash(pin,salt),Base64.NO_WRAP)).commit();ok=true;}
                else ok=MessageDigest.isEqual(hash(pin,Base64.decode(prefs.getString("salt",""),Base64.NO_WRAP)),Base64.decode(prefs.getString("pin",""),Base64.NO_WRAP));
                runOnUiThread(()->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);if(ok){prefs.edit().remove("attempts").remove("blockedUntil").apply();unlocked=true;pinShowing=false;web.setVisibility(View.VISIBLE);dialog.dismiss();deliverImport();}else{int attempts=prefs.getInt("attempts",0)+1;prefs.edit().putInt("attempts",attempts%5).putLong("blockedUntil",attempts>=5?System.currentTimeMillis()+30000:0).apply();first.setText("");first.setError("PIN වැරදියි");}});
            }catch(Exception ex){runOnUiThread(()->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);note.setText("Login සකස් කිරීමට නොහැක. නැවත උත්සාහ කරන්න.");});}}).start();
        }));dialog.show();
    }
    private void tell(String message){if(pageLoaded)web.evaluateJavascript("notify("+JSONObject.quote(message)+")",null);else Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    public class Bridge {
        @JavascriptInterface public void sendSms(String phone,String message){runOnUiThread(()->{
            if(!unlocked)return;if(phone==null||!phone.matches("\\+?[0-9]{7,15}")||message==null||message.trim().isEmpty()||message.length()>1200){tell("අංකය සහ පණිවිඩය පරීක්ෂා කරන්න.");return;}
            if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)){tell("මේ device එකෙන් SIM SMS යවන්න බැහැ.");return;}
            if(pendingMessage!=null){tell("කලින් SMS request එක සම්පූර්ණ කරන්න.");return;}
            new AlertDialog.Builder(MainActivity.this).setTitle("SMS එක යවන්නද?").setMessage(phone+"\n\n"+message+"\n\nඔබේ SIM එකෙන් යවයි. දිගු / සිංහල SMS කොටස් කිහිපයකට බෙදී ගාස්තු අදාළ විය හැක.").setPositiveButton("යවන්න",(d,w)->{pendingPhone=phone;pendingMessage=message;if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.SEND_SMS},SMS_PERMISSION);else sendPending();}).setNegativeButton("අවලංගු කරන්න",null).show();
        });}
        @JavascriptInterface public void exportBackup(String json){runOnUiThread(()->{if(!unlocked||json==null||json.length()>10000000)return;pendingBackup=json;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NayaPotha-backup.json");try{startActivityForResult(i,EXPORT);}catch(ActivityNotFoundException e){pendingBackup=null;tell("File picker එකක් අවශ්‍යයි.");}});}
        @JavascriptInterface public void importBackup(){runOnUiThread(()->{if(!unlocked)return;try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT);}catch(ActivityNotFoundException e){tell("File picker එකක් අවශ්‍යයි.");}});}
        @JavascriptInterface public void copy(String message){runOnUiThread(()->{if(!unlocked)return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SMS",message));tell("පණිවිඩය Copy කළා");});}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==SMS_PERMISSION){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)sendPending();else{pendingMessage=null;pendingPhone=null;tell("SMS permission ලැබුණේ නැහැ. SMS යැව්වේ නැහැ.");}}}
    @SuppressWarnings("deprecation") private void sendPending(){
        if(pendingMessage==null)return;
        String phone=pendingPhone,message=pendingMessage;pendingPhone=null;pendingMessage=null;
        try{
            int subscription=android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId();
            if(!android.telephony.SubscriptionManager.isValidSubscriptionId(subscription)){tell("Phone Settings වලින් SMS සඳහා default SIM එකක් තෝරන්න. SMS යැව්වේ නැහැ.");return;}
            SmsManager manager=SmsManager.getSmsManagerForSubscriptionId(subscription);ArrayList<String> parts=manager.divideMessage(message);
            String action=getPackageName()+".SMS_SENT."+UUID.randomUUID();int count=parts.size();final int[] remaining={count};final boolean[] failed={false};
            BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(getResultCode()!=Activity.RESULT_OK)failed[0]=true;if(--remaining[0]<=0){unregisterReceiver(this);receivers.remove(this);tell(failed[0]?"SMS යැවීම සම්පූර්ණ වුණේ නැහැ. නැවත යවන්න කලින් Messages බලන්න.":"SMS යැව්වා. ලබන්නාට ලැබීම තවම තහවුරු කර නැහැ.");}}};
            if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(action),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(action));receivers.add(receiver);
            ArrayList<PendingIntent> sent=new ArrayList<>();for(int n=0;n<count;n++)sent.add(PendingIntent.getBroadcast(this,n,new Intent(action).setPackage(getPackageName()),PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE));
            if(count==1)manager.sendTextMessage(phone,null,message,sent.get(0),null);else manager.sendMultipartTextMessage(phone,null,parts,sent,null);
            tell("SMS යවමින්…");
            new Handler(Looper.getMainLooper()).postDelayed(()->{if(receivers.remove(receiver)){try{unregisterReceiver(receiver);}catch(Exception ignored){}tell("SMS තත්ත්වය තහවුරු කළ නොහැක. නැවත යවන්න කලින් Messages බලන්න.");}},60000);
        }catch(Exception e){tell("SMS යවන්න බැරි වුණා. SIM, signal සහ permission පරීක්ෂා කරන්න.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null){pendingBackup=null;return;}Uri uri=intent.getData();if(uri==null)return;
        if(request==EXPORT&&pendingBackup!=null){String json=pendingBackup;pendingBackup=null;try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(json.getBytes(StandardCharsets.UTF_8));tell("Backup එක සුරැකුවා");}catch(Exception e){tell("Backup සුරැකීම අසාර්ථකයි.");}}
        if(request==IMPORT){try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>10000000)throw new IOException();out.write(b,0,n);}importedBackup=out.toString("UTF-8");deliverImport();}catch(Exception e){tell("Backup කියවීමට නොහැක.");}}
    }
    private void deliverImport(){if(unlocked&&pageLoaded&&importedBackup!=null){String raw=importedBackup;importedBackup=null;web.evaluateJavascript("restoreNative("+JSONObject.quote(raw)+")",null);}}
    @Override public void onBackPressed(){web.evaluateJavascript("(()=>{const d=[...document.querySelectorAll('dialog[open]')].pop();if(d){d.close();return true}return false})()",value->{if(!"true".equals(value))finish();});}
    @Override protected void onDestroy(){for(BroadcastReceiver r:new HashSet<>(receivers)){try{unregisterReceiver(r);}catch(Exception ignored){}}receivers.clear();web.removeJavascriptInterface("AndroidApp");web.destroy();super.onDestroy();}
}
