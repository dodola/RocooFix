/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils

import org.gradle.api.Project

/**
 * Created by shoyu666 on 16/7/26.
 */
public class HttpClientJarPathUtil {

    /**
     * 判断build.gralde是否设置了
     * useLibrary 'org.apache.http.legacy'
     * @param lines
     * @return
     */
    public static boolean hasHttpLib(String[] lines) {
        boolean has = false;
        for (String line : lines) {
            if (line.contains("useLibrary") && line.contains("org.apache.http.legacy")) {
                has = true;
            }
        }
        return has;
    }

    /**
     * build.gralde文件每行读入String[]
     * @param buildFile
     * @return
     */
    public static String[] getBuildFileLines(File buildFile) {
        List<String> lines = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new FileReader(buildFile))
        String s = "";
        while ((s = br.readLine()) != null) {
            lines.add(s)
        }
        br.close();
        return lines
    }

    /**
     * 根据local.properties的sdk路径和compileSdkVersion 获得
     * org.apache.http.legacy的jar文件路径
     * @param project
     * @return
     */
    public static String getHttpClientPath(Project project){
            def rootDir = project.rootDir
            def localProperties = new File(rootDir, "local.properties")
            if (localProperties.exists()) {
                Properties properties = new Properties()
                localProperties.withInputStream { instr ->
                    properties.load(instr)
                }
                def sdkDir = properties.getProperty('sdk.dir')
                def androidJarPath = sdkDir + File.separator+"platforms"+File.separator +
                        project.android.compileSdkVersion +File.separator +"optional"+File.separator+"org.apache.http.legacy.jar"
                return androidJarPath;
            }
        }
}
