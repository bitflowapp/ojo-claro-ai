package com.ojoclaro.android.message

interface HumanMessageComposer {
    fun compose(request: MessageCompositionRequest): MessageCompositionResult
}

