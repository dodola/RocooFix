/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */

package com.dodola.rocoofix;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;

/**
 * modify from multidex source code
 */
public final class RocooFix {

    static final String TAG = "Rocoo";

    private static final String CODE_CACHE_NAME = "code_cache";

    private static final String CODE_CACHE_SECONDARY_FOLDER_NAME = "rocoo-dexes";

    private static final int VM_WITH_MULTIDEX_VERSION_MAJOR = 2;

    private static final int VM_WITH_MULTIDEX_VERSION_MINOR = 1;

    private static final Set<String> installedApk = new HashSet<String>();


    private static final boolean IS_VM_CAPABLE =
            isARTVMCapable(System.getProperty("java.vm.version"));//判断是否是dalvik虚拟机

    private RocooFix() {
    }

    public static void init(Context context) {
        initPathFromAssets(context, "rocoo.dex");
    }

    public static void initPathFromAssets(Context context, String assetName) {
        File dexDir = new File(context.getFilesDir(), "hotfix");
        dexDir.mkdir();

        String dexPath = null;
        try {
            dexPath = copyAsset(context, assetName, dexDir);
        } catch (IOException e) {
        } finally {
            if (new File(dexPath).exists()) {
                applyPatch(context, dexPath);
            }
        }
    }

    public static void applyPatch(Context context, String dexPath) {
        if (IS_VM_CAPABLE) {
            //art虚拟机走另外一套fix
            return;
        }

        try {
            ApplicationInfo applicationInfo = getApplicationInfo(context);
            if (applicationInfo == null) {
                return;
            }

            synchronized (installedApk) {
                if (installedApk.contains(dexPath)) {
                    return;
                }
                installedApk.add(dexPath);

                /* The patched class loader is expected to be a descendant of
                 * dalvik.system.BaseDexClassLoader. We modify its
                 * dalvik.system.DexPathList pathList field to append additional DEX
                 * file entries.
                 */
                ClassLoader loader;
                try {
                    loader = context.getClassLoader();
                } catch (RuntimeException e) {
                    /* Ignore those exceptions so that we don't break tests relying on Context like
                     * a android.test.mock.MockContext or a android.content.ContextWrapper with a
                     * null base Context.
                     */
                    Log.w(TAG, "Failure while trying to obtain Context class loader. " +
                            "Must be running in test mode. Skip patching.", e);
                    return;
                }
                if (loader == null) {
                    // Note, the context class loader is null when running Robolectric tests.
                    Log.e(TAG,
                            "Context class loader is null. Must be running in test mode. "
                                    + "Skip patching.");
                    return;
                }

                List<File> files = new ArrayList<File>();
                files.add(new File(dexPath));
                File dexDir = getDexDir(context, applicationInfo);
                installDexes(loader, dexDir, files);
            }

        } catch (Exception e) {
            throw new RuntimeException("Hotfix installation failed (" + e.getMessage() + ").");
        } catch (Throwable e) {
        }
    }

