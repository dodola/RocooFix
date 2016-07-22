/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils


import com.dodola.rocoofix.RocooFixPlugin
import com.dodola.rocoofix.ref.PatchRefScan
import com.dodola.rocoofix.ref.Path
import javassist.*
import javassist.bytecode.AccessFlag
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Created by jixin.jia on 15/11/10.
 */
class NuwaProcessor {


    public
    static processJar(File hashFile, File jarFile, File patchDir, Map map, HashSet<String> includePackage, HashSet<String> excludeClass) {
        if (jarFile) {

            def optJar = new File(jarFile.getParent(), jarFile.name + ".opt")
            def file = new JarFile(jarFile);
            def path = new Path(jarFile.getAbsolutePath());
            com.dodola.rocoofix.ref.ClassReferenceListBuilder mainListBuilder = new com.dodola.rocoofix.ref.ClassReferenceListBuilder(path);
            def patchClasss = new HashSet<String>();
            Enumeration enumeration = file.entries();
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar));

            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);

                InputStream inputStream = file.getInputStream(jarEntry);
                jarOutputStream.putNextEntry(zipEntry);

                if (shouldProcessClassInJar(entryName, includePackage, excludeClass)) {
                    def bytes = referHackWhenInit(inputStream);
                    jarOutputStream.write(bytes);
                    def hash = DigestUtils.shaHex(bytes)
                    hashFile.append(RocooUtils.format(entryName, hash))

                    if (RocooUtils.notSame(map, entryName, hash)) {

                        def entryFile = new File("${patchDir}${File.separator}${entryName}")
                        entryFile.getParentFile().mkdirs()
                        if (!entryFile.exists()) {
                            entryFile.createNewFile()
                        }
                        FileUtils.writeByteArrayToFile(entryFile, bytes)
                        if (RocooFixPlugin.rocooConfig.scanref) {
                            //收集补丁类
                            patchClasss.add(entryName)
                            System.out.println("============patchClasss add======" + entryName)
                            mainListBuilder.addRootsV2(entryName)
                        }
                    }
                } else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                }
                jarOutputStream.closeEntry();
            }
            //
            if (RocooFixPlugin.rocooConfig.scanref) {
                //step 1 查找补丁类的相关依赖(主要为了找到继承)
                for (String className : mainListBuilder.getClassNames()) {
                    System.out.println("===========step1===" + className)
                    def entryFile = new File("${patchDir}/${className}.class")
                    if (entryFile.exists()) {
                        //补丁中存在,跳过
                        continue;
                    }
                    //遍历收集到的依赖,写入patchDir
                    ZipEntry zipEntry = new ZipEntry(className + ".class");
                    InputStream inputStreamX = file.getInputStream(zipEntry);
                    def bytes = referHackWhenInit(inputStreamX);
                    FileUtils.writeByteArrayToFile(entryFile, bytes)
                }
                mainListBuilder.clearAllForReuse();

                //step 2 遍历整个jar包 查找调用了补丁的类(主要找调用了补丁的类)
                System.out.println("============patchClasss======" + patchClasss.size())
                Enumeration enumerationForScanref = file.entries();
                while (enumerationForScanref.hasMoreElements()) {
                    JarEntry jarEntry = (JarEntry) enumerationForScanref.nextElement();
                    String entryName = jarEntry.getName();
                    mainListBuilder.addRootsV2(entryName)
                    def allrefs = mainListBuilder.getClassNames();
                    PatchRefScan.addClassIfRefPatchClass(jarEntry, entryName, allrefs, patchClasss, file, patchDir);
                    mainListBuilder.clearAllForReuse();
                }
                //
            }
            jarOutputStream.close();
            file.close();

            if (jarFile.exists()) {
                jarFile.delete()
            }
