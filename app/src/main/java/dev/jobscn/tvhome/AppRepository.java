package dev.jobscn.tvhome;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.*;
import java.util.concurrent.*;

final class AppRepository {
 interface Result {void accept(List<AppEntry> apps);}
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final Handler main=new Handler(Looper.getMainLooper());
 final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
 private final Context context; private boolean closed;
 AppRepository(Context c){context=c.getApplicationContext();}
 void scan(Result result){worker.execute(()->{
  PackageManager pm=context.getPackageManager(); LinkedHashMap<String,AppEntry> byPackage=new LinkedHashMap<>();
  for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   List<ResolveInfo> resolved;try{resolved=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);}catch(RuntimeException e){continue;}
   for(ResolveInfo r:resolved){try{
    ActivityInfo a=r.activityInfo;
    if(a==null||!a.enabled||!a.exported||a.packageName.equals(context.getPackageName())||byPackage.containsKey(a.packageName))continue;
    String label="";try{CharSequence value=r.loadLabel(pm);if(value!=null)label=value.toString().trim();}catch(RuntimeException ignored){}
    if(label.isEmpty())try{CharSequence value=a.applicationInfo.loadLabel(pm);if(value!=null)label=value.toString().trim();}catch(RuntimeException ignored){}
    if(label.isEmpty())label=a.packageName;
    byPackage.put(a.packageName,new AppEntry(new ComponentName(a.packageName,a.name),label));
    }catch(RuntimeException badApp){}
   }
  }
  List<AppEntry> list=new ArrayList<>(byPackage.values()); list.sort((a,b)->a.title.compareToIgnoreCase(b.title));
  main.post(()->{if(!closed)result.accept(list);});
 });}
 void icon(AppEntry app,int width,int height,java.util.function.Consumer<Bitmap> done){
  String key=app.key()+":"+width+"x"+height; Bitmap hit=icons.get(key); if(hit!=null){done.accept(hit);return;}
  worker.execute(()->{Bitmap bitmap=null;try{
   ActivityInfo info=context.getPackageManager().getActivityInfo(app.component,0);
   Drawable d;try{d=info.loadBanner(context.getPackageManager());}catch(RuntimeException badBanner){d=null;} if(d==null)d=info.loadIcon(context.getPackageManager());
   bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
   float scale=Math.min((float)width/Math.max(1,d.getIntrinsicWidth()),(float)height/Math.max(1,d.getIntrinsicHeight()));
   int w=Math.max(1,(int)(d.getIntrinsicWidth()*scale)),h=Math.max(1,(int)(d.getIntrinsicHeight()*scale));
   d.setBounds((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);d.draw(canvas);icons.put(key,bitmap);
  }catch(Exception ignored){bitmap=null;} final Bitmap image=bitmap;main.post(()->{if(!closed)done.accept(image);});});
 }
 void clear(){icons.evictAll();}
 void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);icons.evictAll();}
}
