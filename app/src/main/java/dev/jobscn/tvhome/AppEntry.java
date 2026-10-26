package dev.jobscn.tvhome;
import android.content.ComponentName;
final class AppEntry {
 final ComponentName component; final String title;
 AppEntry(ComponentName c,String t){component=c;title=t;}
 String key(){return component.flattenToString();}
}
