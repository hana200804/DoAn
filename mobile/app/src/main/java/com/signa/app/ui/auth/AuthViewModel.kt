package com.signa.app.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class AuthEvent { SignedIn, Registered, PasswordReset, PasswordChanged }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val initialForm = AuthFormState().withValue(AuthField.Email, savedStateHandle.get<String>("email").orEmpty())
    private val mutableState = MutableStateFlow(AuthUiState(form = initialForm))
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    internal val events = eventChannel.receiveAsFlow()

    fun setField(field: AuthField, value: String) {
        if (mutableState.value.isSubmitting) return
        mutableState.update {
            it.copy(
                form = it.form.withValue(field, value),
                errors = it.errors - field,
                requestError = null,
                resetChallenge = if (field == AuthField.Email) null else it.resetChallenge
            )
        }
    }

    fun resendOtp() {
        submit(AuthMode.ForgotPassword)
    }

    fun submit(mode: AuthMode) {
        if (mutableState.value.isSubmitting) return
        val form = mutableState.value.form
        val errors = AuthFormValidator.validate(mode, form)
        mutableState.update { it.copy(errors = errors, requestError = null) }
        if (errors.isNotEmpty()) return
        mutableState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                when (mode) {
                    AuthMode.Login -> {
                        repository.login(form[AuthField.Email].trim(), form[AuthField.Password])
                        complete(AuthEvent.SignedIn)
                    }
                    AuthMode.Register -> {
                        repository.register(form[AuthField.DisplayName], form[AuthField.Email].trim(), form[AuthField.Password])
                        complete(AuthEvent.Registered)
                    }
                    AuthMode.ForgotPassword -> {
                        val challenge = repository.requestPasswordReset(form[AuthField.Email].trim())
                        mutableState.update { it.copy(resetChallenge = challenge, form = it.form.withValue(AuthField.Otp, "")) }
                    }
                    AuthMode.ResetPassword -> {
                        repository.resetPassword(form[AuthField.Email].trim(), form[AuthField.Otp], form[AuthField.Password])
                        complete(AuthEvent.PasswordReset)
                    }
                    AuthMode.ChangePassword -> {
                        repository.changePassword(form[AuthField.OldPassword], form[AuthField.Password])
                        complete(AuthEvent.PasswordChanged)
                    }
                }
            } catch (error: AuthException) {
                mutableState.update { it.copy(requestError = error.error) }
            } catch (_: IOException) {
                mutableState.update { it.copy(requestError = AuthError.Storage) }
            } catch (_: IllegalArgumentException) {
                mutableState.update { it.copy(requestError = AuthError.InvalidResponse) }
            } finally {
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun complete(event: AuthEvent) {
        mutableState.value = AuthUiState()
        eventChannel.send(event)
    }
}
