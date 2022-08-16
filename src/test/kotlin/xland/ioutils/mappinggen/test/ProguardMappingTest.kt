package xland.ioutils.mappinggen.test

import net.fabricmc.mappingio.format.Tiny1Writer
import net.fabricmc.mappingio.format.TsrgReader
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

class ProguardMappingTest {
    private val path: Path = Paths.get("/tmp/aba.txt")
    private val output : Path = Paths.get("/tmp/996f.txt")
    @Test
    fun run() {
        if (Files.notExists(path)) return
        if (Files.exists(output)) return
        val writer = Tiny1Writer(output.bufferedWriter())
        TsrgReader.read(path.bufferedReader(), "official", "forge", writer)
        writer.close()
    }
}