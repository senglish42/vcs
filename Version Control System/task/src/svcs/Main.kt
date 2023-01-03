package svcs

import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun getMapData(map: Map<String, String>) {
    println("These are SVCS commands:")
    for ((k, v) in map) println("${String.format("%-11s", k)}$v.")
}

fun config(args: Array<String>, config: File): String {
    if (args.size == 1) {
        if (config.readText().isEmpty()) return "Please, tell me who you are."
    } else config.writeText(args[1])
    return "The username is ${config.readText()}."
}

fun add(args: Array<String>, index: File): String {
    if (args.size == 1) {
        index.readText().let { return if (it.isEmpty()) "Add a file to the index." else "Tracked files:\n${it}" }
    } else {
        val file = File(args[1])
        if (!file.isFile) return "Can't find '${args[1]}'."
        index.let { if (it.readText().isEmpty()) it.writeText(args[1]) else it.appendText("\n${args[1]}")}
        return "The file '${args[1]}' is tracked."
    }
}

fun addLog(title: String, name: String, commit: String, sep: String = ""): String {
    return """
        commit $commit
        Author: $name
        $title
    """.trimIndent() + if (sep != "") "\n\n$sep" else sep
}

fun copyFiles(fileNameList: List<String>, hashDir: File) {
    return fileNameList.forEach{ File(it).copyTo(File("${hashDir.path}/$it")) }
}

fun commit(args: Array<String>, mapFile: Map<String, File>): String {
    if (args.size == 1) return "Message was not passed."
    val fileNameList = mapFile["index"]!!.readText().split("\n")
    val digest = MessageDigest.getInstance("SHA-256")
    val hash =  digest.digest(args[1].toByteArray(StandardCharsets.UTF_8))
    var hashText = BigInteger(1, hash).toString(16)
    while (hashText.length < 32) hashText = "0$hashText"
    if (fileNameList.count { File(it).isFile } == fileNameList.size) {
        val hashDir = File("${mapFile["commits"]!!.path}/$hashText")
        if (mapFile["commits"]!!.walk().count() == 1) {
            copyFiles(fileNameList, hashDir)
            mapFile["log"]!!.writeText(addLog(args[1], mapFile["config"]!!.readText(), hashText))
        } else {
            val listOfCommits = mapFile["commits"]!!.listFiles()!!
            if (hashDir in listOfCommits) return "Nothing to commit."
            val lastCommit = listOfCommits.filter { it.isDirectory }.maxByOrNull { it.lastModified() }!!
            if (fileNameList.count { File("${lastCommit.path}/$it").readText() != File(it).readText() } > 0) {
                copyFiles(fileNameList, hashDir)
                mapFile["log"]!!.writeText(addLog(args[1], mapFile["config"]!!.readText(), hashText, mapFile["log"]!!.readText()))
            } else return "Nothing to commit."
        }
    }
    return "Changes are committed."
}

fun checkout(args: Array<String>, mapFile: Map<String, File>): String {
    if (args.size == 1) return "Commit id was not passed."
    if (!File("${mapFile["commits"]!!.path}/${args[1]}").isDirectory) return "Commit does not exist."
    File("${mapFile["commits"]!!.path}/${args[1]}").listFiles()!!.forEach { File(it.name).writeText(it.readText()) }
    return "Switched to commit ${args[1]}."
}

fun log(log: File) = log.readText().ifEmpty { "No commits yet." }

fun mapOfHelp(): Map<String, String> {
    return mapOf(
            "config" to "Get and set a username",
            "add" to "Add a file to the index",
            "log" to "Show commit logs",
            "commit" to "Save changes",
            "checkout" to "Restore a file")
}

fun mapOfVCSFiles(): Map<String, File> {
    return mapOf(
            "config" to File("vcs/config.txt"),
            "log" to File("vcs/log.txt"),
            "index" to File("vcs/index.txt"),
            "commits" to File("vcs/commits")
    )
}

fun main(args: Array<String>) {
    if (args.size > 2) return else File("vcs").mkdir()
    val map = mapOfHelp()
    if (args.isEmpty() || args[0] == "--help") { getMapData(map) ; return }
    val mapFile = mapOfVCSFiles()
    mapFile.values.filter { it.toString().contains(".txt") }.forEach { it.createNewFile() }
    mapFile["commits"]!!.let { if (!it.exists()) it.mkdir() }
    println(when(args[0]) {
        "config" -> config(args, mapFile["config"]!!)
        "add" -> add(args, mapFile["index"]!!)
        "commit" -> commit(args, mapFile)
        "log" -> log(mapFile["log"]!!)
        "checkout" -> checkout(args, mapFile)
        else -> "'${args[0]}' is not a SVCS command."
    })
}