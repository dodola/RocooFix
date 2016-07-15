/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoosample;

import com.dodola.rocoosample.Ref.RefByHelloHack;

/**
 * Created by sunpengfei on 16/5/24.
 */
public class HelloHack {

    public String showHello() {
        RefByHelloHack a = new RefByHelloHack();
        a.toString();
        return "==============H";
    }
}
