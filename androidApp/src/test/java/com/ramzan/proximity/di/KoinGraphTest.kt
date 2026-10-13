package com.ramzan.proximity.di

import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.NfcRepository
import com.ramzan.proximity.domain.repository.SensorRepository
import com.ramzan.proximity.domain.repository.UwbRepository
import com.ramzan.proximity.domain.usecase.StartTrackingUseCase
import com.ramzan.proximity.domain.usecase.WriteNfcTagUseCase
import com.ramzan.proximity.testing.FakeCapabilityRepository
import com.ramzan.proximity.testing.FakeNfcRepository
import com.ramzan.proximity.testing.FakeSensorRepository
import com.ramzan.proximity.testing.FakeUwbRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class KoinGraphTest {
    @After
    fun tearDown() = stopKoin()

    @Test
    fun `shared module resolves every use case against platform bindings`() {
        val platform = module {
            single<CapabilityRepository> { FakeCapabilityRepository() }
            single<SensorRepository> { FakeSensorRepository() }
            single<UwbRepository> { FakeUwbRepository() }
            single<NfcRepository> { FakeNfcRepository() }
            single<Clock> { Clock { 0L } }
        }
        val koin = startKoin { modules(sharedModule, platform) }.koin

        assertNotNull(koin.get<StartTrackingUseCase>())
        assertNotNull(koin.get<WriteNfcTagUseCase>())
    }
}
