package me.bgregos.foreground.util

import androidx.fragment.app.FragmentTransaction
import me.bgregos.foreground.R

// Extension methods to make adding standardized animations to
// fragment interactions more consistent.

fun FragmentTransaction.phoneDetailAnimations() = this.setCustomAnimations(
        R.anim.fade_in,
        R.anim.fade_out,
        R.anim.fade_in,
        R.anim.fade_out
)

fun FragmentTransaction.tabletDetailAnimations() = this.setCustomAnimations(
        R.anim.fade_in,
        R.anim.fade_out,
        R.anim.fade_in,
        R.anim.fade_out
)

