package dodola.appsofix;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Created by shoyu666 on 16/8/11.
 */

public class SoFileUtil {

    /**
     * so 安装根目录
     * @param base
     * @return
     */
    public static File getDataFileSoPatchForInstall(Context base){
        File dir =new File(base.getFilesDir().getAbsolutePath()+File.separator+"lib"+File.separator);
        if(!dir.exists()){
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 获得so 安装目录
     * @param base
     * @return
     */
    public static File getDataFileSoPath(Context base){
        File dir =new File(getDataFileSoPatchForInstall(base),"armeabi-v7a"+File.separator);
        if(!dir.exists()){
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * lib 文件存放地址  sdcard/libhello-jni.so
     * @return
     */
    public static File getSDCardSoPath(){
        String fileStr = Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator;
        File f = new File(fileStr);
        if(!f.exists()){
            f.mkdirs();
        }
        return f;
    }
}