//            optJar.renameTo(jarFile)
            org.apache.commons.io.FileUtils.copyFile(optJar, jarFile);
        }

    }

    //refer hack class when object init
    public static byte[] referHackWhenInit(InputStream inputStream) {
//        ClassReader cr = new ClassReader(inputStream);
//        ClassWriter cw = new ClassWriter(cr, 0);
//        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4, cw) {
//            @Override
//            public MethodVisitor visitMethod(int access, String name, String desc,
//                                             String signature, String[] exceptions) {
//
//                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
//                mv = new MethodVisitor(Opcodes.ASM4, mv) {
//                    @Override
//                    void visitInsn(int opcode) {
//                        if ("<init>".equals(name) && opcode == Opcodes.RETURN) {
//                            Label l1 = new Label();
//                            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
//                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
//                            super.visitJumpInsn(Opcodes.IFEQ, l1);
//                            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
//                            super.visitLdcInsn(Type.getType("Lcom/dodola/rocoo/Hack;"));
//                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
//                            super.visitLabel(l1);
//                        }
//                        super.visitInsn(opcode);
//                    }
//
//                    @Override
//                    public void visitMaxs(int maxStack, int maxLocal) {
//                        if ("<init>".equals(name)) {
//                            super.visitMaxs(maxStack + 2, maxLocal);
//                        } else {
//                            super.visitMaxs(maxStack, maxLocal);
//                        }
//                    }
//                }
//                return mv;
//            }
//
//        };
//        cr.accept(cv, 0);
//        return cw.toByteArray();
        return injectHackClass(inputStream);
    }


    public static ClassPool pool;

    public static StringBuilder injectCode = new StringBuilder();
    public static List<ClassPath> classPathList = new ArrayList<>();

    static {


        injectCode
                .append("if(Boolean.FALSE.booleanValue()){")
                .append("System.out.println(com.dodola.rocoo.Hack.class);")
                .append("}");
        pool = ClassPool.getDefault();
        CtClass unPreverifiedStub = pool.makeClass("com.dodola.rocoo.Hack");
        CtMethod m = new CtMethod(CtClass.voidType, "init", null, unPreverifiedStub);
        try {
            m.setModifiers(AccessFlag.PUBLIC | AccessFlag.STATIC);
            unPreverifiedStub.addMethod(m);
            unPreverifiedStub.setModifiers(unPreverifiedStub.getModifiers());
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }


    public
    static byte[] getHackClass() throws IOException, CannotCompileException, NotFoundException {
        CtClass ctClass = pool.get("com.dodola.rocoo.Hack");
        return ctClass.toBytecode();
    }


    public static void initClassPaths(List<String> classpathes) {
        classPathList.clear();
        if (classpathes != null && classpathes.size() > 0) {
            for (String classpath : classpathes) {
                classPathList.add(pool.insertClassPath(classpath));
            }
        }
    }

    public static byte[] injectHackClass(InputStream classInputstream)
            throws IOException, CannotCompileException, NotFoundException {

        CtClass cc = pool.makeClass(classInputstream, false);
        cc.defrost();
        CtConstructor[] constructors = cc.getDeclaredConstructors();
        if (constructors != null && constructors.length > 0) {
            for (CtConstructor constructor : constructors) {
                constructor.insertAfter(injectCode.toString());
            }
        } else {
            if (!cc.isInterface()) {
                CtConstructor constructor = cc.makeClassInitializer();
                constructor.insertAfter(injectCode.toString());
                constructor.setModifiers(AccessFlag.PUBLIC | AccessFlag.STATIC);
            }
        }
        byte[] res = cc.toBytecode();
        cc.detach();
        return res;
    }


    public static boolean shouldProcessPreDexJar(String path) {
        return path.endsWith("classes.jar") &&
                !path.contains("com.android.support") &&
                !path.contains("/android/m2repository");
    }

    private
    static boolean shouldProcessClassInJar(String entryName, HashSet<String> includePackage, HashSet<String> excludeClass) {
        return entryName.endsWith(".class") &&
                !entryName.startsWith("com/dodola/rocoofix/") &&
                !entryName.startsWith("com/lody/legend/") &&
                NuwaSetUtils.isIncluded(entryName, includePackage) &&
                !NuwaSetUtils.isExcluded(entryName, excludeClass) &&
                !entryName.contains("android/support/")
    }

    public static byte[] processClass(File file) {
        def optClass = new File(file.getParent(), file.name + ".opt")

        FileInputStream inputStream = new FileInputStream(file);
        FileOutputStream outputStream = new FileOutputStream(optClass)

        def bytes = referHackWhenInit(inputStream);
        outputStream.write(bytes)
        inputStream.close()
        outputStream.close()
        if (file.exists()) {
            file.delete()
        }
        optClass.renameTo(file)
        return bytes
    }
}
