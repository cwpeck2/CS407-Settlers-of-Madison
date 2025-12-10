package com.cs407.settlersofmadison.ui.lobby // <-- keep or adjust to your package

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Shared profile settings used by GameViewModel.applyLocalProfile(...)
data class ProfileSettings(
    val nickname: String = "",
    val preferredColor: Long? = null,   // ARGB long
    val avatarUri: String? = null       // String URI for profile picture
)

class ProfileViewModel : ViewModel() {

    // IMPORTANT: this is MutableStateFlow<ProfileSettings>, not Any
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

    /** Used when you press "Done" on the profile screen */
    fun updateAll(nickname: String, color: Long?, avatarUri: String?) {
        _profile.value = ProfileSettings(
            nickname = nickname,
            preferredColor = color,
            avatarUri = avatarUri
        )
    }
}