    private static ApplicationInfo getApplicationInfo(Context context)
            throws NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException e) {
            /* Ignore those exceptions so that we don't break tests relying on Context like
             * a android.test.mock.MockContext or a android.content.ContextWrapper with a null
             * base Context.
             */
            Log.w(TAG, "Failure while trying to obtain ApplicationInfo from Context. " +
                    "Must be running in test mode. Skip patching.", e);
            return null;
        }
        if (pm == null || packageName == null) {
            // This is most likely a mock context, so just return without patching.
            return null;
        }
        ApplicationInfo applicationInfo =
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        return applicationInfo;
    }

    /**
     * Identifies if the current VM has a native support for Hotfix, meaning there is no need for
     * additional installation by this library.
     *
     * @return true if the VM handles Hotfix
     */
    /* package visible for test */
    static boolean isARTVMCapable(String versionString) {
        boolean isHotfixCapable = false;
        if (versionString != null) {
            Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    int major = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    isHotfixCapable = (major > VM_WITH_MULTIDEX_VERSION_MAJOR)
                            || ((major == VM_WITH_MULTIDEX_VERSION_MAJOR)
                            && (minor >= VM_WITH_MULTIDEX_VERSION_MINOR));
                } catch (NumberFormatException e) {
                    // let isHotfixCapable be false
                }
            }
        }
        Log.i(TAG, "VM with version " + versionString +
                (isHotfixCapable ?
                        " has Hotfix support" :
                        " does not have Hotfix support"));
        return isHotfixCapable;
    }

    private static void installDexes(ClassLoader loader, File dexDir, List<File> files)
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
            InvocationTargetException, NoSuchMethodException, IOException, InstantiationException {
        if (!files.isEmpty()) {
            if (Build.VERSION.SDK_INT >= 23) {
                V23.install(loader, files, dexDir);
            } else if (Build.VERSION.SDK_INT >= 19) {
                V19.install(loader, files, dexDir);
            } else if (Build.VERSION.SDK_INT >= 14) {
                V14.install(loader, files, dexDir);
            } else {
                V4.install(loader, files);
            }
        }
    }


    /**
     * Locates a given field anywhere in the class inheritance hierarchy.
     *
     * @param instance an object to search the field into.
     * @param name     field name
     * @return a field object
     * @throws NoSuchFieldException if the field cannot be located
     */
    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);


                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                return field;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * Locates a given method anywhere in the class inheritance hierarchy.
     *
     * @param instance       an object to search the method into.
     * @param name           method name
     * @param parameterTypes method parameter types
     * @return a method object
     * @throws NoSuchMethodException if the method cannot be located
     */
    private static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);


                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }

                return method;
            } catch (NoSuchMethodException e) {
                // ignore and search next
            }
        }

        throw new NoSuchMethodException("Method " + name + " with parameters " +
                Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }

    /**
     * Replace the value of a field containing a non null array, by a new array containing the
     * elements of the original array plus the elements of extraElements.
     *
     * @param instance      the instance whose field is to be modified.
     * @param fieldName     the field to modify.
     * @param extraElements elements to append at the end of the array.
     */
    private static void expandFieldArray(Object instance, String fieldName,
                                         Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        Field jlrField = findField(instance, fieldName);
        Object[] original = (Object[]) jlrField.get(instance);
        Object[] combined = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length + extraElements.length);
        System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
        System.arraycopy(original, 0, combined, extraElements.length, original.length);
        jlrField.set(instance, combined);
    }

    private static File getDexDir(Context context, ApplicationInfo applicationInfo)
            throws IOException {
        File cache = new File(applicationInfo.dataDir, CODE_CACHE_NAME);
        try {
            mkdirChecked(cache);
        } catch (IOException e) {
            /* If we can't emulate code_cache, then store to filesDir. This means abandoning useless
             * files on disk if the device ever updates to android 5+. But since this seems to
             * happen only on some devices running android 2, this should cause no pollution.
             */
            cache = new File(context.getFilesDir(), CODE_CACHE_NAME);
            mkdirChecked(cache);
        }
        File dexDir = new File(cache, CODE_CACHE_SECONDARY_FOLDER_NAME);
        mkdirChecked(dexDir);
        return dexDir;
    }

    private static void mkdirChecked(File dir) throws IOException {
        dir.mkdir();
        if (!dir.isDirectory()) {
            File parent = dir.getParentFile();
            if (parent == null) {
                Log.e(TAG, "Failed to create dir " + dir.getPath() + ". Parent file is null.");
            } else {
                Log.e(TAG, "Failed to create dir " + dir.getPath() +
                        ". parent file is a dir " + parent.isDirectory() +
                        ", a file " + parent.isFile() +
                        ", exists " + parent.exists() +
                        ", readable " + parent.canRead() +
                        ", writable " + parent.canWrite());
            }
            throw new IOException("Failed to create directory " + dir.getPath());
        }
    }


    private static final class V23 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException, InstantiationException {

            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            Field dexElement = findField(dexPathList, "dexElements");
            Class<?> elementType = dexElement.getType().getComponentType();
            Method loadDex = findMethod(dexPathList, "loadDexFile", File.class, File.class);
            loadDex.setAccessible(true);

            Object dex = loadDex.invoke(null, additionalClassPathEntries.get(0), optimizedDirectory);
            Constructor<?> constructor = elementType.getConstructor(File.class, boolean.class, File.class, DexFile.class);
            constructor.setAccessible(true);
            Object element = constructor.newInstance(new File(""), false, additionalClassPathEntries.get(0), dex);

            Object[] newEles = new Object[1];
            newEles[0] = element;
            expandFieldArray(dexPathList, "dexElements", newEles);
        }

    }

    /**
     * Installer for platform versions 19.
     */
    private static final class V19 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                    suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makeDexElement", e);
                }
                Field suppressedExceptionsField =
                        findField(loader, "dexElementsSuppressedExceptions");
                IOException[] dexElementsSuppressedExceptions =
                        (IOException[]) suppressedExceptionsField.get(loader);

                if (dexElementsSuppressedExceptions == null) {
                    dexElementsSuppressedExceptions =
                            suppressedExceptions.toArray(
                                    new IOException[suppressedExceptions.size()]);
                } else {
                    IOException[] combined =
                            new IOException[suppressedExceptions.size() +
                                    dexElementsSuppressedExceptions.length];
                    suppressedExceptions.toArray(combined);
                    System.arraycopy(dexElementsSuppressedExceptions, 0, combined,
                            suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
                    dexElementsSuppressedExceptions = combined;
                }

                suppressedExceptionsField.set(loader, dexElementsSuppressedExceptions);
            }
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                ArrayList<IOException> suppressedExceptions)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                            ArrayList.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions);
        }
    }

    /**
     * Installer for platform versions 14, 15, 16, 17 and 18.
     */
    private static final class V14 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory));
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

    /**
     * Installer for platform versions 4 to 13.
     */
    private static final class V4 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.DexClassLoader. We modify its
             * fields mPaths, mFiles, mZips and mDexs to append additional DEX
             * file entries.
             */
            int extraSize = additionalClassPathEntries.size();

            Field pathField = findField(loader, "path");

            StringBuilder path = new StringBuilder((String) pathField.get(loader));
            String[] extraPaths = new String[extraSize];
            File[] extraFiles = new File[extraSize];
            ZipFile[] extraZips = new ZipFile[extraSize];
            DexFile[] extraDexs = new DexFile[extraSize];
            for (ListIterator<File> iterator = additionalClassPathEntries.listIterator();
                 iterator.hasNext(); ) {
                File additionalEntry = iterator.next();
                String entryPath = additionalEntry.getAbsolutePath();
                path.append(':').append(entryPath);
                int index = iterator.previousIndex();
                extraPaths[index] = entryPath;
                extraFiles[index] = additionalEntry;
                extraZips[index] = new ZipFile(additionalEntry);
                extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0);
            }

            pathField.set(loader, path.toString());
            expandFieldArray(loader, "mPaths", extraPaths);
            expandFieldArray(loader, "mFiles", extraFiles);
            expandFieldArray(loader, "mZips", extraZips);
            expandFieldArray(loader, "mDexs", extraDexs);
        }
    }

    public static String copyAsset(Context context, String assetName, File dir) throws IOException {
        File outFile = new File(dir, assetName);
        if (!outFile.exists()) {
            AssetManager assetManager = context.getAssets();
            InputStream in = assetManager.open(assetName);
            OutputStream out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            out.close();
        }
        return outFile.getAbsolutePath();
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
