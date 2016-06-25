/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */

package com.dodola.rocoofix.utils.classref;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A zip element.
 */
class ArchivePathElement implements ClassPathElement {

    static class DirectoryEntryException extends IOException {
    }

    private final ZipFile archive;

    public ArchivePathElement(ZipFile archive) {
        this.archive = archive;
    }

    @Override
    public InputStream open(String path) throws IOException {
        ZipEntry entry = archive.getEntry(path);
        if (entry == null) {
            throw new FileNotFoundException("File \"" + path + "\" not found");
        } else if (entry.isDirectory()) {
            throw new DirectoryEntryException();
        } else {
            return archive.getInputStream(entry);
        }
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }

    @Override
    public Iterable<String> list() {
        return new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    Enumeration<? extends ZipEntry> delegate = archive.entries();
                    ZipEntry next = null;

                    @Override
                    public boolean hasNext() {
                        while (next == null && delegate.hasMoreElements()) {
                            next = delegate.nextElement();
                            if (next.isDirectory()) {
                                next = null;
                            }
                        }
                        return next != null;
                    }

                    @Override
                    public String next() {
                        if (hasNext()) {
                            String name = next.getName();
                            next = null;
                            return name;
                        } else {
                            throw new NoSuchElementException();
                        }
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

}
