package com.signa.app.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.AuthError
import com.signa.app.support.TestAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestAuthRepository()
    private lateinit var viewModel: AuthViewModel

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        viewModel = AuthViewModel(repository, SavedStateHandle())
    }
    @After fun cleanup() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun fillCredentials() {
        viewModel.setField(AuthField.Email, "an@example.com")
        viewModel.setField(AuthField.Password, "abcdefgh")
    }

    @Test fun failedResendKeepsOtpAndSuccessfulResendClearsOldCode() = runTest(dispatcher) {
        viewModel.setField(AuthField.Email, "an@example.com")
        viewModel.setField(AuthField.Otp, "654321")
        repository.failure = AuthError.OtpCooldown
        viewModel.resendOtp()
        advanceUntilIdle()
        assertEquals(AuthError.OtpCooldown, viewModel.state.value.requestError)
        assertEquals("654321", viewModel.state.value.form[AuthField.Otp])
        repository.failure = null
        viewModel.resendOtp()
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.form[AuthField.Otp])
        assertEquals("an@example.com", viewModel.state.value.resetChallenge?.email)
        assertNull(viewModel.state.value.requestError)
    }

    @Test fun invalidFormDoesNotCallRepository() = runTest(dispatcher) {
        viewModel.submit(AuthMode.Login)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.errors.size)
        assertEquals(0, repository.loginCount)
    }

    @Test fun repeatedSubmitRunsOneRequestAndClearsPassword() = runTest(dispatcher) {
        fillCredentials()
        viewModel.submit(AuthMode.Login)
        viewModel.submit(AuthMode.Login)
        assertTrue(viewModel.state.value.isSubmitting)
        advanceUntilIdle()
        assertEquals(1, repository.loginCount)
        assertEquals("", viewModel.state.value.form[AuthField.Password])
        assertEquals(AuthEvent.SignedIn, viewModel.events.first())
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test fun registrationCallsRepositoryAndEmitsSuccess() = runTest(dispatcher) {
        fillCredentials()
        viewModel.setField(AuthField.DisplayName, "An")
        viewModel.setField(AuthField.Confirmation, "abcdefgh")
        viewModel.submit(AuthMode.Register)
        advanceUntilIdle()
        assertEquals(1, repository.registerCount)
        assertEquals(AuthEvent.Registered, viewModel.events.first())
    }

    @Test fun forgotPasswordShowsChallengeAndEditingEmailClearsIt() = runTest(dispatcher) {
        viewModel.setField(AuthField.Email, "an@example.com")
        viewModel.submit(AuthMode.ForgotPassword)
        advanceUntilIdle()
        assertEquals("123456", viewModel.state.value.resetChallenge?.demoCode)
        viewModel.setField(AuthField.Email, "other@example.com")
        assertNull(viewModel.state.value.resetChallenge)
    }

    @Test fun resetAndChangePasswordReachRepository() = runTest(dispatcher) {
        fillCredentials()
        viewModel.setField(AuthField.Otp, "123456")
        viewModel.setField(AuthField.Confirmation, "abcdefgh")
        viewModel.submit(AuthMode.ResetPassword)
        advanceUntilIdle()
        assertEquals(1, repository.resetCount)
        assertEquals(AuthEvent.PasswordReset, viewModel.events.first())
        viewModel.setField(AuthField.OldPassword, "previous")
        viewModel.setField(AuthField.Password, "nextpass")
        viewModel.setField(AuthField.Confirmation, "nextpass")
        viewModel.submit(AuthMode.ChangePassword)
        advanceUntilIdle()
        assertEquals(1, repository.changeCount)
        assertEquals(AuthEvent.PasswordChanged, viewModel.events.first())
    }

    @Test fun domainErrorsKeepFormForRetryAndStopLoading() = runTest(dispatcher) {
        fillCredentials()
        repository.failure = AuthError.InvalidCredentials
        viewModel.submit(AuthMode.Login)
        advanceUntilIdle()
        assertEquals(AuthError.InvalidCredentials, viewModel.state.value.requestError)
        assertFalse(viewModel.state.value.isSubmitting)
        assertEquals("an@example.com", viewModel.state.value.form[AuthField.Email])
    }
}
