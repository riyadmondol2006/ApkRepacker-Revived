/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver (JesusFreke)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.tools.smali.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class handles the complexities of translating a class name into a file name. i.e. dealing with case insensitive
 * file systems, windows reserved filenames, class names with extremely long package/class elements, etc.
 *
 * The types of transformations this class does include:
 * - append a '#123' style numeric suffix if 2 physical representations collide
 * - replace some number of characters in the middle with a '#' character name if an individual path element is too long
 * - append a '#' if an individual path element would otherwise be considered a reserved filename
 *
 * Android copy of smali's (apktool fork b6365a84f4) ClassFileNameHandler: the jar's original is
 * stripped in app/build.gradle. It hands out the same names; what changes is how often it asks the
 * file system. The original canonicalized every candidate name and created every file while holding
 * the lock of the class's directory (3 realpath() walks + 1 create per class), so baksmali's threads
 * queued up there: about half of a decompile's smali time. Here, in a directory this handler created
 * itself (so it holds only entries this handler named), the canonical form of a plain name is known
 * without a system call, and the file is created after the lock is released. Everything else
 * (directories it didn't create, ".", "..", over-long paths) still goes through the file system.
 *
 * Two differences, both where the original broke: in a case-insensitive directory, a name that equals
 * a taken one ignoring case counts as taken (with Linux's realpath() the original gave class foo the
 * name foo.1.smali next to class Foo.1's Foo.1.smali, the same file there, and one class was lost);
 * and package Foo.smali next to class Foo gets its own folder instead of a ClassCastException.
 */
public class ClassFileNameHandler {
    private static final int MAX_FILENAME_LENGTH = 255;
    // How many characters to reserve in the physical filename for numeric suffixes
    // Dex files can currently only have 64k classes, so 5 digits plus 1 for an '.' should
    // be sufficient to handle the case when every class has a conflicting name
    private static final int NUMERIC_SUFFIX_RESERVE = 6;
    // Above this many UTF-8 bytes, a path may exceed PATH_MAX (4096), which canonicalization reports as an error.
    private static final int MAX_FAST_PATH_BYTES = 4000;

    private final int NO_VALUE = -1;
    private final int CASE_INSENSITIVE = 0;
    private final int CASE_SENSITIVE = 1;
    private int forcedCaseSensitivity = NO_VALUE;

    private DirectoryEntry top;
    private String fileExtension;
    private boolean modifyWindowsReservedFilenames;

    public ClassFileNameHandler(File path, String fileExtension) {
        this.top = new DirectoryEntry(path);
        this.fileExtension = fileExtension;
        this.modifyWindowsReservedFilenames = isWindows();
    }

    // for testing
    public ClassFileNameHandler(File path, String fileExtension, boolean caseSensitive,
                                boolean modifyWindowsReservedFilenames) {
        this.top = new DirectoryEntry(path);
        this.fileExtension = fileExtension;
        this.forcedCaseSensitivity = caseSensitive?CASE_SENSITIVE:CASE_INSENSITIVE;
        this.modifyWindowsReservedFilenames = modifyWindowsReservedFilenames;
    }

    private int getMaxFilenameLength() {
        return MAX_FILENAME_LENGTH - NUMERIC_SUFFIX_RESERVE;
    }

    public File getUniqueFilenameForClass(String className) throws IOException {
        //class names should be passed in the normal dalvik style, with a leading L, a trailing ;, and using
        //'/' as a separator.
        if (className.charAt(0) != 'L' || className.charAt(className.length()-1) != ';') {
            throw new RuntimeException("Not a valid dalvik class name");
        }

        int packageElementCount = 1;
        for (int i=1; i<className.length()-1; i++) {
            if (className.charAt(i) == '/') {
                packageElementCount++;
            }
        }

        String[] packageElements = new String[packageElementCount];
        int elementIndex = 0;
        int elementStart = 1;
        for (int i=1; i<className.length()-1; i++) {
            if (className.charAt(i) == '/') {
                //if the first char after the initial L is a '/', or if there are
                //two consecutive '/'
                if (i-elementStart==0) {
                    throw new RuntimeException("Not a valid dalvik class name");
                }

                packageElements[elementIndex++] = className.substring(elementStart, i);
                elementStart = ++i;
            }
        }

        //at this point, we have added all the package elements to packageElements, but still need to add
        //the final class name. elementStart should point to the beginning of the class name

        //this will be true if the class ends in a '/', i.e. Lsome/package/className/;
        if (elementStart >= className.length()-1) {
            throw new RuntimeException("Not a valid dalvik class name");
        }

        packageElements[elementIndex] = className.substring(elementStart, className.length()-1);

        return addUniqueChild(top, packageElements, 0);
    }

    private File addUniqueChild(DirectoryEntry parent, String[] packageElements, int packageElementIndex)
            throws IOException {
        if (packageElementIndex == packageElements.length - 1) {
            FileEntry fileEntry = new FileEntry(parent, packageElements[packageElementIndex] + fileExtension);
            parent.addChild(fileEntry);

            String physicalName = fileEntry.getPhysicalName();

            // the physical name should be set when adding it as a child to the parent
            assert  physicalName != null;

            File file = new File(parent.file, physicalName);
            if (fileEntry.createLater) {
                // Outside of the directory's lock; the name is reserved already.
                file.createNewFile();
            }
            return file;
        } else {
            DirectoryEntry directoryEntry = parent.getOrAddDirectory(packageElements[packageElementIndex]);
            return addUniqueChild(directoryEntry, packageElements, packageElementIndex+1);
        }
    }

    private static int utf8Length(String str) {
        int utf8Length = 0;
        int i=0;
        while (i<str.length()) {
            int c = str.codePointAt(i);
            utf8Length += utf8Length(c);
            i += Character.charCount(c);
        }
        return utf8Length;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        } else if (codePoint < 0x800) {
            return 2;
        } else if (codePoint < 0x10000) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * Shortens an individual file/directory name, removing the necessary number of code points
     * from the middle of the string such that the utf-8 encoding of the string is at least
     * bytesToRemove bytes shorter than the original.
     *
     * The removed codePoints in the middle of the string will be replaced with a # character.
     */
    static String shortenPathComponent(String pathComponent, int bytesToRemove) {
        // We replace the removed part with a #, so we need to remove 1 extra char
        bytesToRemove++;

        int[] codePoints;
        try {
            IntBuffer intBuffer = ByteBuffer.wrap(pathComponent.getBytes("UTF-32BE")).asIntBuffer();
            codePoints = new int[intBuffer.limit()];
            intBuffer.get(codePoints);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }

        int midPoint = codePoints.length/2;

        int firstEnd = midPoint; // exclusive
        int secondStart = midPoint+1; // inclusive

        int bytesRemoved = utf8Length(codePoints[midPoint]);

        // if we have an even number of codepoints, start by removing both middle characters,
        // unless just removing the first already removes enough bytes
        if (((codePoints.length % 2) == 0) && bytesRemoved < bytesToRemove) {
            bytesRemoved += utf8Length(codePoints[secondStart]);
            secondStart++;
        }

        while ((bytesRemoved < bytesToRemove) &&
                (firstEnd > 0 || secondStart < codePoints.length)) {
            if (firstEnd > 0) {
                firstEnd--;
                bytesRemoved += utf8Length(codePoints[firstEnd]);
            }

            if (bytesRemoved < bytesToRemove && secondStart < codePoints.length) {
                bytesRemoved += utf8Length(codePoints[secondStart]);
                secondStart++;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i=0; i<firstEnd; i++) {
            sb.appendCodePoint(codePoints[i]);
        }
        sb.append('#');
        for (int i=secondStart; i<codePoints.length; i++) {
            sb.appendCodePoint(codePoints[i]);
        }

        return sb.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static Pattern reservedFileNameRegex = Pattern.compile("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$",
            Pattern.CASE_INSENSITIVE);
    private static boolean isReservedFileName(String className) {
        return reservedFileNameRegex.matcher(className).matches();
    }

    /** True when the canonical form of [dir]/[name] is [dir]/[name] whenever [dir] holds no symlink called [name]. */
    private static boolean isPlainName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..") && name.indexOf('/') < 0 &&
                name.indexOf('\0') < 0;
    }

    /**
     * Over-approximates "same name on a case-insensitive file system" (Android's casefolded f2fs/ext4
     * also ignores Unicode normalization: é and e + U+0301 are one name there).
     */
    private static String foldCase(String name) {
        for (int i=0; i<name.length(); i++) {
            if (name.charAt(i) >= 0x80) {
                String folded = Normalizer.normalize(name, Normalizer.Form.NFD).toUpperCase(Locale.ROOT)
                        .toLowerCase(Locale.ROOT);
                return Normalizer.normalize(folded, Normalizer.Form.NFD);
            }
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private abstract class FileSystemEntry {
        public final DirectoryEntry parent;
        public final String logicalName;
        protected String physicalName = null;

        private FileSystemEntry(DirectoryEntry parent, String logicalName) {
            this.parent = parent;
            this.logicalName = logicalName;
        }

        public String getNormalizedName(boolean preserveCase) {
            return normalizeName(parent, logicalName, preserveCase);
        }

        public String getPhysicalName() {
            return physicalName;
        }

        public void setSuffix(int suffix) throws IOException {
            if (suffix < 0 || suffix > 99999) {
                throw new IllegalArgumentException("suffix must be in [0, 100000)");
            }

            if (this.physicalName != null) {
                throw new IllegalStateException("The suffix can only be set once");
            }

            String physicalName = getPhysicalNameWithSuffix(suffix);
            File file = new File(parent.file, physicalName).getCanonicalFile();
            this.physicalName = file.getName();
            createIfNeeded();
        }

        /** setSuffix() for a name that the parent knows to be its own canonical form. */
        public void setPlainPhysicalName(String physicalName) {
            if (this.physicalName != null) {
                throw new IllegalStateException("The suffix can only be set once");
            }
            this.physicalName = physicalName;
            createPlainIfNeeded();
        }

        public abstract String getPhysicalNameWithSuffix(int suffix);
        protected abstract void createIfNeeded() throws IOException;
        protected abstract void createPlainIfNeeded();
    }

    private String normalizeName(DirectoryEntry parent, String logicalName, boolean preserveCase) {
        String elementName = logicalName;
        if (!preserveCase && parent != null && !parent.isCaseSensitive()) {
            elementName = elementName.toLowerCase();
        }

        if (modifyWindowsReservedFilenames) {
            boolean reservedFileName = isReservedFileName(elementName);
            if (reservedFileName) {
                elementName = addSuffixBeforeExtension(elementName, "#");
            }
        }

        int utf8Length = utf8Length(elementName);
        if (utf8Length > getMaxFilenameLength()) {
            elementName = shortenPathComponent(elementName, utf8Length - getMaxFilenameLength());
        }
        return elementName;
    }

    private class DirectoryEntry extends FileSystemEntry {
        private File file = null;
        private int caseSensitivity = forcedCaseSensitivity;

        // maps a normalized (but not suffixed) entry name to 1 or more FileSystemEntries.
        // Each FileSystemEntry asociated with a normalized entry name must have a distinct
        // physical name
        private final Multimap<String, FileSystemEntry> children = ArrayListMultimap.create();
        private final Map<String, FileSystemEntry> physicalToEntry = new HashMap<>();
        private final Map<String, Integer> lastSuffixMap = new HashMap<>();

        // The canonical form of file, which the canonical paths of new children start with.
        private File canonicalFile = null;
        // UTF-8 length of canonicalFile's path.
        private int canonicalPathBytes;
        // Created empty by this handler (or found empty): every entry in it is a child named here.
        private boolean fresh = false;
        // foldCase() of every physical name, for case-insensitive directories.
        private final Set<String> foldedPhysicalNames = new HashSet<>();

        public DirectoryEntry(File path) {
            super(null, path.getName());
            file = path;
            physicalName = file.getName();
        }

        public DirectoryEntry(DirectoryEntry parent, String logicalName) {
            super(parent, logicalName);
        }

        /** addChild(new DirectoryEntry(this, logicalName)), without building the entry when it exists already. */
        public synchronized DirectoryEntry getOrAddDirectory(String logicalName) throws IOException {
            String normalizedChildName = normalizeName(this, logicalName, false);
            for (FileSystemEntry childEntry: children.get(normalizedChildName)) {
                if (childEntry instanceof DirectoryEntry && childEntry.logicalName.equals(logicalName)) {
                    return (DirectoryEntry) childEntry;
                }
            }
            return (DirectoryEntry) addChild(new DirectoryEntry(this, logicalName));
        }

        public synchronized FileSystemEntry addChild(FileSystemEntry entry) throws IOException {
            String normalizedChildName = entry.getNormalizedName(false);
            Collection<FileSystemEntry> entries = children.get(normalizedChildName);
            if (entry instanceof DirectoryEntry) {
                for (FileSystemEntry childEntry: entries) {
                    // (The original also returned a file here, e.g. Foo.smali for package Foo.smali,
                    // which then failed with a ClassCastException.)
                    if (childEntry instanceof DirectoryEntry && childEntry.logicalName.equals(entry.logicalName)) {
                        return childEntry;
                    }
                }
            }
            if (parent == null && canonicalFile == null) {
                canonicalFile = file.getCanonicalFile();
                canonicalPathBytes = utf8Length(canonicalFile.getPath());
                String[] list = file.list();
                fresh = list == null ? !file.exists() : list.length == 0;
            }

            Integer lastSuffix = lastSuffixMap.get(normalizedChildName);
            if (lastSuffix == null) {
                lastSuffix = -1;
            }

            int suffix = lastSuffix;
            String entryPhysicalName;
            boolean plain;
            while (true) {
                suffix++;

                String candidate = entry.getPhysicalNameWithSuffix(suffix);
                plain = fresh && isPlainName(candidate) &&
                        canonicalPathBytes + 1 + utf8Length(candidate) < MAX_FAST_PATH_BYTES;
                if (!isCaseSensitive() && foldedPhysicalNames.contains(foldCase(candidate))) {
                    // Another entry here has this name in other letter case: the same file on this file
                    // system, whether or not canonicalizing would have returned that entry's name.
                    continue;
                }
                if (plain) {
                    entryPhysicalName = candidate;
                } else {
                    File entryFile = new File(file, candidate);
                    entryPhysicalName = entryFile.getCanonicalFile().getName();
                }

                if (!physicalToEntry.containsKey(entryPhysicalName)) {
                    break;
                }
            }
            if (plain) {
                entry.setPlainPhysicalName(entryPhysicalName);
            } else {
                entry.setSuffix(suffix);
            }
            lastSuffixMap.put(normalizedChildName, suffix);
            physicalToEntry.put(entry.getPhysicalName(), entry);
            foldedPhysicalNames.add(foldCase(entry.getPhysicalName()));
            entries.add(entry);
            return entry;
        }

        @Override
        public String getPhysicalNameWithSuffix(int suffix) {
            if (suffix > 0) {
                return getNormalizedName(true) + "." + Integer.toString(suffix);
            }
            return getNormalizedName(true);
        }

        @Override
        protected void createIfNeeded() throws IOException {
            String physicalName = getPhysicalName();
            if (parent != null && physicalName != null) {
                file = new File(parent.file, physicalName).getCanonicalFile();
                setCanonical(file, file.mkdirs());
            }
        }

        @Override
        protected void createPlainIfNeeded() {
            String physicalName = getPhysicalName();
            if (parent != null && physicalName != null) {
                file = new File(parent.canonicalFile, physicalName);
                setCanonical(file, file.mkdirs());
            }
        }

        private void setCanonical(File canonicalFile, boolean created) {
            this.canonicalFile = canonicalFile;
            this.canonicalPathBytes = utf8Length(canonicalFile.getPath());
            this.fresh = created;
        }

        protected boolean isCaseSensitive() {
            if (getPhysicalName() == null || file == null) {
                throw new IllegalStateException("Must call setSuffix() first");
            }

            if (caseSensitivity != NO_VALUE) {
                return caseSensitivity == CASE_SENSITIVE;
            }

            File path = file;
            if (path.exists() && path.isFile()) {
                if (!path.delete()) {
                    throw new ExceptionWithContext("Can't delete %s to make it into a directory",
                            path.getAbsolutePath());
                }
            }

            if (!path.exists() && !path.mkdirs()) {
                throw new ExceptionWithContext("Couldn't create directory %s", path.getAbsolutePath());
            }

            try {
                boolean result = PathUtil.testCaseSensitivity(path);
                caseSensitivity = result?CASE_SENSITIVE:CASE_INSENSITIVE;
                return result;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private class FileEntry extends FileSystemEntry {
        // Set when the file is still to be created, by addUniqueChild() once the parent's lock is released.
        boolean createLater = false;

        private FileEntry(DirectoryEntry parent, String logicalName) {
            super(parent, logicalName);
        }

        @Override
        public String getPhysicalNameWithSuffix(int suffix) {
            if (suffix > 0) {
                return addSuffixBeforeExtension(getNormalizedName(true), '.' + Integer.toString(suffix));
            }
            return getNormalizedName(true);
        }

        @Override
        protected void createIfNeeded() throws IOException {
            String physicalName = getPhysicalName();
            if (parent != null && physicalName != null) {
                File file = new File(parent.file, physicalName).getCanonicalFile();
                file.createNewFile();
            }
        }

        @Override
        protected void createPlainIfNeeded() {
            createLater = parent != null && getPhysicalName() != null;
        }
    }

    private static String addSuffixBeforeExtension(String pathElement, String suffix) {
        int extensionStart = pathElement.lastIndexOf('.');

        StringBuilder newName = new StringBuilder(pathElement.length() + suffix.length() + 1);
        if (extensionStart < 0) {
            newName.append(pathElement);
            newName.append(suffix);
        } else {
            newName.append(pathElement.subSequence(0, extensionStart));
            newName.append(suffix);
            newName.append(pathElement.subSequence(extensionStart, pathElement.length()));
        }
        return newName.toString();
    }
}
