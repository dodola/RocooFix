/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */

package com.dodola.rocoofix.utils.classref;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * A folder element.
 */
class FolderPathElement implements ClassPathElement {

    private File baseFolder;

    public FolderPathElement(File baseFolder) {
        this.baseFolder = baseFolder;
    }

    @Override
    public InputStream open(String path) throws FileNotFoundException {
        return new FileInputStream(new File(baseFolder,
                path.replace(SEPARATOR_CHAR, File.separatorChar)));
    }

    @Override
    public void close() {
    }

    @Override
    public Iterable<String> list() {
        ArrayList<String> result = new ArrayList<String>();
        collect(baseFolder, "", result);
        return result;
    }

    private void collect(File folder, String prefix, ArrayList<String> result) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                collect(file, prefix + SEPARATOR_CHAR + file.getName(), result);
            } else {
                result.add(prefix + SEPARATOR_CHAR + file.getName());
            }
        }
    }

}
