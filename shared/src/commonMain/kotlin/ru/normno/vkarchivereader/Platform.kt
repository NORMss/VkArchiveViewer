package ru.normno.vkarchivereader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform