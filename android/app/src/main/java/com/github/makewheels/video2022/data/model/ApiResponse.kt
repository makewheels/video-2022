package com.github.makewheels.video2022.data.model

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 0
}
