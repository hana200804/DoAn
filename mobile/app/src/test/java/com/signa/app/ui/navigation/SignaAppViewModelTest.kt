package com.signa.app.ui.navigation

import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.domain.repository.AuthRepository
import com.signa.app.support.TestAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignaAppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestAuthRepository()
    private lateinit var viewModel: SignaAppViewModel
    private val session = Session("s", User("u", "An", "an@example.com"), Long.MAX_VALUE, SessionSource.FAKE)

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        viewModel = SignaAppViewModel(repository)
    }

    @After fun cleanup() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun waitsForRestoreThenRequiresSignIn() = runTest(dispatcher) {
        viewModel.viewModelScope.cancel()
        val restoredSessions = MutableSharedFlow<Session?>()
        viewModel = SignaAppViewModel(object : AuthRepository by repository {
            override val session = restoredSessions
        })
        runCurrent()
        assertEquals(AppAccess.Loading, viewModel.access.value)
        viewModel.continueAsGuest()
        assertEquals(AppAccess.Loading, viewModel.access.value)
        restoredSessions.emit(null)
        runCurrent()
        assertEquals(AppAccess.SignedOut, viewModel.access.value)
    }

    @Test fun guestAccessRequiresExplicitChoiceAndCanReturnToAuth() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.continueAsGuest()
        assertEquals(AppAccess.Guest, viewModel.access.value)
        viewModel.requestSignIn()
        assertEquals(AppAccess.SignedOut, viewModel.access.value)
    }

    @Test fun validRestoredSessionEntersMainApp() = runTest(dispatcher) {
        repository.session.value = session
        advanceUntilIdle()
        assertEquals(AppAccess.Authenticated, viewModel.access.value)
        viewModel.continueAsGuest()
        assertEquals(AppAccess.Authenticated, viewModel.access.value)
    }

    @Test fun logoutOrExpiryAfterGuestLoginReturnsToAuthInsteadOfGuest() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.continueAsGuest()
        repository.session.value = session
        advanceUntilIdle()
        assertEquals(AppAccess.Authenticated, viewModel.access.value)
        repository.session.value = null
        advanceUntilIdle()
        assertEquals(AppAccess.SignedOut, viewModel.access.value)
    }
}
