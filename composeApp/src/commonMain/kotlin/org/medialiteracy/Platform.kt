package org.medialiteracy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform