package com.example.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.di.AppContainer

class DipeViewModelFactory<T : ViewModel>(
  private val creator: () -> T,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}

inline fun <reified T : ViewModel> dipeFactory(
  container: AppContainer,
  crossinline builder: (AppContainer) -> T,
): DipeViewModelFactory<T> = DipeViewModelFactory { builder(container) }
