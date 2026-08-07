package ru.normno.vkarchivereader.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ru.normno.vkarchivereader.data.repository.ArchiveRepository

/**
 * Koin graph. ViewModels are created via Compose's own `viewModel { }` factory
 * (see UI) and pull their dependencies from this container, so we don't depend
 * on koin-compose-viewmodel (which is sensitive to the lifecycle binary version).
 */
val appModule: Module = module {
    single { ArchiveRepository() }
}
