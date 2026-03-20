val line = "[ Intro ]"
val tokens = "\\[.*?\\]|\\S+".toRegex().findAll(line).map { it.value }.toList()
println(tokens)