/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix;

import android.content.Context;
import android.text.TextUtils;

import com.lody.legend.dalvik.DalvikMethodStruct;
import com.lody.legend.dalvik.ObjectStruct;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by sunpengfei on 16/7/3.
 */

public class ClassLoaderHack {


    private static boolean isArt;
    private static Method findClassMethod;

    public ClassLoaderHack(Context context) {

    }

    public static void initHack(Context context) {

        String version = System.getProperty("java.vm.version");
        isArt = version.startsWith("2");
        if (!TextUtils.isEmpty(version) && !isArt) {

            try {
                findClassMethod = ClassLoader.class.getDeclaredMethod("findClass", String.class);
                findClassMethod.setAccessible(true);

                ClassLoader defClassLoader = context.getClassLoader();
                hackParent(defClassLoader);

            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

    }

    private static void hackParent(ClassLoader defClassLoader)
            throws NoSuchFieldException, IllegalAccessException {
        Field parentField = ClassLoader.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        ClassLoader parent = (ClassLoader) parentField.get(defClassLoader);
        PatchClassLoader patchClassLoader = new PatchClassLoader(parent, defClassLoader);
        parentField.set(defClassLoader, patchClassLoader);
    }

    private static class PatchClassLoader extends ClassLoader {

        private ClassLoader delegate;

        public PatchClassLoader(ClassLoader parent, ClassLoader delagete) {
            super(parent);
            this.delegate = delagete;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                Class<?> clazz = (Class<?>) findClassMethod.invoke(delegate, name);
                if (clazz != null) {

                    try {
                        clearPreverifiedState(clazz);
                    } catch (Throwable e) {
                    }
                    return clazz;
                }
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
            throw new ClassNotFoundException();
        }
    }

    /**
     * 去除掉调用方的Preverified标志,暂时测试通过
     *
     * @param clazz
     */
    public static void clearPreverifiedState(Class<?> clazz) {
        if (clazz != null) {
            Method[] declaredMethods = clazz.getDeclaredMethods();
            if (declaredMethods != null && declaredMethods.length > 0) {
                Method m = declaredMethods[0];
                DalvikMethodStruct dalvikMethodStruct = DalvikMethodStruct.of(m);
                long l = dalvikMethodStruct.clazz.readLong();
                ObjectStruct objectStruct = ObjectStruct.of(l);
                long l1 = objectStruct.accessFlags.readLong();
                l1 &= ~(1 << 16);
                objectStruct.accessFlags.write(l1);
            }
        }
    }
}
