/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dodola.rocoofix.utils.classref;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.rop.cst.Constant;
import com.android.dx.rop.cst.ConstantPool;
import com.android.dx.rop.cst.CstFieldRef;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.type.Prototype;
import com.android.dx.rop.type.StdTypeList;
import com.android.dx.rop.type.Type;
import com.android.dx.rop.type.TypeList;
import com.dodola.rocoofix.utils.NuwaProcessor;
import com.google.common.collect.Sets;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Tool to find direct class references to other classes.
 */
public class ClassReferenceListBuilder {
    private static final String CLASS_EXTENSION = ".class";

    private Path path;
    private final Set<String> alreadyAnalyzedClasses = new HashSet<String>();

    private final Set<String> classNames = new HashSet<String>();
    private JarFile jarOfRoots;
    private String refcname;
    private String currentName;

    private String patchDir;

    public ClassReferenceListBuilder(String patchDir) {
        this.patchDir = patchDir;
    }


    public void addRoots(String path) throws IOException {
        this.path = new Path(path);
        this.jarOfRoots = new JarFile(path);
    }

    public boolean shouldScan(String entryName) {
        return !entryName.startsWith("com/dodola/rocoofix/") &&
                !entryName.startsWith("com/lody/legend/") &&
                !entryName.contains("android/support/");
    }

    public void clearCache() {
        classNames.clear();
    }

    public void run(String refClassName) throws IOException {

        classNames.add(refClassName);
        while (true) {
            Set<String> temp = getUnAnalyzeClasses();
            if (temp.isEmpty()) {
                break;
            }
            for (String tempName : temp) {
                alreadyAnalyzedClasses.add(tempName);
                refcname = tempName;
//                System.out.println("scan:---------->" + refcname);
                for (Enumeration<? extends ZipEntry> entries = jarOfRoots.entries();
                     entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    currentName = name;
                    if (name.endsWith(CLASS_EXTENSION) && shouldScan(name)) {
                        DirectClassFile classFile;
                        try {
                            classFile = path.getClass(name);
                        } catch (FileNotFoundException e) {
                            throw new IOException("Class " + name +
                                    " is missing form original class path " + path, e);
                        }
//                        System.out.println("=====classname:" + name);
                        addDependencies(classFile.getConstantPool());
                    }
                }
            }
        }
    }

    public HashSet<String> getUnAnalyzeClasses() {
        HashSet<String> temp = Sets.newHashSet();
//        for (String alreadyAnalyzedClass : alreadyAnalyzedClasses) {
//            System.out.println("alreadyAnalyzedClass:---------====--->" + alreadyAnalyzedClass);
//        }
        for (String className : classNames) {
//            System.out.println("classNames:---------====--->" + className);


            if (!alreadyAnalyzedClasses.contains(className)) {
                temp.add(className);
            }
        }
        return temp;
    }


    public Set<String> getClassNames() {
        return classNames;
    }

    private void addDependencies(ConstantPool pool) {

        for (Constant constant : pool.getEntries()) {
            if (constant instanceof CstType) {
                checkDescriptor(((CstType) constant).getClassType());
            } else if (constant instanceof CstFieldRef) {
                checkDescriptor(((CstFieldRef) constant).getType());
            } else if (constant instanceof CstMethodRef) {
                Prototype proto = ((CstMethodRef) constant).getPrototype();
                checkDescriptor(proto.getReturnType());
                StdTypeList args = proto.getParameterTypes();
                for (int i = 0; i < args.size(); i++) {
                    checkDescriptor(args.get(i));
                }
            }
        }
    }

    private void checkDescriptor(Type type) {

        String descriptor = type.getDescriptor();
        if (descriptor.endsWith(";")) {
//            System.out.println("=====descriptor:" + descriptor);

            int lastBrace = descriptor.lastIndexOf('[');
            if (lastBrace < 0) {
                addClassWithHierachy(descriptor.substring(1, descriptor.length() - 1));
            } else {
                assert descriptor.length() > lastBrace + 3
                        && descriptor.charAt(lastBrace + 1) == 'L';
                addClassWithHierachy(descriptor.substring(lastBrace + 2,
                        descriptor.length() - 1));
            }
        }
    }

    private void addClassWithHierachy(String classBinaryName) {
        if (classNames.contains(classBinaryName) || !refcname.equals(classBinaryName + CLASS_EXTENSION)) {
            return;
        }

        try {
            DirectClassFile classFile = path.getClass(currentName);
            classNames.add(currentName);
            File entryFile = new File(patchDir + "/" + currentName);
            entryFile.getParentFile().mkdirs();


            if (!entryFile.exists()) {
                entryFile.createNewFile();
//                Iterable<ClassPathElement> elements = path.getElements();
//                for (ClassPathElement element : elements) {
//                    InputStream in = element.open(currentName);
                byte[] bytes = NuwaProcessor.referHackWhenInit(classFile.getBytes().makeDataInputStream());
//                    System.out.println(classFile.getFilePath() + ",size:" + bytes.length);
                FileUtils.writeByteArrayToFile(entryFile, bytes);
//                }
            }
//            NuwaProcessor.referHackWhenInit();


            CstType superClass = classFile.getSuperclass();
            if (superClass != null) {
                addClassWithHierachy(superClass.getClassType().getClassName());
            }

            TypeList interfaceList = classFile.getInterfaces();
            int interfaceNumber = interfaceList.size();
            for (int i = 0; i < interfaceNumber; i++) {
                addClassWithHierachy(interfaceList.getType(i).getClassName());
            }
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
