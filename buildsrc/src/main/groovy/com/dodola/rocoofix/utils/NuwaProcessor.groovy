/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.*

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

//            ClassReferenceListBuilder referenceListBuilder = new ClassReferenceListBuilder(patchDir.getAbsolutePath());
//            referenceListBuilder.addRoots(jarFile.getAbsolutePath());

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

//                        if (RocooFixPlugin.rocooConfig.scanref) {
//                            referenceListBuilder.run(entryName)
//                            referenceListBuilder.clearCache()
//                        }
                    }
                }
                else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                }
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            file.close();

            if (jarFile.exists()) {
                jarFile.delete()
            }
            optJar.renameTo(jarFile)
        }

    }

    //refer hack class when object init
    public static byte[] referHackWhenInit(InputStream inputStream) {
        ClassReader cr = new ClassReader(inputStream);
        ClassWriter cw = new ClassWriter(cr, 0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {

                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                mv = new MethodVisitor(Opcodes.ASM4, mv) {
                    @Override
                    void visitInsn(int opcode) {
                        if ("<init>".equals(name) && opcode == Opcodes.RETURN) {
                            Label l1 = new Label();
                            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                            super.visitJumpInsn(Opcodes.IFEQ, l1);
                            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                            super.visitLdcInsn(Type.getType("Lcom/dodola/rocoo/Hack;"));
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                            super.visitLabel(l1);
                        }
                        super.visitInsn(opcode);
                    }

                    @Override
                    public void visitMaxs(int maxStack, int maxLocal) {
                        if ("<init>".equals(name)) {
                            super.visitMaxs(maxStack + 2, maxLocal);
                        } else {
                            super.visitMaxs(maxStack, maxLocal);
                        }
                    }
                }
                return mv;
            }

        };
        cr.accept(cv, 0);
        return cw.toByteArray();
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
