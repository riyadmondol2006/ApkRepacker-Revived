package com.riyadm.apkrepacker.utils.grep

/**
 * @author https://github.com/drippel/JavaGrep
 */
class GrepBuilder private constructor() {

    private val theGrep: ExtGrep = ExtGrep()

    // Regex options
    fun setRegex(find: String?, regex: Boolean): GrepBuilder {

        theGrep.setRegex(find, regex)

        return this
    }

    fun addRegexFromFiles(vararg files: String): GrepBuilder {
        theGrep.readRegexFromFile(*files)
        return this
    }

    fun addLongRegexFromFiles(vararg files: String): GrepBuilder {
        theGrep.readLongRegexFromFile(arrayOf(*files))
        return this
    }

    fun ignoreCase(): GrepBuilder {
        theGrep.ignoreCase = true
        return this
    }

    fun wordRegex(): GrepBuilder {
        theGrep.wordRegex = true
        return this
    }

    fun lineRegex(): GrepBuilder {
        theGrep.lineRegex = true
        return this
    }

    // Miscellaneous
    fun noMessages(): GrepBuilder {
        theGrep.noMessages = true
        return this
    }

    fun invertMatch(): GrepBuilder {
        theGrep.invertMatch = true
        return this
    }

    // Output control
    fun maxCount(l: Int): GrepBuilder {
        theGrep.maxCount = l
        return this
    }

    fun printByteOffset(): GrepBuilder {
        theGrep.printByteOffset = true
        return this
    }

    fun printLineNumber(): GrepBuilder {
        theGrep.printLineNumber = true
        return this
    }

    fun withFilename(): GrepBuilder {
        theGrep.printFileName = false
        return this
    }

    fun noFilename(): GrepBuilder {
        theGrep.printFileName = false
        return this
    }

    fun onlyMatching(): GrepBuilder {
        theGrep.printMatchOnly = true
        return this
    }

    fun quiet(): GrepBuilder {
        theGrep.quiet = true
        return this

    }

    fun recurseDirectories(): GrepBuilder {
        theGrep.recurseDirectories = true
        return this
    }

    fun setExeption(list: ArrayList<String>): GrepBuilder {
        theGrep.mExtensions = list
        return this
    }

    fun skipDirectories(): GrepBuilder {
        theGrep.skipDirectories = true
        return this
    }

    fun include(vararg includes: String): GrepBuilder {

        theGrep.useInclude = true
        theGrep.includeFilePatterns.addAll(listOf(*includes))
        return this
    }

    fun includeFrom(vararg from: String): GrepBuilder {
        theGrep.useInclude = true
        theGrep.readIncludesFrom(arrayOf(*from))
        return this
    }

    fun exclude(vararg excludes: String): GrepBuilder {
        theGrep.useExclude = true
        theGrep.excludeFilePatterns.addAll(listOf(*excludes))
        return this
    }

    fun excludeFrom(vararg from: String): GrepBuilder {

        theGrep.useExclude = true
        theGrep.readExcludeFrom(arrayOf(*from))
        return this
    }

    fun excludeDir(vararg dirs: String): GrepBuilder {
        theGrep.excludeDirPatterns.addAll(listOf(*dirs))
        return this
    }

    fun printFilesWithoutMatch(): GrepBuilder {
        theGrep.printFilesWithoutMatch = true
        return this
    }

    fun printFileNameOnly(): GrepBuilder {
        theGrep.printFileNameOnly = true
        return this
    }

    fun printCountOnly(): GrepBuilder {
        theGrep.printCountOnly = true
        return this
    }

    // Context control
    fun context(lines: Int): GrepBuilder {

        theGrep.afterContext = lines
        theGrep.beforeContext = lines
        return this
    }

    fun afterContext(lines: Int): GrepBuilder {

        theGrep.afterContext = lines
        return this
    }

    fun beforeContext(lines: Int): GrepBuilder {

        theGrep.beforeContext = lines
        return this

    }

    fun addFile(name: String?): GrepBuilder {

        theGrep.addFile(name)
        return this
    }

    fun build(): ExtGrep {
        return theGrep
    }

    companion object {
        @JvmStatic
        fun start(): GrepBuilder {

            return GrepBuilder()
        }
    }

}
