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
        def variants = project.android.applicationVariants;
        println(ReflectionToStringBuilder.toString(variants, RecursiveToStringStyle.MULTI_LINE_STYLE));
        def plugin = project.plugins.findPlugin(AppPlugin.class);
        println(ReflectionToStringBuilder.toString(plugin, RecursiveToStringStyle.MULTI_LINE_STYLE));
        println(ReflectionToStringBuilder.toString(plugin.extension, RecursiveToStringStyle.MULTI_LINE_STYLE))

        variants.all { variant ->
            println("==================variant start====================" + variant)
            def manager = variant.variantData.getScope().transformManager;

            manager.transforms.each {
                println(ReflectionToStringBuilder.toString(it, RecursiveToStringStyle.MULTI_LINE_STYLE));
            }

            def proguardTransform = manager.transforms.find {
                it.class.name == ProGuardTransform.class.name
            };
            if (proguardTransform)
                println(ReflectionToStringBuilder.toString(proguardTransform.configuration, RecursiveToStringStyle.MULTI_LINE_STYLE));
        }
        println("==================variant end====================")

//        println(ReflectionToStringBuilder.toString(, RecursiveToStringStyle.MULTI_LINE_STYLE));

    }
}
