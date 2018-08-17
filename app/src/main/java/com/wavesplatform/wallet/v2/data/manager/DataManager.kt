package com.wavesplatform.wallet.v2.data.manager

import com.wavesplatform.wallet.v2.data.helpers.PublicKeyAccountHelper
import com.wavesplatform.wallet.v2.data.local.PreferencesHelper
import com.wavesplatform.wallet.v2.data.remote.AppService
import javax.inject.Inject

open class DataManager @Inject constructor() {

    @Inject lateinit var appService: AppService
    @Inject lateinit var preferencesHelper: PreferencesHelper
    @Inject lateinit var publicKeyAccountHelper: PublicKeyAccountHelper

    fun generatePublicKeyFrom(key: String){
        publicKeyAccountHelper.isPublicKeyAccountAvailable(key)
    }

    fun getAddress(): String? {
        return publicKeyAccountHelper.publicKeyAccount?.address
    }

    fun getPublicKeyStr(): String? {
        return publicKeyAccountHelper.publicKeyAccount?.publicKeyStr
    }
}