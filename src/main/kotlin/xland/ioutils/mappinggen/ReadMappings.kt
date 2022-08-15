package xland.ioutils.mappinggen

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.ProGuardReader
import net.fabricmc.mappingio.format.TsrgReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.IOException
import kotlin.io.path.bufferedReader

@Throws(IOException::class)
fun readMappings(runArgsHolder: RunArgsHolder) : MemoryMappingTree {
    val tree = MemoryMappingTree()
    // Fabric
    if (runArgsHolder.proguardMapping != null) {
        MappingReader.read(runArgsHolder.yarnOrIntermediaryMapping, tree)
        ProGuardReader.read(runArgsHolder.proguardMapping.bufferedReader(), "fabric", "official", tree)
    } else {
        MappingReader.read(runArgsHolder.yarnOrIntermediaryMapping, tree.run {
            MappingNsRenamer(this, mapOf("yarn" to "fabric"))
        })
    }
    // Forge
    TsrgReader.read(runArgsHolder.srgMapping.bufferedReader(), "official", runArgsHolder.forgeField, tree)
    return tree
}
