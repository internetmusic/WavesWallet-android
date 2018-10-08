package com.wavesplatform.wallet.v2.ui.home.wallet.leasing

import android.util.Log
import com.arellomobile.mvp.InjectViewState
import com.vicpin.krealmextensions.queryAsSingle
import com.vicpin.krealmextensions.queryAsync
import com.wavesplatform.wallet.R.layout.test
import com.wavesplatform.wallet.v1.data.rxjava.RxUtil
import com.wavesplatform.wallet.v2.data.Constants
import com.wavesplatform.wallet.v2.data.model.local.LeasingStatus
import com.wavesplatform.wallet.v2.data.model.remote.response.AssetBalance
import com.wavesplatform.wallet.v2.data.model.remote.response.Transaction
import com.wavesplatform.wallet.v2.ui.base.presenter.BasePresenter
import com.wavesplatform.wallet.v2.util.sumByLong
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import pyxis.uzuki.live.richutilskt.utils.runAsync
import javax.inject.Inject

@InjectViewState
class LeasingPresenter @Inject constructor() : BasePresenter<LeasingView>() {

    fun getActiveLeasing() {
        runAsync {
                addSubscription(Observable.zip(nodeDataManager.loadWavesBalance(),
                        queryAsSingle<Transaction> {
                            equalTo("status", LeasingStatus.ACTIVE.status)
                                    .and()
                                    .equalTo("transactionTypeId", Constants.ID_STARTED_LEASING_TYPE)
                        }.toObservable(),
                        BiFunction { t1: AssetBalance, t2: List<Transaction> ->
                            return@BiFunction Pair(t1, t2)
                        })
                        .compose(RxUtil.applySchedulersToObservable())
                        .subscribe {
                            val leasedSum = it.second.sumByLong { it.amount }
                            viewState.showBalances(it.first,
                                    leasedSum, it.first.balance?.minus(leasedSum))
                            viewState.showActiveLeasingTransaction(it.second)
                        })
            }
        }

    }
