/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils

import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

/**
 * Created by sunpengfei on 16/7/22.
 */

public class ClassPathUtils {

    public
    static List<String> getClassLibraryPaths(Project project, boolean proguard, boolean multiDex, String variantDirName) {
        NamedDomainObjectContainer<AndroidSourceSet> sourceSets = project.android.sourceSets
        AndroidSourceSet sourceSet = sourceSets.findByName("main")
        List<String> classPath = new ArrayList<>()
        sourceSet.jniLibs.getSrcDirs().each {
            classPath.add(it.absolutePath + File.separator + "*")
        }

        classPath.add(new File(project.buildDir, "intermediates" + File.separator
                + "classes" + File.separator + variantDirName).absolutePath)
        if (proguard) {
            classPath.add(new File(project.buildDir, "intermediates" + File.separator
                    + "classes-proguard" + File.separator + variantDirName).absolutePath + File.separator + "*")
        } else if (multiDex) {//multidex下的处理
            println new File(project.buildDir, "intermediates" + File.separator
                    + "multi-dex" + File.separator + variantDirName).absolutePath + File.separator + "*"
            classPath.add(new File(project.buildDir, "intermediates" + File.separator
                    + "multi-dex" + File.separator + variantDirName).absolutePath + File.separator + "*")
        } else {
            File explodedArrClassPath = new File(project.buildDir, "intermediates${File.separator}exploded-aar")
            List<File> child = explodedArrClassPath.listFiles()
            if (child) {
                child.each {
                    List<File> childForChild = it.listFiles()
                    if (childForChild) {
                        childForChild.each {
                            File file = new File(it, "unspecified${File.separator}jars${File.separator}classes.jar")
                            if (file.exists()) {
                                classPath.add(file.absolutePath)
                            }
                        }
                    }
                }
            }
        }

        String sdkDir
        Properties properties = new Properties()
        File localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            properties.load(localProps.newDataInputStream())
            sdkDir = properties.getProperty("sdk.dir")
        } else {
            sdkDir = System.getenv("ANDROID_HOME")
        }

        if (sdkDir) {
            def compileSdkVersion = project.android.compileSdkVersion
            if (!compileSdkVersion) {
                throw new Exception('can not find CompilesdkVersion')
            }
            File sdkClasspath =
                    new File(sdkDir, "platforms${File.separator}${compileSdkVersion}${File.separator}android.jar")
            if (!sdkClasspath.exists()) {
                throw new Exception('Can not find android.jar' + sdkClasspath.absolutePath)
            }
            classPath.add(sdkClasspath.absolutePath)
        } else {
            throw new Exception('Can not find ANDROID_HOME');
        }
        return classPath
    }

}
