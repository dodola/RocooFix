package com.dodola.rocoosample.Ref;

import com.dodola.rocoosample.HelloHack;

/**
 * 测试依赖关系
 * 测试修改HelloHack 会导致RefByHelloHack加入补丁
 * Created by shoyu666 on 16/7/15.
 */
public class RefByHelloHack {
    public String showHello() {
        HelloHack a = new HelloHack();
        a.toString();
        return "==============H";
    }
    public String test() {
        return "YYYYYYYYYYYYY";
    }
}
