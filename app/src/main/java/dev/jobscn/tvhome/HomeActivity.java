package dev.jobscn.tvhome;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.net.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.text.DateFormat;
import java.util.*;

/** Native, offline-first launcher. Never modifies another app or claims VPN ownership. */
public final class HomeActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private AppRepository repository; private MediaBridge media; private SharedPreferences prefs;
 private final List<AppEntry> apps=new ArrayList<>(),shown=new ArrayList<>();
 private final List<String> favorites=new ArrayList<>(),order=new ArrayList<>();
 private GridView grid; private AppAdapter adapter; private TextView clock,date,mediaTitle,mediaArtist;private int columns;
 private LinearLayout mediaActions; private Button vpnButton; private boolean all,started,loaded;
 private ConnectivityManager network;private boolean networkRegistered;
 private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){vpnChanged();}public void onLost(Network n){vpnChanged();}public void onCapabilitiesChanged(Network n,NetworkCapabilities c){vpnChanged();}};
 private void vpnChanged(){handler.post(()->{if(started)refreshVpn();});}
 private String lastFocus=""; private Button primaryMedia,allButton; private LinearLayout root;
 private final Runnable tick=new Runnable(){public void run(){updateClock();if(started)handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
 private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent i){repository.clear();scan();}};
 private final BroadcastReceiver time=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updateClock();}};
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  prefs=getSharedPreferences("atrium",MODE_PRIVATE);readList("favorites",favorites);readList("order",order);
  repository=new AppRepository(this);media=new MediaBridge(this,this::renderMedia);build();scan();
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xffeef4e8);return t;}
 GradientDrawable surface(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
 void focusStyle(View v,int color){v.setBackground(surface(color));v.setOnFocusChangeListener((view,on)->{GradientDrawable d=surface(on?0xff456453:color);if(on)d.setStroke(dp(3),0xffe4f2cb);view.setBackground(d);});}
 Button button(String label,Runnable action){Button b=new Button(this);b.setId(View.generateViewId());b.setText(label);b.setTextSize(15);b.setTextColor(0xfff0f4e9);b.setAllCaps(false);b.setMinHeight(dp(46));b.setPadding(dp(15),dp(5),dp(15),dp(5));focusStyle(b,0xff294439);b.setOnClickListener(v->action.run());return b;}
 void addRow(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.setMargins(0,0,dp(12),0);row.addView(v,p);}
 void build(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(42),dp(24),dp(42),dp(24));
  applyTheme();setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
  TextView brand=text(getString(dev.jobscn.tvhome.R.string.app_name),22);brand.setTypeface(null,Typeface.BOLD);addRow(header,brand);
  addRow(header,button("常用",()->{all=false;filter();}));allButton=button("全部应用",()->{all=true;filter();grid.requestFocus();});addRow(header,allButton);
  View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));addRow(header,button("设置",this::settings));
  LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(132));hp.setMargins(0,dp(12),0,dp(18));root.addView(hero,hp);
  LinearLayout timeBlock=new LinearLayout(this);timeBlock.setOrientation(LinearLayout.VERTICAL);clock=text("",56);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));date=text("",14);date.setTextColor(0xffa8babd);timeBlock.addView(clock);timeBlock.addView(date);hero.addView(timeBlock,new LinearLayout.LayoutParams(0,-2,.38f));
  LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(22),dp(12),dp(22),dp(12));card.setBackground(surface(0xff203d34));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(124),.62f);hero.addView(card,cp);
  LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);mediaTitle=text("播放器",20);mediaArtist=text("",13);mediaTitle.setSingleLine(true);mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);mediaArtist.setSingleLine(true);mediaArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);mediaArtist.setTextColor(0xffb8c8ba);labels.addView(mediaTitle);labels.addView(mediaArtist);card.addView(labels,new LinearLayout.LayoutParams(-1,0,1));
  mediaActions=new LinearLayout(this);mediaActions.setGravity(Gravity.END);card.addView(mediaActions,new LinearLayout.LayoutParams(-1,dp(48)));
  grid=new GridView(this);grid.setId(View.generateViewId());columns=getResources().getConfiguration().screenWidthDp>=850?6:4;grid.setNumColumns(columns);grid.setHorizontalSpacing(dp(16));grid.setVerticalSpacing(dp(14));grid.setClipToPadding(false);grid.setPadding(dp(4),dp(4),dp(4),dp(4));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);
  adapter=new AppAdapter();grid.setAdapter(adapter);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
  grid.setOnItemClickListener((p,v,pos,id)->launch(shown.get(pos)));grid.setOnItemLongClickListener((p,v,pos,id)->{organize(shown.get(pos));return true;});
  grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos<shown.size())lastFocus=shown.get(pos).key();}public void onNothingSelected(AdapterView<?> p){}});
  grid.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&key==KeyEvent.KEYCODE_MENU){int pos=grid.getSelectedItemPosition();if(pos>=0&&pos<shown.size())organize(shown.get(pos));return true;}return false;});
  LinearLayout quick=new LinearLayout(this);quick.setPadding(0,dp(16),0,0);root.addView(quick);vpnButton=button("VPN",this::vpn);addQuick(quick,vpnButton);addQuick(quick,button("小米桌面",this::xiaomi));addQuick(quick,button("系统设置",()->safeStart(new Intent(Settings.ACTION_SETTINGS))));renderMedia();
 }
 void addQuick(LinearLayout row,View button){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(0,0,dp(12),0);row.addView(button,p);}
 void scan(){repository.scan(list->{apps.clear();apps.addAll(list);if(!prefs.contains("favorites")){for(int i=0;i<Math.min(6,apps.size());i++)favorites.add(apps.get(i).key());saveList("favorites",favorites);}loaded=true;filter();});}
 void filter(){shown.clear();for(AppEntry a:apps)if(all||favorites.contains(a.key()))shown.add(a);shown.sort((a,b)->Integer.compare(rank(a),rank(b)));adapter.notifyDataSetChanged();int target=0;for(int i=0;i<shown.size();i++)if(shown.get(i).key().equals(lastFocus))target=i;if(!shown.isEmpty())grid.setSelection(target);}
 int rank(AppEntry a){int n=order.indexOf(a.key());return n<0?100000+apps.indexOf(a):n;}
 void launch(AppEntry app){lastFocus=app.key();safeStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));}
 void safeStart(Intent i){try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){notice("此入口在当前设备不可用");}}
 void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 void organize(AppEntry a){String[] options={favorites.contains(a.key())?"取消收藏":"加入常用","向前移动","向后移动"};new AlertDialog.Builder(this).setTitle(a.title).setItems(options,(d,n)->{if(n==0){if(!favorites.remove(a.key()))favorites.add(a.key());saveList("favorites",favorites);}else{ArrayList<String> keys=new ArrayList<>();for(AppEntry x:shown)keys.add(x.key());int at=keys.indexOf(a.key()),to=at+(n==1?-1:1);if(to>=0&&to<keys.size()){Collections.swap(keys,at,to);order.removeAll(keys);order.addAll(0,keys);saveList("order",order);}}filter();grid.requestFocus();}).show();}
 void readList(String key,List<String> list){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){String s=a.optString(i);if(ComponentName.unflattenFromString(s)!=null&&!list.contains(s))list.add(s);}}catch(Exception ignored){}}
 void saveList(String key,List<String> list){prefs.edit().putString(key,new JSONArray(list).toString()).apply();}
 void updateClock(){clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));date.setText(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));}
 @Override protected void onStart(){super.onStart();started=true;IntentFilter pf=new IntentFilter();pf.addAction(Intent.ACTION_PACKAGE_ADDED);pf.addAction(Intent.ACTION_PACKAGE_REMOVED);pf.addAction(Intent.ACTION_PACKAGE_CHANGED);pf.addDataScheme("package");registerReceiver(packages,pf);IntentFilter tf=new IntentFilter(Intent.ACTION_TIME_CHANGED);tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);registerReceiver(time,tf);tick.run();media.start();refreshVpn();try{network=getSystemService(ConnectivityManager.class);if(network!=null){network.registerNetworkCallback(new NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networkCallback);networkRegistered=true;}}catch(RuntimeException ignored){}if(loaded)scan();}
 @Override protected void onResume(){super.onResume();if(media!=null)renderMedia();if(vpnButton!=null)refreshVpn();}
 @Override protected void onStop(){started=false;handler.removeCallbacks(tick);if(networkRegistered){try{network.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}networkRegistered=false;}unregisterReceiver(packages);unregisterReceiver(time);media.stop();super.onStop();}
 @Override protected void onDestroy(){repository.close();super.onDestroy();}
 @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_BACKGROUND||level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL)repository.clear();}
 @Override public void onBackPressed(){if(all){all=false;filter();}else grid.requestFocus();}
 @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){if(grid.hasFocus()&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&grid.getSelectedItemPosition()<columns){primaryMedia.requestFocus();return true;}if(mediaActions.hasFocus()&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||e.getKeyCode()==KeyEvent.KEYCODE_BACK)){if(shown.isEmpty())allButton.requestFocus();else grid.requestFocus();return true;}}if(e.getRepeatCount()>0&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN)return true;return super.dispatchKeyEvent(e);}
 void renderMedia(){if(mediaActions==null)return;boolean focus=mediaActions.hasFocus();Object focusTag=getCurrentFocus()==null?null:getCurrentFocus().getTag();MediaBridge.Snapshot s=media.snapshot();mediaActions.removeAllViews();
  if(s.live){prefs.edit().putString("lastMedia",s.packageName).apply();mediaTitle.setText(s.title.isEmpty()?"播放器":s.title);mediaArtist.setText((s.artist.isEmpty()?"":s.artist+" · ")+packageLabel(s.packageName));
   if(s.canPrevious)addMedia("previous","|◀",()->{if(!media.previous())notice("此操作暂不可用");});
   if(s.canPlayPause){primaryMedia=button(s.playing?"暂停":"播放",()->{if(!media.playPause())notice("会话已失效");});addRow(mediaActions,primaryMedia);}else{primaryMedia=button("打开",()->openPackage(s.packageName));addRow(mediaActions,primaryMedia);}
   if(s.canNext)addMedia("next","▶|",()->{if(!media.next())notice("此操作暂不可用");});
  }else{String last=prefs.getString("lastMedia","");mediaTitle.setText("播放器");mediaArtist.setText(last.isEmpty()?"":packageLabel(last));primaryMedia=button(last.isEmpty()?"选择播放器":"打开",()->{if(last.isEmpty())choosePlayer();else openPackage(last);});addRow(mediaActions,primaryMedia);}
  primaryMedia.setTag("primary");for(int n=0;n<mediaActions.getChildCount();n++){View control=mediaActions.getChildAt(n);control.setNextFocusLeftId(mediaActions.getChildAt(Math.max(0,n-1)).getId());control.setNextFocusRightId(mediaActions.getChildAt(Math.min(mediaActions.getChildCount()-1,n+1)).getId());control.setNextFocusUpId(allButton.getId());control.setNextFocusDownId(grid.getId());}allButton.setNextFocusDownId(primaryMedia.getId());if(focus){View previous=focusTag==null?null:mediaActions.findViewWithTag(focusTag);if(previous!=null)previous.requestFocus();else primaryMedia.requestFocus();}
 }
 void addMedia(String tag,String title,Runnable action){Button b=button(title,action);b.setTag(tag);b.setContentDescription(tag.equals("previous")?"上一首":"下一首");addRow(mediaActions,b);}
 String packageLabel(String p){for(AppEntry a:apps)if(a.component.getPackageName().equals(p))return a.title;try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){return "上次使用的播放器";}}
 void openPackage(String p){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(p);if(i==null)i=getPackageManager().getLaunchIntentForPackage(p);if(i==null){notice("应用不可用，请重新选择播放器");choosePlayer();}else safeStart(i);}
 void choosePlayer(){choosePlayer(false);}
 void choosePlayer(boolean returnSettings){final List<AppEntry> choices=new ArrayList<>(apps);if(choices.isEmpty()){notice("暂无可启动应用");return;}String[] names=new String[choices.size()];for(int i=0;i<choices.size();i++)names[i]=choices.get(i).title;new AlertDialog.Builder(this).setTitle("选择播放器").setItems(names,(d,n)->{prefs.edit().putString("lastMedia",choices.get(n).component.getPackageName()).apply();renderMedia();if(returnSettings)settings();else primaryMedia.requestFocus();}).setOnCancelListener(d->{if(returnSettings)settings();else primaryMedia.requestFocus();}).show();}
 void refreshVpn(){VpnStatus status=VpnStatus.read(this);vpnButton.setText(status==VpnStatus.ACTIVE?"VPN · 已启用":status==VpnStatus.INACTIVE?"VPN · 未检测到":"VPN · 状态未知");}
 void vpn(){Intent i=VpnStatus.providerIntent(this);new AlertDialog.Builder(this).setTitle("VPN").setMessage("状态仅表示系统 VPN。当前提供者未开放已验证的直控接口，请在应用内启停。").setPositiveButton(i==null?"未安装 Sockstun":"打开 Sockstun",(d,n)->{if(i!=null)safeStart(i);}).setNegativeButton("返回",null).show();}
 void xiaomi(){safeStart(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.mitv.tvhome","com.mitv.tvhome.MainActivityUserMode")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 void applyTheme(){int n=prefs.getInt("theme",0);int[][] colors={{0xff223b3c,0xff101d23},{0xff44372d,0xff28221f},{0xff29334b,0xff111624}};root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors[Math.max(0,Math.min(2,n))]));}
 void themes(){new AlertDialog.Builder(this).setTitle("背景").setSingleChoiceItems(new String[]{"森林","暖砂","夜幕"},prefs.getInt("theme",0),(d,n)->{prefs.edit().putInt("theme",n).apply();applyTheme();d.dismiss();settings();}).setOnCancelListener(d->settings()).show();}
 void settings(){String[] labels={"媒体控制权限","选择播放器","设为默认桌面","更改默认桌面","背景","系统设置"};new AlertDialog.Builder(this).setTitle("设置").setItems(labels,(d,n)->{switch(n){case 0:Intent i=MediaBridge.accessSettings(this);if(i==null)notice("设备未提供媒体访问授权入口");else new AlertDialog.Builder(this).setTitle("媒体控制").setMessage("由你在系统页面授权通知访问，以读取活动媒体会话；不读取或保存通知内容。").setPositiveButton("打开设置",(x,y)->safeStart(i)).setNegativeButton("取消",(x,y)->settings()).setOnCancelListener(x->settings()).show();break;case 1:choosePlayer(true);break;case 2:requestHome();break;case 3:safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));break;case 4:themes();break;default:safeStart(new Intent(Settings.ACTION_SETTINGS));}}).show();}
 void requestHome(){if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)){if(rm.isRoleHeld(RoleManager.ROLE_HOME)){notice("已是默认桌面");return;}try{startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),10);return;}catch(Exception ignored){}}}safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}
 @Override protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(req==10&&Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);notice(rm!=null&&rm.isRoleHeld(RoleManager.ROLE_HOME)?"已选择为默认桌面；请用实体 Home 键验证":"未更改默认桌面");}}
 final class AppAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
  public View getView(int position,View reuse,android.view.ViewGroup parent){LinearLayout tile;ImageView icon;TextView label;
   if(reuse==null){tile=new LinearLayout(HomeActivity.this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(10),dp(6),dp(10),dp(6));icon=new ImageView(HomeActivity.this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);tile.addView(icon,new LinearLayout.LayoutParams(-1,dp(60)));label=text("",14);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(label);tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(94)));}else{tile=(LinearLayout)reuse;icon=(ImageView)tile.getChildAt(0);label=(TextView)tile.getChildAt(1);}
   AppEntry a=shown.get(position);tile.setTag(a.key());label.setText(a.title);icon.setImageDrawable(null);int color=new int[]{0xff405a4c,0xff40505f,0xff685344,0xff4c4e66}[Math.floorMod(a.component.getPackageName().hashCode(),4)];android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable selected=surface(color);selected.setStroke(dp(3),0xffedffd6);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},surface(color));tile.setBackground(states);final LinearLayout target=tile;repository.icon(a,dp(Math.max(64,(getResources().getConfiguration().screenWidthDp-92-16*(columns-1))/columns-20)),dp(60),bitmap->{if(a.key().equals(target.getTag()))icon.setImageBitmap(bitmap);});return tile;
  }
 }
}
