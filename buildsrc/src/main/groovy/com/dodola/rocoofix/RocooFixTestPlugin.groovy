/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.transforms.ProGuardTransform
import org.apache.commons.lang3.builder.RecursiveToStringStyle
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

class RocooFixTestPlugin implements Plugin<Project> {


    @Override
    void apply(Project project) {
        println("=========================================")
        def variants = project.android.applicationVariants;
        println(ReflectionToStringBuilder.toString(variants, RecursiveToStringStyle.MULTI_LINE_STYLE));
        println("===================end======================")
        println(ReflectionToStringBuilder.toString(project.plugins.findPlugin(AppPlugin.class), RecursiveToStringStyle.MULTI_LINE_STYLE));
        println("====================start2=====================")

        variants.all { variant ->
            def manager = variant.variantData.getScope().transformManager;
            def proguardTransform = manager.transforms.find {
                it.class.name == ProGuardTransform.class.name
            };
            if (proguardTransform)
                println(ReflectionToStringBuilder.toString(proguardTransform.configuration, RecursiveToStringStyle.MULTI_LINE_STYLE));
        }
        println("===================end2======================")

//        for(){
//
//        }
    }
}
