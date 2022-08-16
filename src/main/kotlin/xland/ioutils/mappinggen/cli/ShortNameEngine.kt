package xland.ioutils.mappinggen.cli

import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTree.*
import xland.ioutils.mappinggen.util.Either
import xland.ioutils.mappinggen.util.LRUMap
import xland.ioutils.mappinggen.util.leftOrNull
import java.util.stream.Collectors

open class ShortNameEngine(private val mappingTree: MappingTree,
                           typed: String,
                           from: String,
                           to: String) {
    private val classCacheMap : LinkedHashMap<String, Pair<String, String>> = LRUMap(2 shl 15)
    private val classNodeMap : LinkedHashMap<String, ClassMapping> = LRUMap(2 shl 15)
    private val fieldCacheMap : LinkedHashMap<String, Pair<EntryElement, String>> = LRUMap(2 shl 17)
    private val methodCacheMap: LinkedHashMap<String, Pair<EntryElement, String>> = LRUMap(2 shl 17)

    private val namespaceTyped = mappingTree.getNamespaceId(typed)
    private val namespaceFrom = mappingTree.getNamespaceId(from)
    private val namespaceTo = mappingTree.getNamespaceId(to)

    fun trySuggestClass(name: String) : Either<Pair<String, String>, FailReason> {
        return classCacheMap[name].leftOrNull() ?: getClassMapping(name).mapLeft {
            val realName = it.getName(namespaceTyped)
            classNodeMap[name] = it
            classNodeMap[realName] = it
            (it.getName(namespaceFrom) to it.getName(namespaceTo)).also { p ->
                classCacheMap[realName] = p
                classCacheMap[name] = p
            }
        }
    }

    fun trySuggestField(name: String) : Either<Pair<EntryElement, String>, FailReason> {
        return fieldCacheMap[name].leftOrNull() ?: this.run {
            val s = splitOND(name)
            getFieldMapping(s.first, s.second, s.third).mapLeft {
                (EntryElement(
                    it.owner.getName(namespaceFrom),
                    it.getName(namespaceFrom),
                    it.getDesc(namespaceFrom)
                ) to it.getName(namespaceTo)).also { p ->
                    fieldCacheMap[name] = p
                }
            }
        }
    }

    fun trySuggestMethod(name : String) : Either<Pair<EntryElement, String>, FailReason> {
        return methodCacheMap[name].leftOrNull() ?: this.run {
            val s = splitOND(name)
            getMethodMapping(s.first, s.second, s.third).mapLeft {
                (EntryElement(
                    it.owner.getName(namespaceFrom),
                    it.getName(namespaceFrom),
                    it.getDesc(namespaceFrom)
                ) to it.getName(namespaceTo)).also { p ->
                    methodCacheMap[name] = p
                }
            }
        }
    }

    private fun getClassMapping(name: String) : Either<ClassMapping, FailReason> =
        mappingTree.getClass(name, namespaceTyped)?.let { Either.left(it) } ?: run {
            mappingTree.classes.parallelStream()
                .filter { it.getName(namespaceTyped)?.endsWith(name) ?: false }
                .collect(Collectors.toList())
                .let {
                    when (it.size) {
                        0 -> Either.right(FailReason.absent("Can't find class ends with $name"))
                        1 -> Either.left(it[0])
                        else -> {
                            val l : MutableList<String> = arrayListOf()
                            it.forEach { c ->
                                val n = c.getName(namespaceTyped)
                                l.add(n)
                                classCacheMap[n] =
                                    c.getName(namespaceFrom) to c.getName(namespaceTo)
                                classNodeMap[n] = c
                            }
                            Either.right(FailReason.ambiguous("Suffix $name is too ambiguous (size=${it.size}): $l"))
                        }
                    }
                }
        }

    private fun getFieldMapping(owner: String?, name: String, desc: String?) : Either<FieldMapping, FailReason> {
        if (owner != null && desc != null) {
            var field = mappingTree.getField(owner, name, desc, namespaceTyped)
            if (field != null) return Either.left(field)
            trySuggestClass(owner).ifRight { return Either.right(this) }
                .ifLeft {
                val classMapping = classNodeMap[owner] ?: return Either.right(FailReason.absent("Can't find class $owner because of " +
                        "unknown reason"))
                field = classMapping.getField(name, desc, namespaceTyped)
                if (field != null) return Either.left(field)
            }
        }
        if (owner != null) {
            trySuggestClass(owner).ifRight { return Either.right(this) }.ifLeft {
                val classMapping = classNodeMap[owner] ?: return Either.right(FailReason.absent("Can't find class $owner because of " +
                        "unknown reason"))
                return classMapping.fields.parallelStream()
                    .filter {
                        name == it.getName(namespaceTyped) &&
                                ((desc == null) || (desc == it.getDesc(namespaceTyped)))
                    }.collect(Collectors.toList())
                    .let {
                        when (it.size) {
                            0 -> {
                                var s = "No such field named ${owner}.$name"
                                if (desc != null) s += ":$desc"
                                Either.right(FailReason.absent(s))
                            }
                            1 -> Either.left(it[0])
                            else -> {
                                val l : MutableList<String> = arrayListOf()
                                it.forEach { f ->
                                    fieldCacheMap["${f.owner.getName(namespaceTyped)}.${f.getName(namespaceTyped)}" +
                                            ":${f.getDesc(namespaceTyped)}".also(l::add)] =
                                        EntryElement(
                                            f.owner.getName(namespaceFrom),
                                            f.getName(namespaceFrom),
                                            f.getDesc(namespaceFrom)
                                        ) to f.getName(namespaceTo)
                                }
                                val sb = StringBuilder("field ${owner}.${name}")
                                if (desc != null) sb.append(":$desc")
                                Either.right(FailReason.ambiguous(sb.append(" is ambiguous (size=${it.size}): $l").toString()))
                            }
                        }
                    }
            }
            throw IncompatibleClassChangeError()
        } else {    // owner == null
            return mappingTree.classes.parallelStream()
                .flatMap { it.fields.parallelStream() }
                .filter {
                    name == it.getName(namespaceTyped) &&
                            (desc == null || desc == it.getDesc(namespaceTyped))
                }.collect(Collectors.toList())
                .let {
                    when (it.size) {
                        0 -> {
                            var s = "No such field named $name"
                            if (desc != null) s += ":$desc"
                            Either.right(FailReason.absent(s))
                        }
                        1 -> Either.left(it[0])
                        else -> {
                            val l : MutableList<String> = arrayListOf()
                            it.forEach { f ->
                                fieldCacheMap["${f.owner.getName(namespaceTyped)}.${f.getName(namespaceTyped)}" +
                                        ":${f.getDesc(namespaceTyped)}".also(l::add)] =
                                    EntryElement(
                                        f.owner.getName(namespaceFrom),
                                        f.getName(namespaceFrom),
                                        f.getDesc(namespaceFrom)
                                    ) to f.getName(namespaceTo)
                            }
                            val sb = StringBuilder("field $name")
                            if (desc != null) sb.append(":$desc")
                            Either.right(FailReason.ambiguous(sb.append(" is ambiguous (size=${it.size}): $l").toString()))
                        }
                    }
                }
        }
    }

    private fun getMethodMapping(owner: String?, name: String, desc: String?) : Either<MethodMapping, FailReason> {
        if (owner != null && desc != null) {
            var method = mappingTree.getMethod(owner, name, desc, namespaceTyped)
            if (method != null) return Either.left(method)
            trySuggestClass(owner).ifRight { return Either.right(this) }
                .ifLeft {
                    val classMapping = classNodeMap[owner] ?: return Either.right(FailReason.absent("Can't find class $owner because of " +
                            "unknown reason"))
                    method = classMapping.getMethod(name, desc, namespaceTyped)
                    if (method != null) return Either.left(method)
                }
        }
        if (owner != null) {
            trySuggestClass(owner).ifRight { return Either.right(this) }.ifLeft {
                val classMapping = classNodeMap[owner] ?: return Either.right(FailReason.absent("Can't find class $owner because of " +
                        "unknown reason"))
                return classMapping.methods.parallelStream()
                    .filter {
                        name == it.getName(namespaceTyped) &&
                                ((desc == null) || (desc == it.getDesc(namespaceTyped)))
                    }.collect(Collectors.toList())
                    .let {
                        when (it.size) {
                            0 -> {
                                var s = "No such method named ${owner}.$name"
                                if (desc != null) s += ":$desc"
                                Either.right(FailReason.absent(s))
                            }
                            1 -> Either.left(it[0])
                            else -> {
                                val l : MutableList<String> = arrayListOf()
                                it.forEach { f ->
                                    methodCacheMap["${f.owner.getName(namespaceTyped)}.${f.getName(namespaceTyped)}" +
                                            ":${f.getDesc(namespaceTyped)}".also(l::add)] =
                                        EntryElement(
                                            f.owner.getName(namespaceFrom),
                                            f.getName(namespaceFrom),
                                            f.getDesc(namespaceFrom)
                                        ) to f.getName(namespaceTo)
                                }
                                val sb = StringBuilder("method ${owner}.${name}")
                                if (desc != null) sb.append(":$desc")
                                Either.right(FailReason.ambiguous(sb.append(" is ambiguous (size=${it.size}): $l").toString()))
                            }
                        }
                    }
            }
            throw IncompatibleClassChangeError()
        } else {    // owner == null
            return mappingTree.classes.parallelStream()
                .flatMap { it.methods.parallelStream() }
                .filter {
                    name == it.getName(namespaceTyped) &&
                            (desc == null || desc == it.getDesc(namespaceTyped))
                }.collect(Collectors.toList())
                .let {
                    when (it.size) {
                        0 -> {
                            var s = "No such method named $name"
                            if (desc != null) s += ":$desc"
                            Either.right(FailReason.absent(s))
                        }
                        1 -> Either.left(it[0])
                        else -> {
                            val l : MutableList<String> = arrayListOf()
                            it.forEach { f ->
                                methodCacheMap["${f.owner.getName(namespaceTyped)}.${f.getName(namespaceTyped)}" +
                                        ":${f.getDesc(namespaceTyped)}".also(l::add)] =
                                    EntryElement(
                                        f.owner.getName(namespaceFrom),
                                        f.getName(namespaceFrom),
                                        f.getDesc(namespaceFrom)
                                    ) to f.getName(namespaceTo)
                            }
                            val sb = StringBuilder("method $name")
                            if (desc != null) sb.append(":$desc")
                            Either.right(FailReason.ambiguous(sb.append(" is ambiguous (size=${it.size}): $l").toString()))
                        }
                    }
                }
        }
    }

    data class EntryElement(val owner : String, val name : String, val desc : String)

    data class FailReason(val type: Int, val reason: String) {
        companion object {
            private const val ABSENT = 0
            private const val AMBIGUOUS = 1

            fun absent(reason: String) = FailReason(ABSENT, reason)
            fun ambiguous(reason: String) = FailReason(AMBIGUOUS, reason)
        }

        fun isAbsent() = this.type == ABSENT
        fun isAmbiguous() = this.type == AMBIGUOUS
    }
}

private fun splitOND(name : String) : Triple<String?, String, String?> {
    // owner.name:desc
    var owner: String? = null
    var desc : String? = null
    var name0 = name
    name.lastIndexOf('.').let {
        if (it >= 0) {
            name0 = name.substring(it + 1)
            owner = name.substring(0 until it)
        }
    }
    name0.indexOf(':').let {
        if (it >= 0) {
            name0 = name0.substring(0 until it)
            desc = name0.substring(it + 1)
        }
    }
    return Triple(owner, name0, desc)
}
