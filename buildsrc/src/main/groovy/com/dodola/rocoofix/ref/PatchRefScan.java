package com.dodola.rocoofix.ref;

import com.dodola.rocoofix.utils.NuwaProcessor;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by shoyu666 on 16/7/16.
 */
public class PatchRefScan {

    /**
     *  判断className是否调用了补丁,如果是加入补丁
     * @param className jar包中的一个类
     * @param allref    className类的依赖
     * @param patchClasses  补丁类
     */
    public static void addClassIfRefPatchClass(JarEntry jarEntry, String className, Set<String> allref, Set<String> patchClasses, JarFile alljar, File patchDir) throws Exception{
        for(String ref:allref){
            if(patchClasses.contains(ref+".class")){
                //该类如果引用了补丁
                addClasstoPatch(jarEntry,className,alljar,patchDir);
                break;
            }
        }
    }
    public static void addClasstoPatch(JarEntry jarEntry,String className,JarFile alljar,File patchDir) throws Exception{
        System.out.println("============addClasstoPatch======"+className);
        File entryFile = new File(patchDir,className);
        if(!entryFile.exists()){
            InputStream inputStreamX = alljar.getInputStream(jarEntry);
            byte[] bytes = NuwaProcessor.referHackWhenInit(inputStreamX);
            FileUtils.writeByteArrayToFile(entryFile, bytes);
        }
    }
}
