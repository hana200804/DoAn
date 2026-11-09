package com.signa.app.data.remote

import com.signa.app.data.remote.dto.ApiErrorDto
import java.io.IOException

class ApiException(val status: Int, val error: ApiErrorDto?, cause: Throwable) : IOException("HTTP $status", cause)
