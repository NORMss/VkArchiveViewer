package ru.normno.vkarchivereader.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking / IO-bound work — reading archive files and zip
 * entries, the SQLite face store. Keeps that work off the CPU-bound
 * `Dispatchers.Default` pool so a burst of file reads can't starve computation
 * (or, in the worst case, stall the app). On JS/Wasm the runtime is
 * single-threaded, so this is just `Dispatchers.Default`.
 */
expect val ioDispatcher: CoroutineDispatcher
