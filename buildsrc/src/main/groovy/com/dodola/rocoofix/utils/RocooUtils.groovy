/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.google.common.collect.Sets
import groovy.xml.Namespace
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.Task

public class RocooUtils {
    private static final String MAP_SEPARATOR = ":"
    private static final String PATCH_NAME = "patch.jar"

    public static boolean notSame(Map map, String name, String hash) {
        def notSame = false
        if (map) {
            def value = map.get(name)
            if (value) {
                if (!value.equals(hash)) {
                    notSame = true
                }
            } else {
                notSame = true
            }
        }
        return notSame
    }

    public static Map parseMap(File hashFile) {
        def hashMap = [:]
        if (hashFile.exists()) {
            hashFile.eachLine {
                List list = it.split(MAP_SEPARATOR)
                if (list.size() == 2) {
                    hashMap.put(list[0], list[1])
                }
            }
        } else {
            println "$hashFile does not exist"
        }
        return hashMap
    }

    public static format(String path, String hash) {
        return path + MAP_SEPARATOR + hash + "\n"
    }


    public static String getApplication(File manifestFile) {
        def manifest = new XmlParser().parse(manifestFile)
        def androidTag = new Namespace("http://schemas.android.com/apk/res/android", 'android')
        return manifest.application[0].attribute(androidTag.name)
    }

    private static List<String> getFilesHash(String baseDirectoryPath, File directoryFile) {
        List<String> javaFiles = new ArrayList<String>();

        File[] children = directoryFile.listFiles();
        if (children == null) {
            return javaFiles;
        }

        for (final File file : children) {
            if (file.isDirectory()) {
                List<String> tempList = getFilesHash(baseDirectoryPath, file);
                if (!tempList.isEmpty()) {
                    javaFiles.addAll(tempList);
                }
            } else {
                InputStream is = new FileInputStream(file);
                def hash = DigestUtils.shaHex(IOUtils.toByteArray(is))
                javaFiles.add(hash)

                is.close()
            }
        }

        return javaFiles;
    }

    public static makeDex(Project project, File classDir) {
        if (classDir.listFiles() != null && classDir.listFiles().size()) {
            StringBuilder builder = new StringBuilder();

            def baseDirectoryPath = classDir.getAbsolutePath() + File.separator;
            getFilesHash(baseDirectoryPath, classDir).each {
                builder.append(it)
            }
            def hash = DigestUtils.shaHex(builder.toString().bytes)

            def sdkDir

            Properties properties = new Properties()
            File localProps = project.rootProject.file("local.properties")
            if (localProps.exists()) {
                properties.load(localProps.newDataInputStream())
                sdkDir = properties.getProperty("sdk.dir")
            } else {
                sdkDir = System.getenv("ANDROID_HOME")
            }

            if (sdkDir) {
                def cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? '.bat' : ''
                def stdout = new ByteArrayOutputStream()
                project.exec {
                    commandLine "${sdkDir}${File.separator}build-tools${File.separator}${project.android.buildToolsVersion}${File.separator}dx${cmdExt}",
                            '--dex',
                            "--output=${new File(classDir.getParent(), PATCH_NAME).absolutePath}",
                            "${classDir.absolutePath}"
                    standardOutput = stdout
                }
                def error = stdout.toString().trim()
                if (error) {
                    println "dex error:" + error
                }
            } else {
            }
        }
    }

    static String getProcessManifestTaskName(Project project, BaseVariant variant) {
        return "process${variant.name.capitalize()}Manifest"
    }

    static String getProGuardTaskName(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
        } else {
            return "proguard${variant.name.capitalize()}"
        }
    }

    static String getPreDexTaskName(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return ""
        } else {
            return "preDex${variant.name.capitalize()}"
        }
    }

    static String getDexTaskName(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return "transformClassesWithDexFor${variant.name.capitalize()}"
        } else {
            return "dex${variant.name.capitalize()}"
        }
    }


    static Set<File> getDexTaskInputFiles(Project project, BaseVariant variant, Task dexTask) {
        if (dexTask == null) {
            dexTask = project.tasks.findByName(getDexTaskName(project, variant));
        }

        if (isUseTransformAPI(project)) {
            def extensions = [SdkConstants.EXT_JAR] as String[]

            Set<File> files = Sets.newHashSet();

            dexTask.inputs.files.files.each {
                if (it.exists()) {
                    if (it.isDirectory()) {
                        Collection<File> jars = FileUtils.listFiles(it, extensions, true);
                        files.addAll(jars)

                        if (it.absolutePath.toLowerCase().endsWith("intermediates${File.separator}classes${File.separator}${variant.dirName}".toLowerCase())) {
                            files.add(it)
                        }
                    } else if (it.name.endsWith(SdkConstants.DOT_JAR)) {
                        files.add(it)
                    }
                }
            }
            return files
        } else {
            return dexTask.inputs.files.files;
        }
    }


    public static boolean isUseTransformAPI(Project project) {
//        println("==========gradleVersion:" + compareVersionName("1.9.0", "1.4.0"))
        return compareVersionName(project.gradle.gradleVersion, "1.4.0") >= 0;
    }


    private static int compareVersionName(String str1, String str2) {
        String[] thisParts = str1.split("-")[0].split("\\.");
        String[] thatParts = str2.split("-")[0].split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart)
                return -1;
            if (thisPart > thatPart)
                return 1;
        }
        return 0;
    }

}