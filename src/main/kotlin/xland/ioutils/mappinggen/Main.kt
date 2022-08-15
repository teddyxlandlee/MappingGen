package xland.ioutils.mappinggen

import xland.ioutils.mappinggen.cli.CliMappingGenApp
import java.awt.GraphicsEnvironment
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (GraphicsEnvironment.isHeadless() || args.isNotEmpty())
        return ConsoleApp.main(args)
    ServiceLoader.load(GuiMappingGenApp::class.java).iterator().run {
        if (hasNext()) return next().run()
        throw UnsupportedOperationException("GuiMappingGenApp not supported")
    }
}

object ConsoleApp {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty() || "help" == args[0]) {
            println("USAGE: java -jar MappingGen.jar yrn:<path> srg:<path> [fgn:1.17] [moj:null] [dsc:null] [out:null]")
            exitProcess(0)
        }

        val builder = RunArgsHolder.Builder()
        var desc : Path? = null
        var output : Path? = null
        Arrays.stream(args)
            .map { it.split(':', '=', limit = 2) }
            .filter { it.size >= 2 }
            .forEach {
                when(it[0]) {
                    //"moj" -> builder.useMojMaps = it[1].toBoolean()
                    "yrn" -> builder.yarnOrIntermediaryMapping = Paths.get(it[1])
                    "srg" -> builder.srgMapping = Paths.get(it[1])
                    "fgn" -> builder.forgeField = it[1]
                    "moj" -> builder.proguardMapping = Paths.get(it[1])

                    "dsc" -> desc = Paths.get(it[1])
                    "out" -> output = Paths.get(it[1])
                }
            }
        builder.build().run(desc, output)
    }
}

data class RunArgsHolder(//val useMojMaps: Boolean,
                         val yarnOrIntermediaryMapping: Path,
                         val proguardMapping : Path?,
                         val srgMapping: Path,
                         val forgeField: String) {
    class Builder {
        //var useMojMaps: Boolean = false
        var yarnOrIntermediaryMapping: Path? = null
        var srgMapping: Path? = null
        var forgeField: String = "forge17"
        var proguardMapping : Path? = null

        fun build() : RunArgsHolder {
            Objects.requireNonNull(yarnOrIntermediaryMapping, "yrn")
            Objects.requireNonNull(srgMapping, "srg")
            return RunArgsHolder(yarnOrIntermediaryMapping!!, proguardMapping, srgMapping!!, forgeField)
        }
    }

    fun run(descFileFrom : Path?, mappingTo : Path?) {
        if (descFileFrom != null && mappingTo == null)
            throw IllegalArgumentException("not using interaction mode, but didn't specify mappingTo")
        val tree = readMappings(this)
        CliMappingGenApp(this, tree, descFileFrom, mappingTo).run()
    }
}
