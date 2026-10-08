package com.riyadm.apkrepacker.ui.filemanager.batch

import android.content.Context
import com.riyadm.apkrepacker.R
import com.riyadm.apkrepacker.ui.filemanager.holder.FileHolder
import java.util.LinkedHashMap

class VariableMatcher(variableConfig: VariableConfig) {

    private val variableMap: MutableMap<String, Variable> = LinkedHashMap()

    init {
        this.variableMap["#"] = object : Variable {
            private var startAt = variableConfig.mStartAt
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                var i2: Int
                val length = sb.length
                var i3 = 1
                var i4 = 1
                while (true) {
                    i2 = i + i3
                    if (i2 >= length) {
                        break
                    }
                    val i5 = i4 + 1
                    if (sb[i4 + i] != '#') {
                        break
                    }
                    i3++
                    i4 = i5
                }
                val i6 = this.startAt
                this.startAt = i6 + 1
                sb.replace(i, i2, String.format("%0" + i3 + "d", i6))
                return i3
            }

            override fun describe(): String {
                return "#    - " + context!!.getString(R.string.variable_digit)
            }

            override fun pattern(): String {
                return "#"
            }
        }
        this.variableMap["N"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.name)
            }

            override fun describe(): String {
                return "%N - " + context!!.getString(R.string.variable_file_name_with_extension)
            }

            override fun pattern(): String {
                return "%N"
            }
        }
        this.variableMap["n"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.extension)
            }

            override fun describe(): String {
                return "%n - " + context!!.getString(R.string.variable_file_name)
            }

            override fun pattern(): String {
                return "%n"
            }
        }
        this.variableMap["E"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.extension)
            }

            override fun describe(): String {
                return "%E - " + context!!.getString(R.string.variable_file_extension)
            }

            override fun pattern(): String {
                return "%E"
            }
        }
        this.variableMap["S"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.getFormattedSize(context, false))
            }

            override fun describe(): String {
                return "%S - " + context!!.getString(R.string.variable_file_size)
            }

            override fun pattern(): String {
                return "%S"
            }
        }
        this.variableMap["D"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.getFormattedModificationDate(context).toString())
            }

            override fun describe(): String {
                return "%D - " + context!!.getString(R.string.variable_file_date)
            }

            override fun pattern(): String {
                return "%D"
            }
        }
        this.variableMap["T"] = object : Variable {
            private val context: Context? = variableConfig.mContext
            override fun apply(sb: StringBuilder, i: Int, fileHolder: FileHolder): Int {
                return applyString(sb, i, fileHolder.getFormattedHour(context).toString().replace(':', '-'))
            }

            override fun describe(): String {
                return "%T - " + context!!.getString(R.string.variable_file_time)
            }

            override fun pattern(): String {
                return "%T"
            }
        }
        /*this.a.put("V", new Variable() {
            

            @Override 
            public int apply(StringBuilder sb, int i, FileHolder sEFile) {
                if (!FileTypeHelper.isAPK(sEFile.getName()) || !sEFile.isLocal()) {
                    return 0;
                }
                return VariableMatcher.applyString(sb, i, VariableMatcher.extractPackageInfo(sEFile).versionName);
            }

            @Override 
            public String describe() {
                return "%V - " + ResUtils.getString(R.string.variable_apk_version);
            }

            @Override 
            public String pattern() {
                return "%V";
            }
        });*/
        /*this.a.put("A", new Variable() {
            @Override 
            public int apply(StringBuilder sb, int i, FileHolder sEFile) {
                if (!FileTypeHelper.isAPK(sEFile.getName()) || !sEFile.isLocal()) {
                    return 0;
                }
                return VariableMatcher.applyString(sb, i, VariableMatcher.extractPackageInfo(sEFile).applicationInfo.loadLabel(SEApp.get().getPackageManager()).toString());
            }

            @Override 
            public String describe() {
                return "%A - " + ResUtils.getString(R.string.variable_apk_app_name);
            }

            @Override 
            public String pattern() {
                return "%A";
            }
        });*/
    }

    /*static PackageInfo extractPackageInfo(FileHolder r4) {
        PackageInfo packageInfo = (PackageInfo) r4.getExtra("package_info");
        if (packageInfo != null) {
            return packageInfo;
        }
        PackageInfo packageArchiveInfo = SEApp.get().getPackageManager().getPackageArchiveInfo(r4.getPath(), 0);
        packageArchiveInfo.applicationInfo.sourceDir = r4.getPath();
        packageArchiveInfo.applicationInfo.publicSourceDir = r4.getPath();
        r4.putExtra("package_info", packageArchiveInfo);
        return packageArchiveInfo;
    }*/

    fun getAll(): Collection<Variable> {
        return this.variableMap.values
    }

    fun match(str: String): Variable? {
        return this.variableMap[str]
    }

    companion object {
        @JvmStatic
        fun applyString(sb: StringBuilder, i: Int, str: String): Int {
            sb.replace(i, i + 2, str)
            return str.length
        }
    }
}
