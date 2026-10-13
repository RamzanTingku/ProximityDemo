package com.ramzan.proximity.di

import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.core.DispatcherProvider
import com.ramzan.proximity.data.AndroidDispatcherProvider
import com.ramzan.proximity.data.ble.BleScanDataSource
import com.ramzan.proximity.data.location.LocationDataSource
import com.ramzan.proximity.data.nfc.NfcRepositoryImpl
import com.ramzan.proximity.data.nfc.NfcTagSink
import com.ramzan.proximity.data.sensor.AndroidTrackingServiceController
import com.ramzan.proximity.data.sensor.SensorRepositoryImpl
import com.ramzan.proximity.data.sensor.TrackingServiceController
import com.ramzan.proximity.data.sensor.TrackingServiceHost
import com.ramzan.proximity.data.system.SystemStateMonitor
import com.ramzan.proximity.data.uwb.UwbRepositoryImpl
import com.ramzan.proximity.domain.repository.CapabilityRepository
import com.ramzan.proximity.domain.repository.NfcRepository
import com.ramzan.proximity.domain.repository.SensorRepository
import com.ramzan.proximity.domain.repository.UwbRepository
import com.ramzan.proximity.presentation.nfc.NfcViewModel
import com.ramzan.proximity.presentation.sensors.SensorsViewModel
import com.ramzan.proximity.presentation.uwb.UwbViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

val AppScope = named("appScope")

val dataModule = module {
    single<DispatcherProvider> { AndroidDispatcherProvider() }
    single<Clock> { Clock { System.currentTimeMillis() } }
    single(AppScope) { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }

    single<CapabilityRepository> { SystemStateMonitor(androidContext()) }
    single { LocationDataSource(androidContext()) }
    single { BleScanDataSource(androidContext()) }
    single<TrackingServiceController> { AndroidTrackingServiceController(androidContext()) }
    single {
        SensorRepositoryImpl(
            scope = get(AppScope),
            locationSource = get(),
            bleSource = get(),
            capabilityRepository = get(),
            serviceController = get(),
        )
    } binds arrayOf(SensorRepository::class, TrackingServiceHost::class)
    single<UwbRepository> { UwbRepositoryImpl(androidContext(), get(AppScope)) }
    single {
        NfcRepositoryImpl(scope = get(AppScope), dispatchers = get(), clock = get())
    } binds arrayOf(NfcRepository::class, NfcTagSink::class)
}

val presentationModule = module {
    viewModelOf(::SensorsViewModel)
    viewModelOf(::UwbViewModel)
    viewModelOf(::NfcViewModel)
}

val appModules = listOf(sharedModule, dataModule, presentationModule)
