/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils

/**
 * Created by jixin.jia on 15/11/10.
 */
class NuwaSetUtils {
    public static boolean isExcluded(String path, Set<String> excludeClass) {
        def isExcluded = false;
        excludeClass.each { exclude ->
            if (path.endsWith(exclude)) {
                isExcluded = true
            }
        }
        return isExcluded
    }

    public static boolean isIncluded(String path, Set<String> includePackage) {
        if (includePackage.size() == 0) {
            return true
        }

        def isIncluded = false;
        includePackage.each { include ->
            if (path.contains(include)) {
                isIncluded = true
            }
        }
        return isIncluded
    }
}
