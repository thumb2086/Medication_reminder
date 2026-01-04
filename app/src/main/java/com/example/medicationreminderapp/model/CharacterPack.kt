package com.example.medicationreminderapp.model

import androidx.annotation.DrawableRes

data class CharacterPack(
    val id: String,
    val name: String,
    @DrawableRes val imageResId: Int
)
