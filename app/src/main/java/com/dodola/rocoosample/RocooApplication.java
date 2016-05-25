/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoosample;

import android.app.Application;
import android.content.Context;

/**
 * Created by sunpengfei on 16/5/24.
 */
public class RocooApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //打补丁
    }
}
