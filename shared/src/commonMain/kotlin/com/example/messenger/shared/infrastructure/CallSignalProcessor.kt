package com.example.messenger.shared.infrastructure

import com.example.messenger.domain.model.ProcessResult

interface CallSignalProcessor {
    fun processSignal(signal: ProcessResult.CallSignal)
}
