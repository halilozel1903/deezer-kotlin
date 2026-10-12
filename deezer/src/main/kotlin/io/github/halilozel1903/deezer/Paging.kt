package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.model.DeezerPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/** The entries of every page, one by one. Pages are requested only as the items are collected. */
public fun <T> Flow<DeezerPage<T>>.items(): Flow<T> = transform { page -> page.data.forEach { emit(it) } }
