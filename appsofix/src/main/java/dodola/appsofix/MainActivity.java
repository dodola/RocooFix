/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package dodola.appsofix;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.hellojni.HelloJni;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView test = (TextView)this.findViewById(R.id.test);
        String jniStr=null;
        try {
            jniStr=HelloJni.stringFromJNI();
            test.setText(jniStr);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            test.setText(e.getMessage());
        }
    }
}
