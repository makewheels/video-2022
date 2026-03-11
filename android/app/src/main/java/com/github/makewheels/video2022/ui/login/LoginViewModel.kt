package com.github.makewheels.video2022.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.makewheels.video2022.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val isCodeSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone, errorMessage = null)
    }

    fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code, errorMessage = null)
    }

    fun requestVerificationCode() {
        val phone = _uiState.value.phone.trim()
        if (phone.length != 11) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入11位手机号")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            userRepository.requestVerificationCode(phone)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isCodeSent = true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun submitVerificationCode() {
        val state = _uiState.value
        if (state.code.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请输入验证码")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            userRepository.submitVerificationCode(state.phone.trim(), state.code.trim())
                .onSuccess {
                    userRepository.initClientAndSession()
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }
}
