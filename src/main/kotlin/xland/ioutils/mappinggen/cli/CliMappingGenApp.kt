package xland.ioutils.mappinggen.cli

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.format.Tiny1Writer
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xland.ioutils.mappinggen.InvalidMappingException
import xland.ioutils.mappinggen.RunArgsHolder
import xland.ioutils.mappinggen.util.Either
import java.io.BufferedReader
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

class CliMappingGenApp(private val runArgsHolder: RunArgsHolder,
                       treeFrom: MappingTree,
                       descFileFrom : Path?,
                       private val mappingTo : Path?) : Closeable, Runnable {
    private val reader : BufferedReader? = descFileFrom?.bufferedReader()
    private val shortNameEngine = ShortNameEngine(treeFrom, "fabric", "intermediary", runArgsHolder.forgeField)

    private val treeTo = MemoryMappingTree()

    private fun parseLine(line : String) : Either<Unit, String> {
        line.split(Regex("\\s+")).run {
            if (this.size < 2) return Either.right("Expression too short: $line")
            val process = processes.indexOf(this[0])
            if (process < 0) return Either.right("Invalid process: ${this[0]}")
            var superClass : String? = null
            var ignoreAbsent = false
            if (this.size > 3 && ">>" == this[2]) {
                superClass = this[3]
            } else if (this.size > 2) {
                if ("!!" == this[2])
                    ignoreAbsent = true
            }
            return parseLine0(process, this[1], superClass, ignoreAbsent)
        }
    }

    private fun parseLine0(
        process: Int, arg: String, superClass: String?,
        ignoreAbsent: Boolean = false
    ) : Either<Unit, String> {
        when (process) {
            CLASS -> {
                shortNameEngine.trySuggestClass((superClass ?: arg).replace('.', '/'))
                    .ifRight {
                        if (!ignoreAbsent || !this.isAbsent()) {
                            return Either.right(this.reason)
                        }
                    }.ifLeft {
                        treeTo.visitClass(this.first)
                        treeTo.visitDstName(MappedElementKind.CLASS, 0, this.second)
                        println("class\t$first=$second")
                    }
                return Either.left(Unit)
            }
            FIELD -> {
                val name = StringBuilder(
                    superClass?.replace('.', '/') ?: arg.substringBeforeLast('.').replace('.', '/')
                ).append(arg.substring(arg.lastIndexOf('.'))).toString()
                shortNameEngine.trySuggestField(name).ifRight {
                    if (!ignoreAbsent || !this.isAbsent()) {
                        return Either.right(this.reason)
                    }
                }.ifLeft {
                    if (treeTo.visitClass(this.first.owner)) {
                        treeTo.visitField(first.name, first.desc)
                        treeTo.visitDstName(MappedElementKind.FIELD, 0, second)
                        println("field\t$first=$second")

                        if (superClass != null) {
                            val realOwner = arg.substringBeforeLast('.').replace('.', '/')
                            treeTo.visitClass(realOwner)
                            treeTo.visitField(first.name, first.desc)
                            treeTo.visitDstName(MappedElementKind.FIELD, 0, second)
                            println("field\t${first.copy(owner=realOwner)}=$second")
                        }
                    }
                }
                return Either.left(Unit)
            }
            METHOD -> {
                val name = StringBuilder(
                    superClass?.replace('.', '/') ?: arg.substringBeforeLast('.').replace('.', '/')
                ).append(arg.substring(arg.lastIndexOf('.'))).toString()
                shortNameEngine.trySuggestMethod(name).ifRight {
                    if (!ignoreAbsent || !this.isAbsent()) {
                        return Either.right(this.reason)
                    }
                }.ifLeft {
                    if (treeTo.visitClass(this.first.owner)) {
                        treeTo.visitMethod(first.name, first.desc)
                        treeTo.visitDstName(MappedElementKind.METHOD, 0, second)
                        println("method\t$first=$second")

                        if (superClass != null) {
                            val realOwner = arg.substringBeforeLast('.').replace('.', '/')
                            treeTo.visitClass(realOwner)
                            treeTo.visitMethod(first.name, first.desc)
                            treeTo.visitDstName(MappedElementKind.FIELD, 0, second)
                            println("method\t${first.copy(owner=realOwner)}=$second")
                        }
                    }
                }
                return Either.left(Unit)
            }
            FIELD_C -> {
                return parseLine0(CLASS, arg.substringBeforeLast('.'), superClass, ignoreAbsent).mapLeft {
                    parseLine0(FIELD, arg, superClass, ignoreAbsent)
                }
            }
            METHOD_C -> {
                return parseLine0(CLASS, arg.substringBeforeLast('.'), superClass, ignoreAbsent).mapLeft {
                    parseLine0(METHOD, arg, superClass, ignoreAbsent)
                }
            }
            else -> {
                throw UnsupportedOperationException("Invalid process id $process")
            }
        }
    }

    override fun run() {
        if (!treeTo.visitHeader()) return
        treeTo.visitNamespaces("intermediary", listOf(runArgsHolder.forgeField))
        this.use { _ ->
            if (reader != null) {
                if (mappingTo == null)
                    throw IllegalArgumentException("not using interaction mode, but didn't specify mappingTo")
                for (line in reader.lineSequence()) {
                    parseLine(line).ifRight {
                        throw InvalidMappingException(this)
                    }
                }
                Tiny1Writer(mappingTo.bufferedWriter()).use {
                    treeTo.accept(it)
                }
            } else {
                print("You're entering interaction mode.\n\$> ")
                val scanner = Scanner(System.`in`)
                var hasWork = false
                while (scanner.hasNextLine()) {
                    try {
                        val ln = scanner.nextLine()
                        if (!ln.isNullOrBlank()) {
                            val trySplit = ln.split(Regex("\\s+"), 2)
                            when (trySplit[0]) {
                                "dmp" -> {
                                    if (trySplit.size == 1) {
                                        if (mappingTo == null) {
                                            println("ERROR: Did not set output path")
                                        }
                                        else {
                                            Tiny1Writer(mappingTo.bufferedWriter()).use {
                                                treeTo.accept(it)
                                                hasWork = false
                                            }
                                        }
                                    } else {
                                        Tiny1Writer(Paths.get(trySplit[1]).bufferedWriter()).use {
                                            treeTo.accept(it)
                                            hasWork = false
                                        }
                                    }
                                }
                                "bye" -> {
                                    if (hasWork) {
                                        println("WARNING: there are things not saved")
                                    } else {
                                        println("Bye~")
                                        break
                                    }
                                }
                                else -> {
                                    parseLine(ln).ifRight {
                                        val err = if (length > 127) substring(0..127) + "..." else this
                                        println("ERROR: $err")
                                    }.ifLeft {
                                        hasWork = true
                                    }
                                }
                            }
                        }
                    } catch (e : Exception) {
                        println("ERROR: ${e.stackTraceToString()}")
                    } finally {
                        print("\$> ")
                    }
                }
            }
        }
    }

    override fun close() {
        reader?.close()
        treeTo.visitEnd()
    }
}

private const val CLASS = 0
private const val FIELD = 1
private const val METHOD = 2
private const val FIELD_C = 3
private const val METHOD_C = 4
private val processes = listOf("c", "f", "m", "fc", "mc")
