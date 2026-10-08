package com.riyadm.apkrepacker.ide.editor.lexer

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.util.Log

import org.apache.commons.io.IOUtils
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedList
import java.util.Locale

object Packages {
    private val baseTypes: MutableMap<String, Member> = LinkedHashMap()
    private val types: MutableMap<String, Member> = LinkedHashMap()
    private val allTypes: MutableMap<String, Member> = LinkedHashMap()
    private val EMPTY: List<String> = ArrayList(0)
    private val memberComparator: Comparator<String> = object : Comparator<String> {
        override fun compare(p1: String, p2: String): Int {
            if (p1.contains(":") && p2.contains("("))
                return -1
            if (p1.contains("(") && p2.contains(":"))
                return 1
            return p1.lowercase(Locale.getDefault()).compareTo(
                p2.lowercase(Locale.getDefault())
            )
        }
    }

    @JvmStatic
    fun load(cacheDir: File?, projectPath: String): Boolean {
        @SuppressLint("DefaultLocale")
        val t = String.format("%d.json", projectPath.hashCode())
        val c = File(cacheDir, t)
        if (c.exists()) {
            try {
                val i: InputStream = FileInputStream(c)
                val data = IOUtils.toString(i, StandardCharsets.UTF_8)
                val json = JSONObject(data)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val m = Member(json.getJSONObject(key))
                    types[key] = m
                }
                done()
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }

    @JvmStatic
    fun save(cacheDir: File?, projectPath: String) {
        val t = String.format("%d.json", projectPath.hashCode())
        val c = File(cacheDir, t)
        try {
            val o: OutputStream = FileOutputStream(c)
            val json = JSONObject()
            for ((key, value) in types) {
                json.put(key, value.toJson())
            }
            val data = json.toString(1)
            IOUtils.write(data, o)
            o.close()
        } catch (e: Exception) {
            Log.i("APKTOOL", e.message!!)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadDex(assets: AssetManager) {
        val dex = assets.open("android.dex")
        val data = IOUtils.toByteArray(dex)
        val dexFile = DexBackedDexFile(Opcodes.forApi(29), data)
        setBaseDex(dexFile)
    }

    @JvmStatic
    @Synchronized
    fun addClass(type: String, m: Member, edit: Boolean) {
        if (edit)
            allTypes[type] = m
        else
            types[type] = m
    }

    private fun addClass(cls: ClassDef, types: MutableMap<String, Member>) {
        val type = cls.type
        val members: MutableList<String> = LinkedList()
        val privateFlag = AccessFlags.PRIVATE.value
        val sb = StringBuilder(100)
        for (field in cls.fields) {
            if ((field.accessFlags and privateFlag) > 0)
                continue
            sb.append(field.name)
            sb.append(':')
            sb.append(field.type)
            members.add(sb.toString())
            sb.setLength(0)
        }
        for (method in cls.methods) {
            if ((method.accessFlags and privateFlag) > 0)
                continue
            if (method.name == "<clinit>")
                continue
            sb.append(method.name)
            sb.append('(')
            for (s in method.parameterTypes)
                sb.append(s)
            sb.append(')')
            sb.append(method.returnType)
            members.add(sb.toString())
            sb.setLength(0)
        }
        val m = Member()
        m.superType = cls.superclass
        m.impls = cls.interfaces
        m.members = members
        types[type] = m
    }

    @JvmStatic
    fun setBaseDex(dex: DexFile) {
        baseTypes.clear()
        for (cls in dex.classes)
            addClass(cls, baseTypes)
    }

    @JvmStatic
    fun reset() {
        types.clear()
        allTypes.clear()
    }

    private fun findParent(m: Member, types: Map<String, Member>, members: MutableList<String>) {
        findParent(m.superType, members, types)
        for (impl in m.impls!!)
            findParent(impl, members, types)
        Collections.sort(members, memberComparator)
    }

    private fun findParent(superType: String?, members: MutableList<String>, types: Map<String, Member>) {
        if (superType == null)
            return
        val m = get(superType) ?: return
        findParent(m, types, members)
        for (mm in m.members!!) {
            if (members.contains(mm))
                continue
            if (mm.startsWith("<init>("))
                continue
            members.add(mm)
        }
    }

    @JvmStatic
    fun getTypes(): Iterable<String> {
        return allTypes.keys
    }

    @JvmStatic
    fun getMembers(type: String): List<String> {
        var type = type
        if (type.startsWith("["))
            type = "Ljava/lang/Object;"
        synchronized(allTypes) {
            val m = get(type) ?: return EMPTY
            val ms: MutableList<String> = ArrayList(m.members!!)
            findParent(m, types, ms)
            return ms
        }
    }

    @JvmStatic
    fun done() {
        val allTypes = Packages.allTypes
        synchronized(allTypes) {
            allTypes.clear()
            allTypes.putAll(baseTypes)
            allTypes.putAll(types)
        }
    }

    private fun get(type: String): Member? {
        return allTypes[type]
    }

    @JvmStatic
    fun isEmpty(): Boolean {
        return allTypes.size <= 1
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun getString(obj: JSONObject, key: String): String? {
        if (obj.isNull(key))
            return null
        return obj.getString(key)
    }

    class Member {
        @JvmField
        var superType: String? = null
        @JvmField
        var members: List<String>? = null
        @JvmField
        var impls: List<String>? = null

        constructor()

        @Throws(JSONException::class)
        internal constructor(obj: JSONObject) {
            superType = getString(obj, "super")
            val mem = obj.getJSONArray("members")
            val members = LinkedList<String>()
            for (i in 0 until mem.length()) {
                members.add(mem.getString(i))
            }
            this.members = members
            val impl = obj.getJSONArray("impls")
            val impls = LinkedList<String>()
            for (i in 0 until impl.length()) {
                impls.add(impl.getString(i))
            }
            this.impls = impls
        }

        @Throws(JSONException::class)
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("super", superType)
            obj.put("members", JSONArray(members))
            obj.put("impls", JSONArray(impls))
            return obj
        }
    }
}
