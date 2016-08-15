package dodola.appsofix;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * Created by shoyu666 on 16/8/11.
 */

public class SoFileUtil {


    /**
     *
     * @param libname  比如  libhello-jni
     * @return  libhello-jni-armbi.so
     */
    public static String getFullSoName(String libname){
        return libname+"-"+Build.CPU_ABI+".so";
    }
    /**
     * so 安装根目录
     * @param base
     * @return
     */
    public static File getDataFileSoPatchForInstall(Context base){
        File dir =new File(base.getFilesDir().getAbsolutePath()+File.separator+"rocoolib"+File.separator);
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
