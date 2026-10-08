package com.riyadm.apkrepacker.utils

import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import java.io.File
import java.io.IOException

object Smali2Java {

    @JvmStatic
    @Throws(IOException::class)
    fun translate(dexBuilder: DexBuilder): String? {
        val tmp = File.createTempFile("temp", ".dex")
        //File tmp1 = File.createTempFile("temp", ".arsc");
        try {
            dexBuilder.writeTo(FileDataStore(tmp))
            // ZipFile zipFile = new ZipFile(ProjectItemJson.getApkPatch());
            //  ZipEntry entry = ZipUtils.getEntry(zipFile, "resources.arsc");
            //  InputStream in = zipFile.getInputStream(entry);
            // FileOutputStream fileOutputStream = new  FileOutputStream(tmp1);
            //  IOUtils.copy(in, fileOutputStream);
            val files: MutableList<File> = ArrayList()
            files.add(tmp)
            //  files.add(tmp1);
            val args = JadxArgs()
            args.setSkipResources(true)
            args.setShowInconsistentCode(true)
            args.setInputFiles(files)
            //   args.setOutDirRes(new File(ProjectUtils.getProjectPath()));
            JadxDecompiler(args).use { decompiler ->
                decompiler.load()
                val jClass = decompiler.classes.iterator().next()
                jClass.decompile()
                return jClass.code
            }
        } finally {
            tmp.delete()
            //tmp1.delete();
        }
    }

    @JvmStatic
    fun translate(smali: File?): String? {
        val args = JadxArgs()
        args.setSkipResources(true)
        args.setShowInconsistentCode(true)
        args.setInputFile(smali)
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            val jClass = decompiler.classes.iterator().next()
            jClass.decompile()
            return jClass.code
        }
    }
}
