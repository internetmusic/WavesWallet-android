package com.wavesplatform.wallet.v2.ui.home.history.tab

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.chad.library.adapter.base.BaseQuickAdapter
import com.ethanhua.skeleton.RecyclerViewSkeletonScreen
import com.ethanhua.skeleton.Skeleton
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.Events
import com.wavesplatform.wallet.v2.data.model.remote.response.AssetBalance
import com.wavesplatform.wallet.v2.ui.base.view.BaseFragment
import com.wavesplatform.wallet.v2.ui.home.MainActivity
import com.wavesplatform.wallet.v2.ui.home.history.HistoryFragment
import com.wavesplatform.wallet.v2.data.model.local.HistoryItem
import com.wavesplatform.wallet.v2.ui.home.history.details.HistoryDetailsBottomSheetFragment
import com.wavesplatform.wallet.v2.util.notNull
import kotlinx.android.synthetic.main.fragment_history_tab.*
import pyxis.uzuki.live.richutilskt.utils.runAsync
import java.util.*
import javax.inject.Inject
import com.wavesplatform.wallet.v2.ui.custom.SpeedyLinearLayoutManager
import com.oushangfeng.pinnedsectionitemdecoration.PinnedHeaderItemDecoration


class HistoryTabFragment : BaseFragment(), HistoryTabView {

    @Inject
    @InjectPresenter
    lateinit var presenter: HistoryTabPresenter

    @ProvidePresenter
    fun providePresenter(): HistoryTabPresenter = presenter

    @Inject
    lateinit var adapter: HistoryTabItemAdapter
    lateinit var layoutManager: LinearLayoutManager
    var changeTabBarVisibilityListener: ChangeTabBarVisibilityListener? = null
    private var skeletonScreen: RecyclerViewSkeletonScreen? = null
    private var headerItemDecoration: PinnedHeaderItemDecoration? = null

    override fun configLayoutRes(): Int = R.layout.fragment_history_tab

    companion object {

        const val all = "All"
        const val send = "Sent"
        const val received = "Received"
        const val exchanged = "Exchanged"
        const val leased = "Leased"
        const val issued = "Issued"

        const val leasing_all = "Leasing All"
        const val leasing_active_now = "Active now"
        const val leasing_canceled = "Canceled"

        const val TYPE = "type"


        fun newInstance(type: String, asset: AssetBalance?): HistoryTabFragment {
            val historyDateItemFragment = HistoryTabFragment()
            val bundle = Bundle()
            bundle.putString(TYPE, type)
            bundle.putParcelable(HistoryFragment.BUNDLE_ASSET, asset)
            historyDateItemFragment.arguments = bundle
            return historyDateItemFragment
        }
    }

    override fun onViewReady(savedInstanceState: Bundle?) {
        eventSubscriptions.add(rxEventBus.filteredObservable(Events.ScrollToTopEvent::class.java)
                .subscribe {
                    if (it.position == MainActivity.HISTORY_SCREEN) {
                        recycle_history.scrollToPosition(0)
                        changeTabBarVisibilityListener?.changeTabBarVisibility(true)
                    }
                })


        layoutManager = SpeedyLinearLayoutManager(baseActivity)
        recycle_history.layoutManager = layoutManager
        recycle_history.adapter = adapter

        presenter.type = arguments?.getString("type")
        presenter.assetBalance = arguments?.getParcelable<AssetBalance>(HistoryFragment.BUNDLE_ASSET)

        adapter.bindToRecyclerView(recycle_history)


        skeletonScreen = Skeleton.bind(recycle_history)
                .adapter(recycle_history.adapter)
                .shimmer(true)
                .count(5)
                .color(R.color.basic100)
                .load(R.layout.item_skeleton_wallet)
                .frozen(false)
                .show()

        // make skeleton as designed
        recycle_history.post {
            recycle_history.layoutManager.findViewByPosition(1)?.alpha = 0.7f
            recycle_history.layoutManager.findViewByPosition(2)?.alpha = 0.5f
            recycle_history.layoutManager.findViewByPosition(3)?.alpha = 0.4f
            recycle_history.layoutManager.findViewByPosition(4)?.alpha = 0.2f
        }

        eventSubscriptions.add(rxEventBus.filteredObservable(Events.NeedUpdateHistoryScreen::class.java)
                .subscribe {
                    presenter.totalHeaders = 0
                    presenter.hashOfTimestamp = hashMapOf()
                    runAsync {
                        presenter.loadTransactions()
                    }
                })

        runAsync {
            presenter.loadTransactions()
        }

        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
            if (position == 0) return@OnItemClickListener // handle click on empty space

            val historyItem = adapter.getItem(position) as HistoryItem
            if (historyItem.header.isEmpty()) {
                val bottomSheetFragment = HistoryDetailsBottomSheetFragment()

                val data = adapter?.data as ArrayList<HistoryItem>
                val allItems = data.asSequence()
                        .filter {
                            it.header.isEmpty()
                        }
                        .map { it.data }
                        .toList()

                var sectionSize = 0
                for (i in 0..position) {
                    if (data[i].header.isNotEmpty()) sectionSize++
                }
                var count = data.count { it.header.isNotEmpty() }

                val selectedPositionWithoutHeaders = position - sectionSize

                bottomSheetFragment.configureData(historyItem.data, selectedPositionWithoutHeaders, allItems)
                bottomSheetFragment.show(fragmentManager, bottomSheetFragment.tag)
            }
        }

        headerItemDecoration = PinnedHeaderItemDecoration.Builder(HistoryItem.TYPE_HEADER)
                .disableHeaderClick(true).create()
        recycle_history.addItemDecoration(headerItemDecoration)
    }

    override fun afterSuccessLoadTransaction(data: ArrayList<HistoryItem>, type: String?) {
        configureTabLayout(type, data)
        configureEmptyView(data)
        adapter.setNewData(data)
        skeletonScreen.notNull { it.hide() }
    }

    private fun configureEmptyView(data: ArrayList<HistoryItem>) {
        if (data.isEmpty()) {
            adapter.setEmptyView(R.layout.layout_empty_data)
        }
    }

    private fun configureTabLayout(type: String?, data: ArrayList<HistoryItem>) {
        // hide tab bar layout if not data available and show empty view
        if ((type == all || type == leasing_all) && data.isEmpty()) {
            changeTabBarVisibilityListener?.changeTabBarVisibility(false)
        } else if ((type == all || type == leasing_all) && data.isNotEmpty()) {
            changeTabBarVisibilityListener?.changeTabBarVisibility(true)
        }
    }

    interface ChangeTabBarVisibilityListener {
        fun changeTabBarVisibility(show: Boolean, onlyExpand: Boolean = false)
    }
}
