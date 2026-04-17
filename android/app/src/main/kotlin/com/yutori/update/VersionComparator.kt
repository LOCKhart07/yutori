package com.yutori.update

// Component-wise integer compare of a "N.N.N" version against a
// "vN.N.N" release tag. Non-numeric inputs (empty, pre-release suffix,
// dev build names like "0.0.0-dev+123") return false — treated as
// "not an update", which keeps dev builds from popping the dialog and
// means a prerelease tag on :latest wouldn't nag release users either.
object VersionComparator {
    fun hasUpdate(current: String, remoteTag: String): Boolean {
        val currentParts = current.toIntParts() ?: return false
        val remoteParts = remoteTag.removePrefix("v").toIntParts() ?: return false
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun String.toIntParts(): List<Int>? {
        if (isEmpty()) return null
        val parts = split(".")
        return parts.map { it.toIntOrNull() ?: return null }
    }
}
