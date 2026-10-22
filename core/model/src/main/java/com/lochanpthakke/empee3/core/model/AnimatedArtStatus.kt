package com.lochanpthakke.empee3.core.model

enum class AnimatedArtStatus {
    UNCHECKED,
    DOWNLOADING,
    AVAILABLE,

    /** A motion cover exists for the album (checked on its page) but has not been downloaded. */
    FOUND,
    NO_MATCH,
    NO_ANIMATION,
    FAILED,
}
