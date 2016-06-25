/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.dodola.rocoofix.utils.classref;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

class Path {

    static ClassPathElement getClassPathElement(File file)
            throws ZipException, IOException {
        if (file.isDirectory()) {
            return new FolderPathElement(file);
        } else if (file.isFile()) {
            return new ArchivePathElement(new ZipFile(file));
        } else if (file.exists()) {
            throw new IOException("\"" + file.getPath() +
                    "\" is not a directory neither a zip file");
        } else {
            throw new FileNotFoundException("File \"" + file.getPath() + "\" not found");
        }
    }

    List<ClassPathElement> elements = new ArrayList<ClassPathElement>();
    private final String definition;
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(40 * 1024);
    private final byte[] readBuffer = new byte[20 * 1024];

    Path(String definition) throws IOException {
        this.definition = definition;
        for (String filePath : definition.split(Pattern.quote(File.pathSeparator))) {
            try {
                addElement(getClassPathElement(new File(filePath)));
            } catch (IOException e) {
                throw new IOException("Wrong classpath: " + e, e);
            }
        }
    }

    private static byte[] readStream(InputStream in, ByteArrayOutputStream baos, byte[] readBuffer)
            throws IOException {
        try {
            for (; ; ) {
                int amt = in.read(readBuffer);
                if (amt < 0) {
                    break;
                }

                baos.write(readBuffer, 0, amt);
            }
        } finally {
            in.close();
        }
        return baos.toByteArray();
    }

    @Override
    public String toString() {
        return definition;
    }

    Iterable<ClassPathElement> getElements() {
        return elements;
    }

    private void addElement(ClassPathElement element) {
        assert element != null;
        elements.add(element);
    }

    synchronized DirectClassFile getClass(String path) throws FileNotFoundException {
        DirectClassFile classFile = null;
        for (ClassPathElement element : elements) {
            try {
                InputStream in = element.open(path);
                try {
                    byte[] bytes = readStream(in, baos, readBuffer);
                    baos.reset();
                    classFile = new DirectClassFile(bytes, path, false);
                    classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
                    break;
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                // search next element
            }
        }
        if (classFile == null) {
            throw new FileNotFoundException("File \"" + path + "\" not found");
        }
        return classFile;
    }
}
