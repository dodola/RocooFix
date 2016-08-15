package dodola.appsofix;

import android.app.Application;
import android.content.Context;
import android.os.Build;

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
            //删除之前的so
            SoFileUtil.getDataFileSoPatchForInstall(base).delete();
            //sdcard/libhello-jni-{cpu}.so  copy to getDataFileSoPath()
            //拷贝sd卡 so到data目录
            FileUtils.copyFileToDirectory(new File(SoFileUtil.getSDCardSoPath(),SoFileUtil.getFullSoName("libhello-jni")),SoFileUtil.getDataFileSoPatchForInstall(base));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //install data目录的so路径
        RocooSoFix.applyPatch(base,SoFileUtil.getDataFileSoPatchForInstall(base).getAbsolutePath());
    }
}
