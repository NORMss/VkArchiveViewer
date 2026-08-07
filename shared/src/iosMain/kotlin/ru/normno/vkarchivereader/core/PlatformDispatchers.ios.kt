package ru.normno.vkarchivereader.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Dispatchers.IO is internal on Kotlin/Native; Default is the portable choice here.
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
