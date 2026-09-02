package com.lochanpthakke.empee3.core.common.logging

import timber.log.Timber
import javax.inject.Inject

class TimberLogger
    @Inject
    constructor() : Logger {
        override fun v(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Timber.tag(tag).v(throwable, message)
        }

        override fun d(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Timber.tag(tag).d(throwable, message)
        }

        override fun i(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Timber.tag(tag).i(throwable, message)
        }

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Timber.tag(tag).w(throwable, message)
        }

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Timber.tag(tag).e(throwable, message)
        }
    }
