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
 private android.graphics.drawable.Drawable.ConstantState placeholderIcon;
 private AppRepository repository; private MediaBridge media; private SharedPreferences prefs;
 private final List<AppEntry> apps=new ArrayList<>(),shown=new ArrayList<>();
 private final List<String> favorites=new ArrayList<>(),order=new ArrayList<>();
 private GridView grid; private AppAdapter adapter; private TextView clock,date,mediaTitle,mediaArtist;private int columns;
 private TextView brand; private Typeface uiFont; private LinearLayout hero,quick; private Button homeButton,settingsButton; private Dialog activePanel; private LinearLayout mediaActions,mediaCard; private Button vpnButton; private boolean all,started,loaded;
 private ConnectivityManager network;private boolean networkRegistered;
 private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){vpnChanged();}public void onLost(Network n){vpnChanged();}public void onCapabilitiesChanged(Network n,NetworkCapabilities c){vpnChanged();}};
 private void vpnChanged(){handler.post(()->{if(started)refreshVpn();});}
 private String lastFocus=""; private Button primaryMedia,allButton; private LinearLayout root;
 private final Runnable tick=new Runnable(){public void run(){updateClock();if(started)handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
 private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent i){repository.clear();scan();}};
 private final BroadcastReceiver time=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updateClock();}};
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  uiFont=Typeface.createFromAsset(getAssets(),"fonts/atrium-sans.otf");
  prefs=getSharedPreferences("atrium",MODE_PRIVATE);readList("favorites",favorites);readList("order",order);
  placeholderIcon=getPackageManager().getDefaultActivityIcon().getConstantState();repository=new AppRepository(this);media=new MediaBridge(this,this::renderMedia);build();scan();
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTypeface(uiFont);t.setIncludeFontPadding(false);t.setTextSize(size);t.setTextColor(0xffeef4e8);return t;}
 GradientDrawable surface(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
 void focusStyle(View v,int color){v.setBackground(surface(color));v.setOnFocusChangeListener((view,on)->{GradientDrawable d=surface(on?0x183fe8b1:color);if(on)d.setStroke(dp(1.5f),0xffe4f2df);view.setBackground(d);view.animate().scaleX(on?1.025f:1).scaleY(on?1.025f:1).setDuration(120).start();});}
 Button button(String label,Runnable action){Button b=new Button(this);configureButton(b,label,action);return b;}
 void configureButton(Button b,String label,Runnable action){b.setId(View.generateViewId());b.setText(label);b.setTypeface(uiFont);b.setIncludeFontPadding(false);b.setTextSize(14);b.setTextColor(0xffe7eee7);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setStateListAnimator(null);b.setPadding(dp(14),dp(7),dp(14),dp(7));focusStyle(b,Color.TRANSPARENT);b.setOnClickListener(v->action.run());}
 void addRow(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(0,0,dp(12),0);row.addView(v,p);}
 void build(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(40),dp(22),dp(40),dp(22));applyTheme();setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
  brand=text("栖庭",23);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  homeButton=button("主页",()->switchPage(false));allButton=button("应用",()->switchPage(true));addRow(header,homeButton);addRow(header,allButton);
  View space=new View(this);header.addView(space,new LinearLayout.LayoutParams(0,1,1));settingsButton=button("设置",this::settings);addRow(header,settingsButton);
  hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(146));hp.setMargins(0,dp(16),0,dp(28));root.addView(hero,hp);
  LinearLayout timeBlock=new LinearLayout(this);timeBlock.setOrientation(LinearLayout.VERTICAL);clock=text("",59);date=text("",13);date.setTextColor(0xffb5c5ba);timeBlock.addView(clock);LinearLayout.LayoutParams dt=new LinearLayout.LayoutParams(-2,-2);dt.topMargin=dp(10);timeBlock.addView(date,dt);hero.addView(timeBlock,new LinearLayout.LayoutParams(0,-2,.48f));
  mediaCard=new LinearLayout(this);mediaCard.setOrientation(LinearLayout.HORIZONTAL);mediaCard.setGravity(Gravity.CENTER_VERTICAL);mediaCard.setMinimumHeight(dp(96));mediaCard.setPadding(dp(20),dp(16),dp(20),dp(12));mediaCard.setBackground(surface(0x0cffffff));hero.addView(mediaCard,new LinearLayout.LayoutParams(0,-2,.52f));
  mediaTitle=text("播放器",18);mediaArtist=text("",12);mediaTitle.setSingleLine(true);mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);mediaArtist.setSingleLine(true);mediaArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);mediaArtist.setTextColor(0xffb0c2b7);LinearLayout mediaLabels=new LinearLayout(this);mediaLabels.setOrientation(LinearLayout.VERTICAL);mediaLabels.addView(mediaTitle);LinearLayout.LayoutParams ma=new LinearLayout.LayoutParams(-1,-2);ma.topMargin=dp(7);mediaLabels.addView(mediaArtist,ma);mediaCard.addView(mediaLabels,new LinearLayout.LayoutParams(0,-2,1));
  mediaActions=new LinearLayout(this);mediaActions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ac=new LinearLayout.LayoutParams(-2,dp(46));ac.leftMargin=dp(10);mediaCard.addView(mediaActions,ac);
  grid=new GridView(this);grid.setId(View.generateViewId());columns=getResources().getConfiguration().screenWidthDp>=850?6:4;grid.setNumColumns(columns);grid.setHorizontalSpacing(dp(18));grid.setVerticalSpacing(dp(24));grid.setClipToPadding(false);grid.setPadding(dp(5),dp(8),dp(5),dp(8));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setVerticalScrollBarEnabled(false);
  adapter=new AppAdapter();grid.setAdapter(adapter);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
  grid.setOnItemClickListener((p,v,pos,id)->launch(shown.get(pos)));grid.setOnItemLongClickListener((p,v,pos,id)->{organize(shown.get(pos));return true;});
  grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos>=0&&pos<shown.size())lastFocus=shown.get(pos).key();}public void onNothingSelected(AdapterView<?> p){}});
  grid.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&key==KeyEvent.KEYCODE_MENU){int pos=grid.getSelectedItemPosition();if(pos>=0&&pos<shown.size())organize(shown.get(pos));return true;}return false;});
  quick=new LinearLayout(this);quick.setPadding(0,dp(12),0,0);root.addView(quick);vpnButton=button("VPN",this::vpn);addQuick(quick,vpnButton);addQuick(quick,button("小米桌面",this::xiaomi));addQuick(quick,button("系统设置",()->safeStart(new Intent(Settings.ACTION_SETTINGS))));renderMedia();updatePageChrome();
 }
 void switchPage(boolean value){all=value;updatePageChrome();filter();if(all&&!shown.isEmpty())grid.requestFocus();}
 void updatePageChrome(){brand.setVisibility(all?View.INVISIBLE:View.VISIBLE);settingsButton.setVisibility(all?View.INVISIBLE:View.VISIBLE);hero.setVisibility(all?View.GONE:View.VISIBLE);quick.setVisibility(all?View.GONE:View.VISIBLE);homeButton.setTextColor(all?0xff91a99b:0xffedf4e8);allButton.setTextColor(all?0xffedf4e8:0xff91a99b);allButton.setNextFocusDownId(all?grid.getId():primaryMedia.getId());homeButton.setNextFocusDownId(grid.getId());settingsButton.setNextFocusDownId(all?grid.getId():primaryMedia.getId());}
 void addQuick(LinearLayout row,View button){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(0,0,dp(12),0);row.addView(button,p);}
 void scan(){repository.scan(list->{apps.clear();apps.addAll(list);if(!prefs.contains("favorites")){for(int i=0;i<Math.min(6,apps.size());i++)favorites.add(apps.get(i).key());saveList("favorites",favorites);}loaded=true;filter();});}
 void filter(){shown.clear();for(AppEntry a:apps)if(all||favorites.contains(a.key()))shown.add(a);shown.sort((a,b)->Integer.compare(rank(a),rank(b)));adapter.notifyDataSetChanged();int target=0;for(int i=0;i<shown.size();i++)if(shown.get(i).key().equals(lastFocus))target=i;if(!shown.isEmpty())grid.setSelection(target);}
 int rank(AppEntry a){int n=order.indexOf(a.key());return n<0?100000+apps.indexOf(a):n;}
 void launch(AppEntry app){lastFocus=app.key();safeStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));}
 void safeStart(Intent i){try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){notice("此入口在当前设备不可用");}}
 void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 void organize(AppEntry a){String[] options={favorites.contains(a.key())?"取消收藏":"加入常用","向前移动","向后移动"};Runnable[] actions=new Runnable[3];for(int n=0;n<3;n++){final int choice=n;actions[n]=()->{if(choice==0){if(!favorites.remove(a.key()))favorites.add(a.key());saveList("favorites",favorites);}else{ArrayList<String> keys=new ArrayList<>();for(AppEntry x:shown)keys.add(x.key());int at=keys.indexOf(a.key()),to=at+(choice==1?-1:1);if(to>=0&&to<keys.size()){Collections.swap(keys,at,to);order.removeAll(keys);order.addAll(0,keys);saveList("order",order);}}filter();grid.requestFocus();};}panel(a.title,null,options,actions,null,0);}
 View panelOrigin;
 void panel(String title,String detail,String[] labels,Runnable[] actions,Runnable back,int focusIndex){
  if(activePanel==null)panelOrigin=getCurrentFocus();else{Dialog previous=activePanel;activePanel=null;previous.setOnDismissListener(null);previous.dismiss();}
  final Dialog d=new Dialog(this);activePanel=d;d.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(23),dp(24),dp(22));GradientDrawable panelBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff2a4039,0xff172b28});panelBackground.setCornerRadius(dp(20));box.setBackground(panelBackground);box.setClipToOutline(true);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(title,23);heading.addView(t,new LinearLayout.LayoutParams(0,-2,1));Button close=button("返回",()->{dismissPanel(d);if(back!=null)back.run();});close.setTextSize(12);heading.addView(close,new LinearLayout.LayoutParams(-2,dp(40)));box.addView(heading);
  ScrollView scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.setVerticalScrollBarEnabled(false);scroller.setClipToPadding(false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(23);box.addView(scroller,sp);
  LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(3),dp(3),dp(3),dp(8));scroller.addView(rows);
  if(detail!=null&&!detail.isEmpty()){TextView desc=text(detail,13);desc.setTextColor(0xffb1c3b7);desc.setLineSpacing(dp(4),1);LinearLayout.LayoutParams dt=new LinearLayout.LayoutParams(-1,-2);dt.bottomMargin=dp(21);rows.addView(desc,dt);}
  List<Button> buttons=new ArrayList<>();for(int n=0;n<labels.length;n++){final int at=n;Button row=button(labels[n],()->{dismissPanel(d);actions[at].run();});row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setSingleLine(false);row.setTextSize(16);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);row.setMinimumHeight(dp(52));rows.addView(row,rp);buttons.add(row);}
  for(int n=0;n<buttons.size();n++){Button row=buttons.get(n);row.setNextFocusUpId(n==0?close.getId():buttons.get(n-1).getId());row.setNextFocusDownId(buttons.get(Math.min(n+1,buttons.size()-1)).getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());}if(!buttons.isEmpty())close.setNextFocusDownId(buttons.get(0).getId());
  d.setContentView(box);d.setOnCancelListener(x->{dismissPanel(d);if(back!=null)back.run();});d.setOnDismissListener(x->{if(activePanel==d)activePanel=null;if(activePanel==null){if(panelOrigin!=null&&panelOrigin.isAttachedToWindow()&&panelOrigin.isShown())panelOrigin.requestFocus();else if(all)allButton.requestFocus();else primaryMedia.requestFocus();}});d.setOnKeyListener((x,key,e)->e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER));
  d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.width=Math.min(dp(370),getResources().getDisplayMetrics().widthPixels-dp(24));lp.height=getResources().getDisplayMetrics().heightPixels-dp(24);lp.x=dp(12);lp.dimAmount=.48f;w.setAttributes(lp);w.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());}if(!buttons.isEmpty())buttons.get(Math.max(0,Math.min(focusIndex,buttons.size()-1))).requestFocus();else close.requestFocus();
 }
 void dismissPanel(Dialog d){if(activePanel==d)activePanel=null;d.dismiss();}

 void readList(String key,List<String> list){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){String s=a.optString(i);if(ComponentName.unflattenFromString(s)!=null&&!list.contains(s))list.add(s);}}catch(Exception ignored){}}
 void saveList(String key,List<String> list){prefs.edit().putString(key,new JSONArray(list).toString()).apply();}
 void updateClock(){clock.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));date.setText(DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));}
 @Override protected void onStart(){super.onStart();started=true;IntentFilter pf=new IntentFilter();pf.addAction(Intent.ACTION_PACKAGE_ADDED);pf.addAction(Intent.ACTION_PACKAGE_REMOVED);pf.addAction(Intent.ACTION_PACKAGE_CHANGED);pf.addDataScheme("package");registerReceiver(packages,pf);IntentFilter tf=new IntentFilter(Intent.ACTION_TIME_CHANGED);tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);registerReceiver(time,tf);tick.run();media.start();refreshVpn();try{network=getSystemService(ConnectivityManager.class);if(network!=null){network.registerNetworkCallback(new NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networkCallback);networkRegistered=true;}}catch(RuntimeException ignored){}if(loaded)scan();}
 @Override protected void onResume(){super.onResume();if(media!=null)renderMedia();if(vpnButton!=null)refreshVpn();}
 @Override protected void onStop(){started=false;handler.removeCallbacks(tick);if(networkRegistered){try{network.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}networkRegistered=false;}unregisterReceiver(packages);unregisterReceiver(time);media.stop();super.onStop();}
 @Override protected void onDestroy(){if(activePanel!=null){activePanel.setOnDismissListener(null);activePanel.dismiss();activePanel=null;}repository.close();super.onDestroy();}
 @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_BACKGROUND||level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL)repository.clear();}
 @Override public void onBackPressed(){if(all){switchPage(false);allButton.requestFocus();}else grid.requestFocus();}
 @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){if(grid.hasFocus()&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&grid.getSelectedItemPosition()<columns){(all?allButton:primaryMedia).requestFocus();return true;}if(mediaActions.hasFocus()&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||e.getKeyCode()==KeyEvent.KEYCODE_BACK)){if(shown.isEmpty())allButton.requestFocus();else grid.requestFocus();return true;}}if(e.getRepeatCount()>0&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN)return true;return super.dispatchKeyEvent(e);}
 void renderMedia(){if(mediaActions==null)return;boolean focus=mediaActions.hasFocus();Object focusTag=getCurrentFocus()==null?null:getCurrentFocus().getTag();MediaBridge.Snapshot s=media.snapshot();mediaActions.removeAllViews();
  if(s.live){prefs.edit().putString("lastMedia",s.packageName).apply();mediaTitle.setText(s.title.isEmpty()?"播放器":s.title);mediaArtist.setText((s.artist.isEmpty()?"":s.artist+" · ")+packageLabel(s.packageName));
   if(s.canPrevious)addMedia("previous","上一首",()->{if(!media.previous())notice("此操作暂不可用");});
   if(s.canPlayPause){primaryMedia=mediaButton(s.playing?"pause":"play",s.playing?"暂停":"播放",()->{if(!media.playPause())notice("会话已失效");});addRow(mediaActions,primaryMedia);}else{primaryMedia=button("打开",()->openPackage(s.packageName));addRow(mediaActions,primaryMedia);}
   if(s.canNext)addMedia("next","下一首",()->{if(!media.next())notice("此操作暂不可用");});
  }else{String last=prefs.getString("lastMedia","");mediaTitle.setText("播放器");mediaArtist.setText(last.isEmpty()?"":packageLabel(last));primaryMedia=button(last.isEmpty()?"选择播放器":"打开",()->{if(last.isEmpty())choosePlayer();else openPackage(last);});addRow(mediaActions,primaryMedia);}
  mediaArtist.setVisibility(mediaArtist.getText().length()==0?View.GONE:View.VISIBLE);primaryMedia.setTag("primary");for(int n=0;n<mediaActions.getChildCount();n++){View control=mediaActions.getChildAt(n);control.setNextFocusLeftId(mediaActions.getChildAt(Math.max(0,n-1)).getId());control.setNextFocusRightId(mediaActions.getChildAt(Math.min(mediaActions.getChildCount()-1,n+1)).getId());control.setNextFocusUpId(allButton.getId());control.setNextFocusDownId(grid.getId());}allButton.setNextFocusDownId(all?grid.getId():primaryMedia.getId());settingsButton.setNextFocusDownId(all?grid.getId():primaryMedia.getId());if(focus){View previous=focusTag==null?null:mediaActions.findViewWithTag(focusTag);if(previous!=null)previous.requestFocus();else primaryMedia.requestFocus();}
 }
 void addMedia(String tag,String title,Runnable action){Button b=mediaButton(tag,title,action);b.setTag(tag);b.setContentDescription(tag.equals("previous")?"上一首":"下一首");addRow(mediaActions,b);}
 Button mediaButton(String kind,String label,Runnable action){Button b=new MediaButton(kind);configureButton(b,"",action);b.setContentDescription(label);b.setMinimumWidth(dp(42));return b;}
 final class MediaButton extends Button {final String kind;final android.graphics.Paint paint=new android.graphics.Paint(3);final android.graphics.Path path=new android.graphics.Path();MediaButton(String k){super(HomeActivity.this);kind=k;}@Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f-dp(10),getHeight()/2f-dp(10));canvas.scale(dp(20)/24f,dp(20)/24f);paint.setColor(0xffe5efdf);paint.setStrokeWidth(2);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStyle(android.graphics.Paint.Style.FILL);path.reset();if(kind.equals("pause")){canvas.drawRect(7,5,10,19,paint);canvas.drawRect(14,5,17,19,paint);}else if(kind.equals("previous")){canvas.drawRect(5,5,7,19,paint);path.moveTo(18,5);path.lineTo(8,12);path.lineTo(18,19);path.close();canvas.drawPath(path,paint);}else{if(kind.equals("next"))canvas.drawRect(18,5,20,19,paint);path.moveTo(7,5);path.lineTo(17,12);path.lineTo(7,19);path.close();canvas.drawPath(path,paint);}canvas.restore();}}
 String packageLabel(String p){for(AppEntry a:apps)if(a.component.getPackageName().equals(p))return a.title;try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){return "上次使用的播放器";}}
 void openPackage(String p){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(p);if(i==null)i=getPackageManager().getLaunchIntentForPackage(p);if(i==null){notice("应用不可用，请重新选择播放器");choosePlayer();}else safeStart(i);}
 void choosePlayer(){choosePlayer(false);}
 void choosePlayer(boolean returnSettings){final List<AppEntry> choices=new ArrayList<>(apps);if(choices.isEmpty()){notice("暂无可启动应用");if(returnSettings)settings(1);return;}String[] names=new String[choices.size()];Runnable[] actions=new Runnable[choices.size()];for(int n=0;n<choices.size();n++){final AppEntry a=choices.get(n);names[n]=a.title;actions[n]=()->{prefs.edit().putString("lastMedia",a.component.getPackageName()).apply();renderMedia();if(returnSettings)settings(1);else primaryMedia.requestFocus();};}panel("选择播放器",null,names,actions,returnSettings?()->settings(1):null,0);}

 void refreshVpn(){VpnStatus status=VpnStatus.read(this);vpnButton.setText(status==VpnStatus.ACTIVE?"VPN · 已启用":status==VpnStatus.INACTIVE?"VPN · 未检测到":"VPN · 状态未知");}
 void vpn(){Intent i=VpnStatus.providerIntent(this);VpnStatus status=VpnStatus.read(this);String name=status==VpnStatus.ACTIVE?"已启用":status==VpnStatus.INACTIVE?"未检测到连接":"状态未知";panel("VPN · "+name,"连接由提供者管理。",new String[]{i==null?"未安装 Sockstun":"打开 Sockstun"},new Runnable[]{()->{if(i!=null)safeStart(i);else notice("请先安装 Sockstun");}},null,0);}

 void xiaomi(){safeStart(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.mitv.tvhome","com.mitv.tvhome.MainActivityUserMode")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 void applyTheme(){int n=prefs.getInt("theme",0);int[][] colors={{0xff2e4b40,0xff0c191a},{0xff44372d,0xff28221f},{0xff29334b,0xff111624}};root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors[Math.max(0,Math.min(2,n))]));}
 void themes(){String[] names={"松林","暖砂","夜航"};Runnable[] actions=new Runnable[3];for(int n=0;n<3;n++){final int theme=n;actions[n]=()->{prefs.edit().putInt("theme",theme).apply();applyTheme();settings(0);};}panel("外观",null,names,actions,()->settings(0),prefs.getInt("theme",0));}
 void settings(){settings(0);}
 void settings(int selected){panel("设置",null,new String[]{"外观","播放器","媒体控制权限","设为默认桌面","更改默认桌面","系统设置"},new Runnable[]{this::themes,()->choosePlayer(true),()->{Intent i=MediaBridge.accessSettings(this);if(i==null){notice("设备未提供媒体访问授权入口");settings(2);}else panel("媒体控制","授权通知访问后可控制活动媒体会话。不读取或保存通知内容。",new String[]{"打开系统授权"},new Runnable[]{()->safeStart(i)},()->settings(2),0);},this::requestHome,()->safeStart(new Intent(Settings.ACTION_HOME_SETTINGS)),()->safeStart(new Intent(Settings.ACTION_SETTINGS))},null,selected);}

 void requestHome(){if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)){if(rm.isRoleHeld(RoleManager.ROLE_HOME)){notice("已是默认桌面");return;}try{startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),10);return;}catch(Exception ignored){}}}safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));}
 @Override protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(req==10&&Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);notice(rm!=null&&rm.isRoleHeld(RoleManager.ROLE_HOME)?"已设为默认桌面":"未更改默认桌面");}}
 final class AppAdapter extends BaseAdapter {
  public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
  public View getView(int position,View reuse,android.view.ViewGroup parent){LinearLayout tile;ImageView icon;TextView label;
   int artWidth=Math.max(64,(getResources().getConfiguration().screenWidthDp-90-18*(columns-1))/columns);
   int artHeight=Math.round(artWidth*9f/16f);
   if(reuse==null){tile=new LinearLayout(HomeActivity.this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);icon=new ImageView(HomeActivity.this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setDuplicateParentStateEnabled(true);icon.setClipToOutline(true);tile.addView(icon,new LinearLayout.LayoutParams(-1,dp(artHeight)));label=text("",14);label.setPadding(0,dp(8),0,dp(4));label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setHorizontallyScrolling(false);label.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));}else{tile=(LinearLayout)reuse;icon=(ImageView)tile.getChildAt(0);label=(TextView)tile.getChildAt(1);}
   // Bound text to the tile: singleLine horizontal scrolling can retain a million-pixel centered layout during GridView premeasurement.
   int labelHeight=(int)Math.ceil(label.getPaint().getFontMetrics().bottom-label.getPaint().getFontMetrics().top)+dp(12);label.setMinimumHeight(labelHeight);label.setLayoutParams(new LinearLayout.LayoutParams(dp(artWidth),labelHeight));label.forceLayout();tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(artHeight)+labelHeight));icon.getLayoutParams().height=dp(artHeight);
   AppEntry a=shown.get(position);tile.setTag(a.key());label.setText(a.title);if(placeholderIcon!=null)icon.setImageDrawable(placeholderIcon.newDrawable(getResources()));else icon.setImageResource(android.R.drawable.sym_def_app_icon);int color=new int[]{0xff405a4c,0xff40505f,0xff685344,0xff4c4e66}[Math.floorMod(a.component.getPackageName().hashCode(),4)];android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable selected=surface(Color.TRANSPARENT);selected.setStroke(dp(2),0xffedffd6);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},surface(Color.TRANSPARENT));icon.setBackground(surface(color));icon.setForeground(states);final LinearLayout target=tile;repository.icon(a,dp(artWidth),dp(artHeight),bitmap->{if(a.key().equals(target.getTag())&&bitmap!=null)icon.setImageBitmap(bitmap);});return tile;
  }
 }
}
