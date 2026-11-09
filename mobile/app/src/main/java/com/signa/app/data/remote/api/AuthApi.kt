package com.signa.app.data.remote.api

import com.signa.app.data.remote.dto.ChangePasswordRequest
import com.signa.app.data.remote.dto.ForgotPasswordRequest
import com.signa.app.data.remote.dto.LoginRequest
import com.signa.app.data.remote.dto.LoginResponse
import com.signa.app.data.remote.dto.RegisterRequest
import com.signa.app.data.remote.dto.ResetPasswordRequest
import com.signa.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest)
    @POST("auth/logout") suspend fun logout()
    @POST("auth/password/forgot") suspend fun forgotPassword(@Body request: ForgotPasswordRequest)
    @POST("auth/password/reset") suspend fun resetPassword(@Body request: ResetPasswordRequest)
    @PUT("users/me/password") suspend fun changePassword(@Body request: ChangePasswordRequest)
    @GET("users/me") suspend fun currentUser(): UserDto
}
