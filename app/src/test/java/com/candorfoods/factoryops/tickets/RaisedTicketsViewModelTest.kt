package com.candorfoods.factoryops.tickets

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import com.candorfoods.factoryops.domain.repository.RaisedTicketRepository
import com.candorfoods.factoryops.domain.repository.TicketActionResult
import com.candorfoods.factoryops.domain.repository.TicketScope
import com.candorfoods.factoryops.presentation.tickets.RaisedTicketsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

fun ticket(
    id: Int = 1,
    ticketNo: String = "TCKT-260801",
    status: TicketStatus = TicketStatus.OPEN,
    assignedTo: String? = null,
    photoUrl: String? = null,
) = RaisedTicket(
    id = id,
    ticketNo = ticketNo,
    asset = "Laptop",
    area = "office",
    description = "not working",
    photoUrl = photoUrl,
    status = status,
    assignedTo = assignedTo,
    raisedBy = "Shubham lohar",
    createdAt = "2026-08-01T13:21:17+05:30",
    closedAt = null,
)

/** In-memory stand-in; each test sets only what it needs. */
class FakeTicketRepository : RaisedTicketRepository {
    var live: Result<List<RaisedTicket>> = Result.Success(emptyList())
    var history: Result<List<RaisedTicket>> = Result.Success(emptyList())
    var detail: Result<RaisedTicket> = Result.Success(ticket())
    var ackResult: TicketActionResult = TicketActionResult.Success(ticket(status = TicketStatus.ACKNOWLEDGED))
    var resolveResult: TicketActionResult = TicketActionResult.Success(ticket(status = TicketStatus.RESOLVED))

    val scopesRequested = mutableListOf<TicketScope>()
    var resolveNote: String? = null

    override suspend fun list(scope: TicketScope): Result<List<RaisedTicket>> {
        scopesRequested += scope
        return if (scope == TicketScope.LIVE) live else history
    }

    override suspend fun get(id: Int) = detail

    override suspend fun acknowledge(id: Int) = ackResult

    override suspend fun resolve(id: Int, correctiveAction: String): TicketActionResult {
        resolveNote = correctiveAction
        return resolveResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RaisedTicketsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeTicketRepository

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTicketRepository()
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads the live queue on open`() = runTest(dispatcher) {
        repo.live = Result.Success(listOf(ticket(id = 7, ticketNo = "TCKT-260807")))
        val vm = RaisedTicketsViewModel(repo)

        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("TCKT-260807"), state.tickets.map { it.ticketNo })
        assertEquals(listOf(TicketScope.LIVE), repo.scopesRequested)
    }

    @Test fun `switching to history refetches with the history scope`() = runTest(dispatcher) {
        repo.history = Result.Success(listOf(ticket(status = TicketStatus.RESOLVED)))
        val vm = RaisedTicketsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onScopeChange(TicketScope.HISTORY)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TicketScope.HISTORY, vm.uiState.value.scope)
        assertEquals(listOf(TicketScope.LIVE, TicketScope.HISTORY), repo.scopesRequested)
    }

    @Test fun `re-selecting the current tab does not refetch`() = runTest(dispatcher) {
        val vm = RaisedTicketsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onScopeChange(TicketScope.LIVE)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.scopesRequested.size)
    }

    @Test fun `an empty queue is empty, not an error`() = runTest(dispatcher) {
        val vm = RaisedTicketsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isEmpty)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `a failure surfaces the message and is not treated as empty`() = runTest(dispatcher) {
        repo.live = Result.Error("Network unreachable")
        val vm = RaisedTicketsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Network unreachable", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isEmpty)
    }

    @Test fun `refresh keeps the existing rows on screen`() = runTest(dispatcher) {
        repo.live = Result.Success(listOf(ticket()))
        val vm = RaisedTicketsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onRefresh()
        // Mid-refresh: isLoading must stay false so the list isn't replaced by a
        // spinner while the user is looking at it.
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isRefreshing)

        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }
}
