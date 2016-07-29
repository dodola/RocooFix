/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.util.Log;

@SuppressLint({ "NewApi" })
public class RocooSoFix {
    private static final String TAG = "RocooSoFix";

    /**
     * 从指定目录加载so
     *
     * 注意,目录要跟jniLibs下的结构一样才可以比如 armeabi/libstub.so
     *
     * @param context
     * @param soDirPath
     */
    public static void applyPatch(Context context, String soDirPath) {

        try {
            ApplicationInfo applicationInfo = getApplicationInfo(context);
            if (applicationInfo == null) {
                return;
            }
            ClassLoader loader;
            try {
                loader = context.getClassLoader();
            } catch (RuntimeException e) {
                Log.w(TAG, "Failure while trying to obtain Context class loader. "
                        + "Must be running in test mode. Skip patching.", e);
                return;
            }
            if (loader == null) {
                Log.e(TAG, "Context class loader is null. Must be running in test mode. " + "Skip patching.");
                return;
            }

            File soDirFile = new File(soDirPath);
            if (soDirFile.exists()) {
                installSoDir(loader, soDirFile);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static ApplicationInfo getApplicationInfo(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException e) {
            /*
             * Ignore those exceptions so that we don't break tests relying on Context like a
             * android.test.mock.MockContext or a android.content.ContextWrapper with a null base Context.
             */
            Log.w(TAG, "Failure while trying to obtain ApplicationInfo from Context. "
                    + "Must be running in test mode. Skip patching.", e);
            return null;
        }
        if (pm == null || packageName == null) {
            // This is most likely a mock context, so just return without patching.
            return null;
        }
        ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        return applicationInfo;
    }

    public static void installSoDir(ClassLoader classloader, File additionalSoPath) throws InvocationTargetException,
            NoSuchMethodException, IllegalAccessException, NoSuchFieldException, InstantiationException, IOException {
        if (additionalSoPath != null && additionalSoPath.exists()) {
            if (VERSION.SDK_INT >= 23) {
                V23.install(classloader, additionalSoPath);
            } else if (VERSION.SDK_INT >= 14) {
                V14.install(classloader, additionalSoPath);
            } else {
                V4.install(classloader, additionalSoPath);
            }
        }
    }

    private static final class V4 {
        private static void install(ClassLoader loader, File additionalSoPath) throws IllegalArgumentException,
                IllegalAccessException, NoSuchFieldException, IOException {

            Field pathField = RocooUtils.findField(loader, "path");
            List<String> libraryPathElements =
                    (List<String>) RocooUtils.findField(loader, "libraryPathElements").get(loader);
            if (libraryPathElements != null) {
                libraryPathElements.add(0, additionalSoPath.getAbsolutePath());
            }
        }
    }

    private static final class V14 {

        private static void install(ClassLoader loader, File additionalSoPath) throws IllegalArgumentException,
                IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            Field pathListField = RocooUtils.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);

            Field nativeLibraryDirectoriesField = RocooUtils.findField(dexPathList, "nativeLibraryDirectories");
            File[] nativeLibraryDirectories = (File[]) nativeLibraryDirectoriesField.get(dexPathList);
            if (nativeLibraryDirectories != null) {
                File[] tmp = new File[(nativeLibraryDirectories.length + 1)];
                tmp[0] = additionalSoPath;
                System.arraycopy(nativeLibraryDirectories, 0, tmp, 1, nativeLibraryDirectories.length);
                nativeLibraryDirectoriesField.set(dexPathList, tmp);
            }
        }
    }

    private static final class V23 {

        private static void install(ClassLoader loader, File additionalSoPath) throws IllegalArgumentException,
                IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException,
                InstantiationException {

            try {
                Object dexPathList = RocooUtils.findField(loader, "pathList").get(loader);
                ArrayList<File> additionalPathEntries = new ArrayList<>();
                additionalPathEntries.add(additionalSoPath);
                ArrayList<IOException> suppressedExceptions = new ArrayList<>();
                RocooUtils.expandFieldArray(dexPathList, "nativeLibraryPathElements",
                        RocooUtils.makePathElements(dexPathList, additionalPathEntries, null, suppressedExceptions));
                if (suppressedExceptions.size() > 0) {
                    for (IOException e : suppressedExceptions) {
                        Log.w(TAG, "Exception in makeDexElement", e);
                    }
                    Field suppressedExceptionsField =
                            RocooUtils.findField(dexPathList, "dexElementsSuppressedExceptions");
                    IOException[] dexElementsSuppressedExceptions =
                            (IOException[]) suppressedExceptionsField.get(dexPathList);

                    if (dexElementsSuppressedExceptions == null) {
                        dexElementsSuppressedExceptions =
                                suppressedExceptions.toArray(new IOException[suppressedExceptions.size()]);
                    } else {
                        IOException[] combined =
                                new IOException[suppressedExceptions.size() + dexElementsSuppressedExceptions.length];
                        suppressedExceptions.toArray(combined);
                        System.arraycopy(dexElementsSuppressedExceptions, 0, combined, suppressedExceptions.size(),
                                dexElementsSuppressedExceptions.length);
                        dexElementsSuppressedExceptions = combined;
                    }

                    suppressedExceptionsField.set(dexPathList, dexElementsSuppressedExceptions);
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
            }
        }
    }
}