package com.riyadm.patchengine.rules

import android.content.Context
import android.util.SparseIntArray
import com.riyadm.apkrepacker.R
import com.riyadm.patchengine.LinedReader
import com.riyadm.patchengine.PatchRule
import com.riyadm.patchengine.ProjectHelper
import com.riyadm.patchengine.interfaces.IPatchContext
import com.riyadm.patchengine.resource.ResourceItem
import com.riyadm.patchengine.resource.ResourceMerger
import com.riyadm.patchengine.utils.RandomHelper
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.closeQuietly
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class PatchRuleMerge : PatchRule() {

    @JvmField
    var replacedIds: SparseIntArray? = null
    private var sourceFile: String? = null

    @Throws(IOException::class)
    override fun parseFrom(linedReader: LinedReader, iPatchContext: IPatchContext) {
        val br = linedReader
        val logger = iPatchContext
        this.startLine = br.currentLine
        var line = br.readLine()
        while (line != null) {
            val line2 = line.trim { it <= ' ' }
            if (strEnd != line2) {
                if (!super.parseAsKeyword(line2, br)) {
                    if (SOURCE == line2) {
                        this.sourceFile = br.readLine()!!.trim { it <= ' ' }
                    } else {
                        logger.error(R.string.patch_error_cannot_parse, br.currentLine, line2)
                    }
                }
                line = br.readLine()
            } else {
                return
            }
        }
    }

    override fun executeRule(projectHelper: ProjectHelper, zipFile: ZipFile, iPatchContext: IPatchContext): String? {
        val activity = projectHelper
        val logger = iPatchContext
        val entry = zipFile.getEntry(this.sourceFile)
        if (entry == null) {
            logger.error(R.string.patch_error_no_entry, this.sourceFile)
            return null
        }
        var input: InputStream? = null
        try {
            input = zipFile.getInputStream(entry)
            // Copy the bundle to a real temp *file*. The old code used IOUtil.makeDir(), which
            // returns a directory path, so FileOutputStream(path) always threw and the whole
            // merge silently did nothing (nothing was ever extracted or renumbered).
            val path = activity.getAppDataPath() + RandomHelper.getRandomString(6)
            FileOutputStream(path).use { fos2 -> IOUtils.copy(input, fos2) }
            mergeIds(activity.getProjectPath() + "/res/values/public.xml", path, activity.mContext)
            addFilesInZip(activity, path, ResourceMerger(this, activity.getProjectPath()), logger)
        } catch (e: Exception) {
            logger.error(R.string.general_error, e.message)
        } catch (th: Throwable) {
            closeQuietly(input)
            throw th
        }
        closeQuietly(input)
        return null
    }

    @Throws(Exception::class)
    private fun mergeIds(curPublicXml: String, zipFilepath: String, context: Context?) {
        var zfile: ZipFile? = null
        var input: InputStream? = null
        var fis: FileInputStream? = null
        try {
            zfile = ZipFile(zipFilepath)
            val entry = zfile.getEntry("res/values/public.xml")
            if (entry != null) {
                input = zfile.getInputStream(entry)
                val addedItems = getResourceItems(input)
                fis = FileInputStream(curPublicXml)
                this.replacedIds = refactorAddedItems(addedItems, getMaxIds(getResourceItems(fis)))
                writeAddedItems(curPublicXml, addedItems)
                return
            }
            throw Exception(context!!.getString(R.string.patch_error_publicxml_notfound))
        } finally {
            closeQuietly(fis)
            closeQuietly(input)
            closeQuietly(zfile)
        }
    }

    @Throws(Exception::class)
    private fun writeAddedItems(curPublicXml: String, addedItems: List<ResourceItem>?) {
        val lines: MutableList<String> = ArrayList()
        for (i in addedItems!!.indices) {
            lines.add(addedItems[i].toString())
        }
        appendResourceLines(curPublicXml, lines)
    }

    @Throws(Exception::class)
    fun appendResourceLines(resourceFile: String, lines: List<String>) {
        var randomFile: RandomAccessFile? = null
        try {
            randomFile = RandomAccessFile(resourceFile, "rw")
            val fileLength = randomFile.length()
            if (fileLength >= 16) {
                randomFile.seek(fileLength - 16)
                val buffer = ByteArray(32)
                val readBytes = randomFile.read(buffer)
                var i = 0
                while (true) {
                    if (i < readBytes) {
                        if (buffer[i].toInt() == 60 && buffer[i + 1].toInt() == 47) {
                            break
                        }
                        i++
                    } else {
                        break
                    }
                }
                randomFile.seek((fileLength - 16) + i.toLong())
                val sb = StringBuilder()
                for (i2 in lines.indices) {
                    sb.append(lines[i2])
                    sb.append("\n")
                }
                sb.append("</resources>")
                randomFile.write(sb.toString().toByteArray(Charset.defaultCharset()))
                return
            }
            throw Exception("File is too small!")
        } finally {
            closeQuietly(randomFile)
        }
    }

    private fun refactorAddedItems(
        addedItems: List<ResourceItem>?,
        type2maxId: MutableMap<String?, Int>
    ): SparseIntArray {
        val replaces = SparseIntArray()
        for (i in addedItems!!.indices) {
            val item = addedItems[i]
            val curMaxId = type2maxId[item.type]
            if (curMaxId != null) {
                val newId = curMaxId + 1
                replaces.put(item.id, newId)
                item.id = newId
                type2maxId[item.type] = newId
            } else {
                val newId = ((getMaxType(type2maxId) + 1) shl 16) + 2130706432
                replaces.put(item.id, newId)
                item.id = newId
                type2maxId[item.type] = newId
            }
        }
        return replaces
    }

    private fun getMaxType(type2maxId: Map<String?, Int>): Int {
        var maxType = 0
        for (v in type2maxId.values) {
            val curType = v and 16711680
            if (curType > maxType) {
                maxType = curType
            }
        }
        return maxType shr 16
    }

    private fun getMaxIds(items: List<ResourceItem>?): MutableMap<String?, Int> {
        val maxIds: MutableMap<String?, Int> = HashMap()
        var drawableMaxId = 0
        var layoutMaxId = 0
        var stringMaxId = 0
        for (item in items!!) {
            if ("drawable" == item.type) {
                if (item.id > drawableMaxId) {
                    drawableMaxId = item.id
                }
            } else if ("layout" == item.type) {
                if (item.id > layoutMaxId) {
                    layoutMaxId = item.id
                }
            } else if ("string" != item.type) {
                val curMax = maxIds[item.type]
                if (curMax == null || item.id > curMax) {
                    maxIds[item.type] = item.id
                }
            } else if (item.id > stringMaxId) {
                stringMaxId = item.id
            }
        }
        maxIds["drawable"] = drawableMaxId
        maxIds["layout"] = layoutMaxId
        maxIds["string"] = stringMaxId
        return maxIds
    }

    @Throws(IOException::class)
    private fun getResourceItems(input: InputStream?): List<ResourceItem>? {
        if (input == null) {
            return null
        }
        // Parse every <public type=".." name=".." id="0x.." /> entry out of a public.xml. The
        // original left this as a stub returning null, so mergeIds() NPE'd and merging never ran.
        val result: MutableList<ResourceItem> = ArrayList()
        try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
            doc.documentElement.normalize()
            val nodeList = doc.getElementsByTagName("public")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    result.add(
                        ResourceItem(
                            element.getAttribute("type"),
                            element.getAttribute("name"),
                            ResourceItem.string2Id(element.getAttribute("id"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw IOException(e)
        }
        return result
    }

    override fun isValid(iPatchContext: IPatchContext): Boolean {
        if (this.sourceFile != null) {
            return true
        }
        iPatchContext.error(R.string.patch_error_no_source_file)
        return false
    }

    override fun isSmaliNeeded(): Boolean {
        return true
    }

    companion object {
        private const val SOURCE = "SOURCE:"
        private const val strEnd = "[/MERGE]"
    }
}
