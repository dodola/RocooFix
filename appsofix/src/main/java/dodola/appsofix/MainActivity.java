/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package dodola.appsofix;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.hellojni.HelloJni;

import java.io.File;

/**
 * Created by shoyu666 on 16/8/11.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView test = (TextView)this.findViewById(R.id.test);
        TextView source = (TextView)this.findViewById(R.id.source);
        source.setText("请在如下路径放入so "+SoFileUtil.getSDCardSoPath().getAbsolutePath()+File.separator+SoFileUtil.getFullSoName("libhello-jni")+"  [请注意修改so文件名称]");
        TextView copyfrom = (TextView)this.findViewById(R.id.copyfrom);
        copyfrom.setText("so会被安装到"+SoFileUtil.getDataFileSoPatchForInstall(this).getAbsolutePath()+ File.separator+SoFileUtil.getFullSoName("libhello-jni")+"路径");
        String jniStr=null;
        try {
            jniStr=HelloJni.stringFromJNI();
            test.setText("读取so内容["+jniStr+"]");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            test.setText("##错误## "+e.getMessage());
        }
    }
}
