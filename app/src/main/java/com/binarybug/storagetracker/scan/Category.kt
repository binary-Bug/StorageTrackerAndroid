package com.binarybug.storagetracker.scan

/** File type buckets, decided by extension only (cheap enough to run on every file during the scan). */
enum class Category(val label: String) {
    IMAGES("Photos & images"),
    VIDEOS("Videos"),
    AUDIO("Audio & music"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("Installers & app packages"),
    OTHER("Other files");

    companion object {
        private val byExt = HashMap<String, Category>().apply {
            "jpg jpeg png gif webp heic heif bmp dng raw cr2 nef arw svg tif tiff".split(' ').forEach { put(it, IMAGES) }
            "mp4 mkv mov avi webm 3gp m4v flv wmv mpg mpeg".split(' ').forEach { put(it, VIDEOS) }
            "mp3 m4a aac wav flac ogg opus wma amr mid midi".split(' ').forEach { put(it, AUDIO) }
            "pdf doc docx xls xlsx ppt pptx txt rtf odt ods csv epub md json xml html htm".split(' ').forEach { put(it, DOCUMENTS) }
            "zip rar 7z tar gz bz2 xz iso".split(' ').forEach { put(it, ARCHIVES) }
            "apk apks xapk apkm obb".split(' ').forEach { put(it, APKS) }
        }

        fun of(fileName: String): Category {
            val dot = fileName.lastIndexOf('.')
            if (dot < 0 || dot == fileName.length - 1) return OTHER
            return byExt[fileName.substring(dot + 1).lowercase()] ?: OTHER
        }

        fun fromName(name: String): Category? = entries.firstOrNull { it.name == name }
    }
}
