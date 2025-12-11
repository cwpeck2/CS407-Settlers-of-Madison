package com.cs407.settlersofmadison.ui.lobby

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


data class ProfileSettings(
    val nickname: String = "",
    val preferredColor: Long? = null,
    val avatarUri: String? = null
)

class ProfileViewModel : ViewModel() {


    private val _profile = MutableStateFlow(ProfileSettings())
    val profile: StateFlow<ProfileSettings> = _profile.asStateFlow()

    fun updateNickname(newName: String) {
        _profile.update { it.copy(nickname = newName) }
    }

    fun updateColor(color: Long?) {
        _profile.update { it.copy(preferredColor = color) }
    }

    fun setAvatar(uri: String?) {
        _profile.update { it.copy(avatarUri = uri) }
    }


    fun updateAll(nickname: String, color: Long?, avatarUri: String?) {
        _profile.value = ProfileSettings(
            nickname = nickname,
            preferredColor = color,
            avatarUri = avatarUri
        )
    }
}
