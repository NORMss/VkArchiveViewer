package ru.normno.vkarchivereader.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// JS/Wasm run on a single thread; Default is the only real option.
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
