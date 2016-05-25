/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.tasks.Input

@CompileStatic
class RocooFixExtension {
    @Input
    boolean enable = false

    @Input
    HashSet<String> includePackageNames = [];

    @Input
    HashSet<String> excludePackageNames = [];

    @Input
    HashSet<String> excludeClasses = [];

    @Input
    String preVersionPath

    public static RocooFixExtension getConfig(Project project) {
        RocooFixExtension config =
                project.getExtensions().findByType(RocooFixExtension.class);
        if (config == null) {
            config = new RocooFixExtension();
        }
        return config;
    }

}
