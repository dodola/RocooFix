/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix

import com.android.SdkConstants
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.dodola.rocoofix.utils.NuwaProcessor
import com.dodola.rocoofix.utils.NuwaSetUtils
import com.dodola.rocoofix.utils.RocooUtils
import com.google.common.collect.Sets
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.DefaultDomainObjectSet
import proguard.gradle.ProGuardTask

class RocooFixPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "rocoo_fix";

    private static final String MAPPING_TXT = "mapping.txt"
    private static final String HASH_TXT = "hash.txt"

    @Override
    public void apply(Project project) {
        DefaultDomainObjectSet<ApplicationVariant> variants
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            variants = project.android.applicationVariants;

            project.extensions.create(EXTENSION_NAME, RocooFixExtension);

            applyTask(project, variants);
        }
    }

    private void applyTask(Project project, DomainObjectCollection<BaseVariant> variants) {

        project.afterEvaluate {
            RocooFixExtension rocooConfig = RocooFixExtension.getConfig(project);
            def includePackage = rocooConfig.includePackage
            def excludeClass = rocooConfig.excludeClass
            if (rocooConfig.enable) {

                variants.all { variant ->

                    def preDexTask = project.tasks.findByName(RocooUtils.getPreDexTaskName(project, variant))
                    def dexTask = project.tasks.findByName(RocooUtils.getDexTaskName(project, variant))
                    def proguardTask = project.tasks.findByName(RocooUtils.getProGuardTaskName(project, variant))
//                    def processManifestTask = project.tasks.findByName(RocooUtils.getProcessManifestTaskName(project, variant))

                    println("manifests:"+variant.outputs.processManifest.manifestOutputFile)
                    def manifestFile = variant.outputs.processManifest.manifestOutputFile[0]

                    Map hashMap = applyMapping(project, variant, proguardTask)

                    def dirName = variant.dirName
                    def rocooFixRootDir = new File("${project.projectDir}/rocoofix/version" + variant.getVersionCode())//project/rocoofix/version11
                    def outputDir = new File("${rocooFixRootDir}/${dirName}")//project/rocoofix/version11/debug
                    def patchDir = new File("${outputDir}/patch")//project/rocoofix/version11/debug/patch
                    def hashFile = new File(outputDir, "${HASH_TXT}")//project/rocoofix/version11/debug/hash.txt
                    println("=========" + rocooFixRootDir);
                    println("=========" + outputDir);
                    println("=========" + patchDir);
                    println("=========" + hashFile);
                    println("==========" + variant.getVersionCode())

                    if (!rocooFixRootDir.exists()) {
                        rocooFixRootDir.mkdirs();
                    }
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                    if (!patchDir.exists()) {
                        patchDir.mkdirs();
                    }

                    def rocooPatchTaskName = "applyRocoo${variant.name.capitalize()}Patch"
                    project.task(rocooPatchTaskName) << {
                        if (patchDir) {
                            println("==============" + rocooPatchTaskName)
                            RocooUtils.makeDex(project, patchDir)
                        }
                    }
                    def rocooPatchTask = project.tasks[rocooPatchTaskName]

                    Closure prepareClosure = {
                        if (rocooConfig.excludeClass == null) {
                            rocooConfig.excludeClass = Sets.newHashSet();
                        }
                        def applicationClassName = RocooUtils.getApplication(manifestFile);
                        if (applicationClassName != null) {
                            applicationClassName = applicationClassName.replace(".", "/") + SdkConstants.DOT_CLASS
                            rocooConfig.excludeClass.add(applicationClassName)
                        }

                        if (rocooConfig.excludePackage == null) {
                            rocooConfig.excludePackage = Sets.newHashSet();
                        }
                        rocooConfig.excludePackage.add("android/support/")

                        outputDir.mkdirs()
                        if (!hashFile.exists()) {
                            hashFile.createNewFile()
                        } else {
                            hashFile.delete()
                            hashFile.createNewFile()
                        }
                    }

                    Closure copyMappingClosure = {
                        println("-------------------proguardTask:" + proguardTask)

                        if (proguardTask) {
                            def mapFile = new File("${project.buildDir}/outputs/mapping/${variant.dirName}/mapping.txt")
                            println("-------------------mapFile:" + mapFile)
                            if (mapFile.exists()) {
                                println("-------------------copy mapping file:")

                                def newMapFile = new File("${rocooFixRootDir}/${dirName}/mapping.txt");
                                FileUtils.copyFile(mapFile, newMapFile)
                            }
                        }
                    }

                    println("-------------------preDexTask:" + preDexTask)

                    if (preDexTask) {
                        def rocooJarBeforePreDex = "rocooJarBeforePreDex${variant.name.capitalize()}"
                        project.task(rocooJarBeforePreDex) << {
                            Set<File> inputFiles = preDexTask.inputs.files.files

                            inputFiles.each { inputFile ->
                                def path = inputFile.absolutePath
                                if (NuwaProcessor.shouldProcessPreDexJar(path)) {
                                    NuwaProcessor.processJar(hashFile, inputFile, patchDir, hashMap, includePackage, excludeClass)
                                }
                            }
                        }
                        def rocooJarBeforePreDexTask = project.tasks[rocooJarBeforePreDex]
                        rocooJarBeforePreDexTask.dependsOn preDexTask.taskDependencies.getDependencies(preDexTask)
                        preDexTask.dependsOn rocooJarBeforePreDexTask

                        rocooJarBeforePreDexTask.doFirst(prepareClosure)

                        def rocooClassBeforeDex = "rocooClassBeforeDex${variant.name.capitalize()}"
                        project.task(rocooClassBeforeDex) << {
                            Set<File> inputFiles = dexTask.inputs.files.files
                            inputFiles.each { inputFile ->
//                                NuwaProcessor.processClasses(inputFile, includePackage, excludeClass, dirName, hashMap, patchDir)
                                def path = inputFile.absolutePath
                                if (path.endsWith(".class") && !path.contains("/R\$") && !path.endsWith("/R.class") && !path.endsWith("/BuildConfig.class")) {
                                    if (NuwaSetUtils.isIncluded(path, includePackage)) {
                                        if (!NuwaSetUtils.isExcluded(path, excludeClass)) {
                                            def bytes = NuwaProcessor.processClass(inputFile)
                                            path = path.split("${dirName}/")[1]
                                            def hash = DigestUtils.shaHex(bytes)
                                            hashFile.append(RocooUtils.format(path, hash))

                                            if (RocooUtils.notSame(hashMap, path, hash)) {
                                                def file = new File("${patchDir}/${path}")
                                                file.getParentFile().mkdirs()
                                                if (!file.exists()) {
                                                    file.createNewFile()
                                                }
                                                FileUtils.writeByteArrayToFile(file, bytes)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        def rocooClassBeforeDexTask = project.tasks[rocooClassBeforeDex]
                        rocooClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        rocooClassBeforeDexTask.doLast(copyMappingClosure)
                        rocooPatchTask.dependsOn rocooClassBeforeDexTask
                        dexTask.dependsOn rocooPatchTask
                    } else if (dexTask != null) {
                        def rocooJarBeforeDex = "rocooJarBeforeDex${variant.name.capitalize()}"
                        project.task(rocooJarBeforeDex) << {
                            Set<File> inputFiles = RocooUtils.getDexTaskInputFiles(project, variant, dexTask)

                            inputFiles.each { inputFile ->

                                def path = inputFile.absolutePath
                                println("rocoojarBefore----->" + path)
                                if (path.endsWith(SdkConstants.DOT_JAR)) {
                                    NuwaProcessor.processJar(hashFile, inputFile, patchDir, hashMap, includePackage, excludeClass)
                                } else if (inputFile.isDirectory()) {
                                    //intermediates/classes/debug
                                    def extensions = [SdkConstants.EXT_CLASS] as String[]

                                    def inputClasses = FileUtils.listFiles(inputFile, extensions, true);
                                    println(inputClasses)
                                    inputClasses.each {
                                        inputClassFile ->

                                            def classPath = inputClassFile.absolutePath
                                            if (classPath.endsWith(".class") && !classPath.contains("/R\$") && !classPath.endsWith("/R.class") && !classPath.endsWith("/BuildConfig.class")) {
                                                if (NuwaSetUtils.isIncluded(classPath, includePackage)) {
                                                    if (!NuwaSetUtils.isExcluded(classPath, excludeClass)) {
                                                        def bytes = NuwaProcessor.processClass(inputClassFile)
                                                        classPath = classPath.split("${dirName}/")[1]
                                                        def hash = DigestUtils.shaHex(bytes)
                                                        hashFile.append(RocooUtils.format(classPath, hash))
                                                        if (RocooUtils.notSame(hashMap, classPath, hash)) {
                                                            def file = new File("${patchDir}/${classPath}")
                                                            file.getParentFile().mkdirs()
                                                            if (!file.exists()) {
                                                                file.createNewFile()
                                                            }
                                                            FileUtils.writeByteArrayToFile(file, bytes)
                                                        }
                                                    }
                                                }
                                            }

                                    }
                                }
                            }
                        }
                        def rocooJarBeforeDexTask = project.tasks[rocooJarBeforeDex]
                        println("-------------------rocooClassTask:" + rocooJarBeforeDexTask)

                        rocooJarBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        rocooJarBeforeDexTask.doFirst(prepareClosure)
                        rocooJarBeforeDexTask.doLast(copyMappingClosure)
                        rocooPatchTask.dependsOn rocooJarBeforeDexTask
                        dexTask.dependsOn rocooPatchTask
                    }
                }
            }
        }
    }


    private static Map applyMapping(Project project, BaseVariant variant, Task proguardTask) {

        Map hashMap
        RocooFixExtension rocooConfig = RocooFixExtension.getConfig(project);
        if (rocooConfig.preVersionPath != null) {

            def preVersionPath = new File("${project.projectDir}/rocoofix/version" + rocooConfig.preVersionPath)
//project/rocoofix/version11

            if (preVersionPath.exists()) {
                def mappingFile = new File("${preVersionPath}/${variant.dirName}/${MAPPING_TXT}")
                if (mappingFile.exists()) {
                    if (proguardTask instanceof ProGuardTask) {
                        if (mappingFile.exists()) {
                            proguardTask.applymapping(mappingFile)
                        }
                    } else {//兼容gradle1.4 增加了transformapi
                        def manager = variant.variantData.getScope().transformManager;
                        def proguardTransform = manager.transforms.find {
                            it.class.name == ProGuardTransform.class.name
                        };
                        if (proguardTransform) {
                            proguardTransform.configuration.applyMapping = mappingFile
                        }
                    }
                }
            }
            if (preVersionPath.exists()) {
                def hashFile = new File("${preVersionPath}/${variant.dirName}/${HASH_TXT}")
                hashMap = RocooUtils.parseMap(hashFile)

            }
            return hashMap;
        }
    }

}
