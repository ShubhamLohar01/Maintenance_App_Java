package com.candorfoods.factoryops.tickets

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import com.candorfoods.factoryops.domain.repository.TicketActionResult
import com.candorfoods.factoryops.presentation.tickets.RaisedTicketDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaisedTicketDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeTicketRepository

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTicketRepository()
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun vmFor(id: Int = 1): RaisedTicketDetailViewModel =
        RaisedTicketDetailViewModel(repo).also {
            it.onTicketId(id)
            dispatcher.scheduler.advanceUntilIdle()
        }

    @Test fun `loads the ticket for the given id`() = runTest(dispatcher) {
        val vm = vmFor()
        assertEquals("TCKT-260801", vm.uiState.value.ticket?.ticketNo)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `acknowledge moves the ticket to acknowledged`() = runTest(dispatcher) {
        val vm = vmFor()

        vm.onAcknowledge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TicketStatus.ACKNOWLEDGED, vm.uiState.value.ticket?.status)
        assertNull(vm.uiState.value.actionMessage)
        assertFalse(vm.uiState.value.isActing)
    }

    @Test fun `a conflict shows who has it and reloads`() = runTest(dispatcher) {
        repo.ackResult = TicketActionResult.Conflict("already acknowledged by Vipul ware")
        // What the reload should reveal: someone else owns it now.
        repo.detail = Result.Success(
            ticket(status = TicketStatus.ACKNOWLEDGED, assignedTo = "Vipul ware")
        )
        val vm = vmFor()

        vm.onAcknowledge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("already acknowledged by Vipul ware", vm.uiState.value.actionMessage)
        assertEquals("Vipul ware", vm.uiState.value.ticket?.assignedTo)
        assertEquals(TicketStatus.ACKNOWLEDGED, vm.uiState.value.ticket?.status)
    }

    @Test fun `dismissing the message clears it`() = runTest(dispatcher) {
        repo.ackResult = TicketActionResult.Conflict("taken")
        val vm = vmFor()
        vm.onAcknowledge()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onDismissMessage()

        assertNull(vm.uiState.value.actionMessage)
    }

    @Test fun `a failed write surfaces its message and keeps the ticket`() = runTest(dispatcher) {
        repo.ackResult = TicketActionResult.Failure("Network unreachable")
        val vm = vmFor()

        vm.onAcknowledge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Network unreachable", vm.uiState.value.actionMessage)
        assertNotNull(vm.uiState.value.ticket)
    }

    // --- resolve dialog ---

    @Test fun `submit is blocked until a note is typed`() = runTest(dispatcher) {
        val vm = vmFor()
        vm.onResolveClicked()
        assertFalse(vm.uiState.value.canSubmitResolve)

        vm.onCorrectiveActionChange("   ")
        assertFalse(vm.uiState.value.canSubmitResolve)   // whitespace is not a note

        vm.onCorrectiveActionChange("Replaced the cable")
        assertTrue(vm.uiState.value.canSubmitResolve)
    }

    @Test fun `resolve sends the trimmed note and closes the ticket`() = runTest(dispatcher) {
        val vm = vmFor()
        vm.onResolveClicked()
        vm.onCorrectiveActionChange("  Replaced the charging cable  ")
        vm.onResolveSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Replaced the charging cable", repo.resolveNote)
        assertEquals(TicketStatus.RESOLVED, vm.uiState.value.ticket?.status)
        assertFalse(vm.uiState.value.showResolveDialog)
    }

    @Test fun `submitting a blank note does nothing`() = runTest(dispatcher) {
        val vm = vmFor()
        vm.onResolveClicked()
        vm.onCorrectiveActionChange("")
        vm.onResolveSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repo.resolveNote)    // never reached the repository
    }

    @Test fun `dismissing the dialog does not resolve`() = runTest(dispatcher) {
        val vm = vmFor()
        vm.onResolveClicked()
        vm.onCorrectiveActionChange("something")
        vm.onResolveDismiss()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.showResolveDialog)
        assertNull(repo.resolveNote)
    }

    @Test fun `a first-load failure becomes an error screen`() = runTest(dispatcher) {
        repo.detail = Result.Error("Ticket not found")
        val vm = vmFor()

        assertEquals("Ticket not found", vm.uiState.value.error)
        assertNull(vm.uiState.value.ticket)
    }
}
