/*
 * Copyright (c) 2009-2013 Panxiaobo
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pxb.android.arsc

import pxb.android.axml.Util
import java.io.File
import java.io.IOException

/**
 * dump an arsc file
 *
 * @author bob
 */
object ArscDumper {
    @JvmStatic
    fun dump(pkgs: List<Pkg>) {
        for (x in pkgs.indices) {
            val pkg = pkgs[x]

            println(
                String.format(
                    "  Package %d id=%d name=%s typeCount=%d", x, pkg.id, pkg.name,
                    pkg.types.size
                )
            )
            for (type in pkg.types.values) {
                println(String.format("    type %d %s", type.id - 1, type.name))

                val resPrefix = (pkg.id shl 24) or (type.id shl 16)
                for (i in 0 until type.specs!!.size) {
                    val spec = type.getSpec(i)
                    println(
                        String.format(
                            "      spec 0x%08x 0x%08x %s", resPrefix or spec.id, spec.flags,
                            spec.name
                        )
                    )
                }
                for (i in 0 until type.configs.size) {
                    val config = type.configs[i]
                    println("      config")

                    val entries: List<ResEntry> = ArrayList(config.resources.values)
                    for (j in entries.indices) {
                        val entry = entries[j]
                        println(
                            String.format(
                                "        resource 0x%08x %-20s: %s",
                                resPrefix or entry.spec.id, entry.spec.name, entry.value
                            )
                        )
                    }
                }
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun main(vararg args: String) {
        if (args.isEmpty()) {
            System.err.println("asrc-dump file.arsc")
            return
        }
        val data = Util.readFile(File(args[0]))
        val pkgs = ArscParser(data).parse()

        dump(pkgs)
    }
}
