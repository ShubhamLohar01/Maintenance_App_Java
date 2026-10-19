package com.candorfoods.factoryops.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.candorfoods.factoryops.data.repository.AssetScheduleRepositoryImpl
import com.candorfoods.factoryops.data.repository.AuthRepositoryImpl
import com.candorfoods.factoryops.data.repository.BreakdownRepositoryImpl
import com.candorfoods.factoryops.data.repository.ChecklistRepositoryImpl
import com.candorfoods.factoryops.data.repository.EnergyRepositoryImpl
import com.candorfoods.factoryops.data.repository.FloorReadingRepositoryImpl
import com.candorfoods.factoryops.data.repository.MachineRepositoryImpl
import com.candorfoods.factoryops.data.repository.MachineTransferRepositoryImpl
import com.candorfoods.factoryops.data.repository.MtMachineRepositoryImpl
import com.candorfoods.factoryops.data.repository.MtUserRepositoryImpl
import com.candorfoods.factoryops.data.repository.PmPlanRepositoryImpl
import com.candorfoods.factoryops.data.repository.PmTemplateRepositoryImpl
import com.candorfoods.factoryops.data.repository.PmWorkOrderRepositoryImpl
import com.candorfoods.factoryops.data.repository.RaisedTicketRepositoryImpl
import com.candorfoods.factoryops.data.repository.ReportsRepositoryImpl
import com.candorfoods.factoryops.data.repository.SparePartsRepositoryImpl
import com.candorfoods.factoryops.data.repository.UserRepositoryImpl
import com.candorfoods.factoryops.data.repository.UtilitiesRepositoryImpl
import com.candorfoods.factoryops.data.repository.A185ReadingsRepositoryImpl
import com.candorfoods.factoryops.data.repository.W202ReadingsRepositoryImpl
import com.candorfoods.factoryops.domain.repository.AssetScheduleRepository
import com.candorfoods.factoryops.domain.repository.AuthRepository
import com.candorfoods.factoryops.domain.repository.BreakdownRepository
import com.candorfoods.factoryops.domain.repository.ChecklistRepository
import com.candorfoods.factoryops.domain.repository.EnergyRepository
import com.candorfoods.factoryops.domain.repository.FloorReadingRepository
import com.candorfoods.factoryops.domain.repository.MachineRepository
import com.candorfoods.factoryops.domain.repository.MachineTransferRepository
import com.candorfoods.factoryops.domain.repository.MtMachineRepository
import com.candorfoods.factoryops.domain.repository.MtUserRepository
import com.candorfoods.factoryops.domain.repository.PmPlanRepository
import com.candorfoods.factoryops.domain.repository.PmTemplateRepository
import com.candorfoods.factoryops.domain.repository.PmWorkOrderRepository
import com.candorfoods.factoryops.domain.repository.RaisedTicketRepository
import com.candorfoods.factoryops.domain.repository.ReportsRepository
import com.candorfoods.factoryops.domain.repository.SparePartsRepository
import com.candorfoods.factoryops.domain.repository.UserRepository
import com.candorfoods.factoryops.domain.repository.UtilitiesRepository
import com.candorfoods.factoryops.domain.repository.A185ReadingsRepository
import com.candorfoods.factoryops.domain.repository.W202ReadingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindMachineRepository(impl: MachineRepositoryImpl): MachineRepository

    @Binds @Singleton
    abstract fun bindMachineTransferRepository(impl: MachineTransferRepositoryImpl): MachineTransferRepository

    @Binds @Singleton
    abstract fun bindMtMachineRepository(impl: MtMachineRepositoryImpl): MtMachineRepository

    @Binds @Singleton
    abstract fun bindMtUserRepository(impl: MtUserRepositoryImpl): MtUserRepository

    @Binds @Singleton
    abstract fun bindAssetScheduleRepository(impl: AssetScheduleRepositoryImpl): AssetScheduleRepository

    @Binds @Singleton
    abstract fun bindEnergyRepository(impl: EnergyRepositoryImpl): EnergyRepository

    @Binds @Singleton
    abstract fun bindFloorReadingRepository(impl: FloorReadingRepositoryImpl): FloorReadingRepository

    @Binds @Singleton
    abstract fun bindBreakdownRepository(impl: BreakdownRepositoryImpl): BreakdownRepository

    @Binds @Singleton
    abstract fun bindChecklistRepository(impl: ChecklistRepositoryImpl): ChecklistRepository

    @Binds @Singleton
    abstract fun bindPmTemplateRepository(impl: PmTemplateRepositoryImpl): PmTemplateRepository

    @Binds @Singleton
    abstract fun bindPmPlanRepository(impl: PmPlanRepositoryImpl): PmPlanRepository

    @Binds @Singleton
    abstract fun bindPmWorkOrderRepository(impl: PmWorkOrderRepositoryImpl): PmWorkOrderRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindUtilitiesRepository(impl: UtilitiesRepositoryImpl): UtilitiesRepository

    @Binds @Singleton
    abstract fun bindA185ReadingsRepository(impl: A185ReadingsRepositoryImpl): A185ReadingsRepository

    @Binds @Singleton
    abstract fun bindW202ReadingsRepository(impl: W202ReadingsRepositoryImpl): W202ReadingsRepository

    @Binds @Singleton
    abstract fun bindSparePartsRepository(impl: SparePartsRepositoryImpl): SparePartsRepository

    @Binds @Singleton
    abstract fun bindReportsRepository(impl: ReportsRepositoryImpl): ReportsRepository

    @Binds @Singleton
    abstract fun bindRaisedTicketRepository(impl: RaisedTicketRepositoryImpl): RaisedTicketRepository
}
