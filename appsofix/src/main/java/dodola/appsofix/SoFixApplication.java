package dodola.appsofix;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.dodola.rocoofix.RocooSoFix;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by shoyu666 on 16/7/30.
 */
public class SoFixApplication extends Application{
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            FileUtils.copyFileToDirectory(new File(getSDCardSoPath(),"libhello-jni.so"),getDataFileSoPath(base));
        } catch (IOException e) {
            e.printStackTrace();
        }
        RocooSoFix.applyPatch(base,new File(getDataFileSoPath(base),"libhello-jni.so").getAbsolutePath());
        // RocooSoFix.applyPatch(base,getDataFileSoPatchForInstall(base).getAbsolutePath());
    }


    public static File getDataFileSoPatchForInstall(Context base){
        File dir =new File(base.getFilesDir().getAbsolutePath()+File.separator+"lib"+File.separator);
        if(!dir.exists()){
            dir.mkdirs();
        }
        return dir;
    }

    public static File getDataFileSoPath(Context base){
//        File dir =new File(base.getFilesDir().getAbsolutePath()+File.separator+"lib"+File.separator+"armeabi-v7a"+File.separator);
        File dir =new File(getDataFileSoPatchForInstall(base),"armeabi-v7a"+File.separator);
        if(!dir.exists()){
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSDCardSoPath(){
//        String fileStr = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"com.dodola.rocoofix.RocooSoFix"+ File.separator+"armeabi"+File.separator;
        String fileStr = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"armeabi-v7a"+File.separator;
        File f = new File(fileStr);
        if(!f.exists()){
            f.mkdirs();
        }
        return f;
    }
}
