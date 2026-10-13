package com.ramzan.proximity.di

import com.ramzan.proximity.domain.usecase.AcceptUwbOfferUseCase
import com.ramzan.proximity.domain.usecase.CreateUwbOfferUseCase
import com.ramzan.proximity.domain.usecase.ObserveCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.ObserveLocationUseCase
import com.ramzan.proximity.domain.usecase.ObserveNearbyDevicesUseCase
import com.ramzan.proximity.domain.usecase.ObserveNfcReadsUseCase
import com.ramzan.proximity.domain.usecase.ObserveTrackingStateUseCase
import com.ramzan.proximity.domain.usecase.ObserveUwbStateUseCase
import com.ramzan.proximity.domain.usecase.RefreshCapabilitiesUseCase
import com.ramzan.proximity.domain.usecase.StartTrackingUseCase
import com.ramzan.proximity.domain.usecase.StartUwbRangingUseCase
import com.ramzan.proximity.domain.usecase.StopTrackingUseCase
import com.ramzan.proximity.domain.usecase.StopUwbRangingUseCase
import com.ramzan.proximity.domain.usecase.WriteNfcTagUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Domain bindings. Repositories, Clock and DispatcherProvider come from the platform module. */
val sharedModule = module {
    factoryOf(::ObserveCapabilitiesUseCase)
    factoryOf(::RefreshCapabilitiesUseCase)
    factoryOf(::ObserveTrackingStateUseCase)
    factoryOf(::ObserveLocationUseCase)
    factory { ObserveNearbyDevicesUseCase(sensors = get(), clock = get()) }
    factoryOf(::StartTrackingUseCase)
    factoryOf(::StopTrackingUseCase)
    factoryOf(::ObserveUwbStateUseCase)
    factoryOf(::CreateUwbOfferUseCase)
    factoryOf(::AcceptUwbOfferUseCase)
    factoryOf(::StartUwbRangingUseCase)
    factoryOf(::StopUwbRangingUseCase)
    factoryOf(::ObserveNfcReadsUseCase)
    factory { WriteNfcTagUseCase(nfc = get(), capabilities = get()) }
}
