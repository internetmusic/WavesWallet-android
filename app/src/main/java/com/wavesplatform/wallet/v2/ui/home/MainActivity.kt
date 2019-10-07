/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.home

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.wavesplatform.sdk.utils.Identicon
import com.wavesplatform.sdk.utils.notNull
import com.wavesplatform.wallet.App
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.Constants
import com.wavesplatform.wallet.v2.data.Events
import com.wavesplatform.wallet.v2.data.analytics.AnalyticEvents
import com.wavesplatform.wallet.v2.data.analytics.analytics
import com.wavesplatform.wallet.v2.data.model.db.userdb.AddressBookUserDb
import com.wavesplatform.wallet.v2.data.model.local.HistoryTab
import com.wavesplatform.wallet.v2.data.model.local.MigrateAccountItem
import com.wavesplatform.wallet.v2.data.model.local.widget.MyAccountItem
import com.wavesplatform.wallet.v2.data.model.service.configs.NewsResponse
import com.wavesplatform.wallet.v2.ui.auth.drawer.AbstractDrawerLoginActivity
import com.wavesplatform.wallet.v2.ui.auth.drawer.MyAccountsAdapter
import com.wavesplatform.wallet.v2.ui.auth.drawer.edit_account.EditAccountBottomSheetFragment
import com.wavesplatform.wallet.v2.ui.auth.passcode.enter.EnterPassCodeActivity
import com.wavesplatform.wallet.v2.ui.home.dex.DexFragment
import com.wavesplatform.wallet.v2.ui.home.history.HistoryFragment
import com.wavesplatform.wallet.v2.ui.home.history.tab.HistoryTabFragment
import com.wavesplatform.wallet.v2.ui.home.profile.ProfileFragment
import com.wavesplatform.wallet.v2.ui.home.profile.backup.BackupPhraseActivity
import com.wavesplatform.wallet.v2.ui.home.quick_action.QuickActionBottomSheetFragment
import com.wavesplatform.wallet.v2.ui.home.wallet.WalletFragment
import com.wavesplatform.wallet.v2.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_backup_seed_warning_snackbar.*
import kotlinx.android.synthetic.main.dialog_news.view.*
import pers.victor.ext.*
import pyxis.uzuki.live.richutilskt.utils.runDelayed
import javax.inject.Inject

class MainActivity : AbstractDrawerLoginActivity(), MainView, TabLayout.OnTabSelectedListener {

    @Inject
    @InjectPresenter
    lateinit var presenter: MainPresenter
    private val fragments = arrayListOf<Fragment>()
    private var activeFragment = Fragment()
    private var seedWarningBehavior: BottomSheetBehavior<LinearLayout>? = null

    @ProvidePresenter
    fun providePresenter(): MainPresenter = presenter

    override fun configLayoutRes() = R.layout.activity_main

    override fun onViewReady(savedInstanceState: Bundle?) {
        setStatusBarColor(R.color.basic50)
        setNavigationBarColor(R.color.white)
        setupToolbar(toolbar_general, homeEnable = false)

        setupBottomNavigation()

        logFirstOpen()

        if (savedInstanceState == null) {
            onTabSelected(tab_navigation.getTabAt(WALLET_SCREEN))
        }

        eventSubscriptions.add(mRxEventBus.filteredObservable(Events.SpamFilterStateChanged::class.java)
                .subscribe {
                    presenter.reloadTransactionsAfterSpamSettingsChanged()
                })

        eventSubscriptions.add(mRxEventBus.filteredObservable(Events.SpamFilterUrlChanged::class.java)
                .subscribe {
                    if (it.updateTransaction) {
                        presenter.reloadTransactionsAfterSpamSettingsChanged(true)
                    }
                })

        image_account_avatar.click {
            slidingRootNav.openMenu(true)
        }

        setupSwitchAccounts()
    }

    private fun setupSwitchAccounts() {
        presenter.getAddresses()

        accountsAdapter.chooseAccountOnClickListener = object : MyAccountsAdapter.MyAccountOnClickListener {
            override fun onEditClicked(position: Int, item: AddressBookUserDb) {
                processAccountEdit(position, item)
            }

            override fun onDeleteClicked(position: Int, item: AddressBookUserDb) {
                processAccountDelete(position, item)
            }

            override fun onItemClicked(position: Int, item: AddressBookUserDb) {
                processAccountClick(item)
            }
        }
    }

    private fun processAccountClick(item: AddressBookUserDb) {
        val guid = App.getAccessManager().findGuidBy(item.address)
        if (MonkeyTest.isTurnedOn()) {
            MonkeyTest.startIfNeed()
        } else {
            launchActivity<EnterPassCodeActivity>(
                    requestCode = EnterPassCodeActivity.REQUEST_ENTER_PASS_CODE) {
                putExtra(EnterPassCodeActivity.KEY_INTENT_GUID, guid)
                putExtra(EnterPassCodeActivity.KEY_INTENT_USE_BACK_FOR_EXIT, true)
            }
        }
    }

    private fun processAccountDelete(position: Int, item: AddressBookUserDb) {
        analytics.trackEvent(AnalyticEvents.StartAccountDeleteEvent)

        val guid = App.getAccessManager().findGuidBy(item.address)

        val alertDialog = AlertDialog.Builder(this@MainActivity).create()
        alertDialog.setTitle(getString(R.string.choose_account_delete_title))
        alertDialog.setMessage(getString(R.string.choose_account_delete_msg))
        if (prefsUtil.getGuidValue(guid, PrefsUtil.KEY_SKIP_BACKUP, true)) {
            alertDialog.setView(inflate(R.layout.content_delete_account_warning_layout, null))
        }
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getString(R.string.choose_account_yes)) { dialog, which ->
            dialog.dismiss()

            App.getAccessManager().deleteWavesWallet(item.address)
            accountsAdapter.remove(position)
        }
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                getString(R.string.choose_account_cancel)) { dialog, which ->
            dialog.dismiss()
        }
        alertDialog.show()
        alertDialog.makeStyled()
    }

    private fun processAccountEdit(position: Int, item: AddressBookUserDb) {
        analytics.trackEvent(AnalyticEvents.StartAccountEditEvent)

        val editAccountDialog = EditAccountBottomSheetFragment.newInstance(position, item)
        editAccountDialog.listener = object : EditAccountBottomSheetFragment.EditAccountListener {
            override fun onSuccess(position: Int, account: AddressBookUserDb) {
                accountsAdapter.setData(position, MyAccountItem(account,
                        accountsAdapter.getItem(position)?.locked ?: false,
                        accountsAdapter.getItem(position)?.active ?: false))
                App.getAccessManager().storeWalletName(item.address, item.name)
            }
        }
        editAccountDialog.show(supportFragmentManager, editAccountDialog::class.java.simpleName)
    }

    private fun setToolbarTitle(title: String) {
        toolbar_title.text = title
    }

    private fun applyAccountAvatar() {
        Glide.with(this)
                .load(Identicon().create(App.getAccessManager().getWallet()?.address))
                .apply(RequestOptions().circleCrop().dontAnimate())
                .into(image_account_avatar)
    }

    private fun logFirstOpen() {
        if (prefsUtil.getValue(PrefsUtil.KEY_ACCOUNT_FIRST_OPEN, true)) {
            prefsUtil.setValue(PrefsUtil.KEY_ACCOUNT_FIRST_OPEN, false)
            prefsUtil.setValue(PrefsUtil.KEY_IS_CLEARED_ALERT_ALREADY_SHOWN, true)
        } else {
            if (!prefsUtil.getValue(PrefsUtil.KEY_IS_CLEARED_ALERT_ALREADY_SHOWN, false)) {
                prefsUtil.setValue(PrefsUtil.KEY_IS_NEED_TO_SHOW_CLEARED_ALERT, true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showBackUpSeedWarning()
        if (App.getAccessManager().isAuthenticated()) {
            presenter.loadNews()
            applyAccountAvatar()
            setToolbarTitle(presenter.getWalletName())
        }
    }

    fun enableElevation(enable: Boolean) {
        if (enable) {
            ViewCompat.setElevation(appbar_layout, 0F)
        } else {
            ViewCompat.setElevation(appbar_layout, 8F)
        }
    }

    /**
     * Setup bottom navigation with custom tabs
     * **/
    private fun setupBottomNavigation() {
        tab_navigation.addTab(tab_navigation.newTab().setCustomView(
                getCustomView(R.drawable.ic_tabbar_wallet)).setTag(TAG_NOT_CENTRAL_TAB))
        tab_navigation.addTab(tab_navigation.newTab().setCustomView(
                getCustomView(R.drawable.ic_tabbar_dex)).setTag(TAG_NOT_CENTRAL_TAB))
        tab_navigation.addTab(tab_navigation.newTab().setCustomView(
                getCenterTabLayout(R.drawable.ic_tabbar_action)).setTag(TAG_CENTRAL_TAB))
        tab_navigation.addTab(tab_navigation.newTab().setCustomView(
                getCustomView(R.drawable.ic_tabbar_history)).setTag(TAG_NOT_CENTRAL_TAB))
        tab_navigation.addTab(tab_navigation.newTab().setCustomView(
                getCustomView(R.drawable.ic_tabbar_profile)).setTag(TAG_NOT_CENTRAL_TAB))

        tab_navigation.addOnTabSelectedListener(this)

        setupBottomNavigationFragments()
    }

    private fun setupBottomNavigationFragments() {
        val bundle = Bundle().apply {
            val tabs = arrayListOf(
                    HistoryTab(HistoryTabFragment.all, getString(R.string.history_all)),
                    HistoryTab(HistoryTabFragment.send, getString(R.string.history_sent)),
                    HistoryTab(HistoryTabFragment.received, getString(R.string.history_received)),
                    HistoryTab(HistoryTabFragment.exchanged, getString(R.string.history_exchanged)),
                    HistoryTab(HistoryTabFragment.leased, getString(R.string.history_leased)),
                    HistoryTab(HistoryTabFragment.issued, getString(R.string.history_issued)))
            putParcelableArrayList(HistoryFragment.BUNDLE_TABS, tabs)
        }

        val walletFragment = WalletFragment.newInstance()
        val dexFragment = DexFragment.newInstance()
        val historyFragment = HistoryFragment.newInstance().apply {
            arguments = bundle
        }
        val profileFragment = ProfileFragment.newInstance()

        val elevationListener = object : OnElevationAppBarChangeListener {
            override fun onChange(elevateEnable: Boolean) {
                enableElevation(elevateEnable)
            }
        }

        walletFragment.setOnElevationChangeListener(elevationListener)
        dexFragment.setOnElevationChangeListener(elevationListener)
        historyFragment.setOnElevationChangeListener(elevationListener)
        profileFragment.setOnElevationChangeListener(elevationListener)

        fragments.add(walletFragment)
        fragments.add(dexFragment)
        fragments.add(QuickActionBottomSheetFragment.newInstance())
        fragments.add(historyFragment)
        fragments.add(profileFragment)

        activeFragment = fragments[WALLET_SCREEN]
    }

    override fun onTabReselected(tab: TabLayout.Tab?) {
        tab?.position.notNull { position ->
            mRxEventBus.post(Events.ScrollToTopEvent(position))
        }
        when (tab?.position) {
            QUICK_ACTION_SCREEN -> {
                showQuickActionDialog()
            }
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab?) {
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        when (tab?.position) {
            WALLET_SCREEN -> {
                showNewTabFragment(fragments[WALLET_SCREEN])
                setToolbarTitle(presenter.getWalletName())
            }
            DEX_SCREEN -> {
                showNewTabFragment(fragments[DEX_SCREEN])
                setToolbarTitle(getString(R.string.dex_toolbar_title))
            }
            QUICK_ACTION_SCREEN -> {
                showQuickActionDialog()
            }
            HISTORY_SCREEN -> {
                showNewTabFragment(fragments[HISTORY_SCREEN])
                setToolbarTitle(getString(R.string.history_toolbar_title))
            }
            PROFILE_SCREEN -> {
                showNewTabFragment(fragments[PROFILE_SCREEN])
                setToolbarTitle(getString(R.string.profile_toolbar_title))
            }
        }

        if (tab?.position != QUICK_ACTION_SCREEN) {
            selectedTabs(tab)
        }
    }

    private fun showQuickActionDialog() {
        if (isNetworkConnected()) {
            analytics.trackEvent(AnalyticEvents.WavesActionPanelEvent)
            val quickActionDialog = QuickActionBottomSheetFragment.newInstance()
            val ft = supportFragmentManager.beginTransaction()
            ft.add(quickActionDialog, quickActionDialog::class.java.simpleName)
            ft.commitAllowingStateLoss()
        }
    }

    private fun showNewTabFragment(fragment: Fragment) {
        if (supportFragmentManager.findFragmentByTag(fragment::class.java.simpleName) == null) {
            supportFragmentManager.beginTransaction().hide(activeFragment).add(R.id.frame_fragment_container, fragment, fragment::class.java.simpleName).show(fragment).commitAllowingStateLoss()
        } else {
            supportFragmentManager.beginTransaction().hide(activeFragment).show(fragment).commitAllowingStateLoss()
        }
        activeFragment = fragment
    }

    /**
     * Here we change tabs except for the central
     * @param selectedTab - the tab which we selected, other tabs will be default
     * **/
    private fun selectedTabs(selectedTab: TabLayout.Tab?) {
        for (i in 0 until tab_navigation.tabCount) {
            if (i == QUICK_ACTION_SCREEN) {
                continue
            }

            val tab = tab_navigation.getTabAt(i)
            if (tab == selectedTab) {
                changeTabIcon(selectedTab!!, true)
            } else {
                changeTabIcon(tab!!, false)
            }
        }
    }

    /**
     * Method that changes the color of the tab icon
     * @param selectedTab -  tab with layout and ImageView with id == R.id.image_tab_icon
     * @param isSelected - if true -> the color of the icon will be {R.color.home_tab_active}
     * otherwise {R.color.home_tab_inactive}
     * **/
    private fun changeTabIcon(selectedTab: TabLayout.Tab, isSelected: Boolean) {
        val imageTabIcon = selectedTab.customView?.findViewById<ImageView>(R.id.image_tab_icon)
        if (isSelected) {
            imageTabIcon?.setColorFilter(ContextCompat
                    .getColor(this, R.color.home_tab_active))
        } else {
            imageTabIcon?.setColorFilter(ContextCompat
                    .getColor(this, R.color.home_tab_inactive))
        }
    }

    /**
     * Returns custom tab layout
     * @param tabIcon
     * **/
    private fun getCustomView(tabIcon: Int): View? {
        val customTab = LayoutInflater.from(this)
                .inflate(R.layout.content_home_navigation_tab, null)
        val imageTabIcon = customTab.findViewById<ImageView>(R.id.image_tab_icon)

        imageTabIcon.setImageResource(tabIcon)
        return customTab
    }

    /**
     * Returns central tab layout
     * @param tabIcon
     * **/
    private fun getCenterTabLayout(tabIcon: Int): View? {
        val customTab = LayoutInflater.from(this)
                .inflate(R.layout.content_home_navigation_center_tab, null)
        val imageTabIcon = customTab.findViewById<ImageView>(R.id.image_tab_icon)

        imageTabIcon.setImageResource(tabIcon)
        return customTab
    }

    private fun showBackUpSeedWarning() {
        if (!prefsUtil.getValue(PrefsUtil.KEY_ACCOUNT_FIRST_OPEN, true) &&
                App.getAccessManager().isCurrentAccountBackupSkipped()) {
            val currentGuid = App.getAccessManager().getLastLoggedInGuid()
            val lastTime = preferencesHelper.getShowSaveSeedWarningTime(currentGuid)
            val now = EnvironmentManager.getTime()
            if (now > lastTime + MIN_15) {
                logBackUpAnalyticEvent()

                implementSwipeToDismiss()

                linear_warning_container.click {
                    seedWarningBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                    // wait for close animation end and open backup screen
                    runDelayed(250) {
                        launchActivity<BackupPhraseActivity> {
                            putExtra(ProfileFragment.KEY_INTENT_SET_BACKUP, true)
                        }
                    }
                }

                preferencesHelper.setShowSaveSeedWarningTime(currentGuid, now)
            }
        }

        if (App.getAccessManager().isCurrentAccountBackupSkipped()) {
            tab_navigation.getTabAt(PROFILE_SCREEN)?.customView?.findViewById<View>(R.id.view_seed_error)?.visiable()
        } else {
            tab_navigation.getTabAt(PROFILE_SCREEN)?.customView?.findViewById<View>(R.id.view_seed_error)?.gone()
        }
    }

    private fun logBackUpAnalyticEvent() {
        analytics.trackEvent(AnalyticEvents.NewUserWithoutBackup(prefsUtil.backUpAlertCount()))
        prefsUtil.incrementBackUpAlertCount()
    }

    private fun implementSwipeToDismiss() {
        seedWarningBehavior = BottomSheetBehavior.from(linear_warning_container)
        seedWarningBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun needToShowNetworkMessage() = true

    override fun rootLayoutToShowNetworkMessage(): ViewGroup = frame_root

    override fun extraBottomMarginToShowNetworkMessage(): Int {
        return tab_navigation.height
    }

    override fun onNetworkConnectionChanged(networkConnected: Boolean) {
        super.onNetworkConnectionChanged(networkConnected)
        if (networkConnected) {
            // enable quick action tab
            tab_navigation.getTabAt(QUICK_ACTION_SCREEN)?.customView?.alpha = Constants.View.ENABLE_VIEW
        } else {
            // disable quick action tab
            tab_navigation.getTabAt(QUICK_ACTION_SCREEN)?.customView?.alpha = Constants.View.DISABLE_VIEW
        }
    }

    override fun onBackPressed() {
        if (slidingRootNav.isMenuOpened) {
            slidingRootNav.closeMenu(true)
        } else {
            exit()
        }
    }

    override fun showNews(news: NewsResponse) {
        val ids = prefsUtil.getGlobalValueList(PrefsUtil.SHOWED_NEWS_IDS).toHashSet()
        var anyNewsShowed = false
        for (notification in news.notifications) {
            if (!ids.contains(notification.id) && !anyNewsShowed) {

                val startDate = notification.startDate ?: Long.MAX_VALUE
                val endDate = notification.endDate ?: Long.MAX_VALUE

                if ((EnvironmentManager.getTime() / 1000) in startDate..endDate) {
                    var accountFirstOpenDialog: AlertDialog? = null
                    val view = LayoutInflater.from(this)
                            .inflate(R.layout.dialog_news, null)

                    Glide.with(this)
                            .load(notification.logoUrl)
                            .into(view.image)

                    val langCode = preferencesHelper.getLanguage()
                    view.text_title.text = NewsResponse.getTitle(langCode, notification)
                    view.text_subtitle.text = NewsResponse.getSubtitle(langCode, notification)
                    view.button_ok.click {
                        prefsUtil.addGlobalListValue(PrefsUtil.SHOWED_NEWS_IDS, notification.id)
                        accountFirstOpenDialog?.dismiss()
                    }

                    accountFirstOpenDialog = AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setView(view)
                            .create()

                    accountFirstOpenDialog.window?.setGravity(Gravity.BOTTOM)
                    accountFirstOpenDialog.show()

                    anyNewsShowed = true
                }
            }
        }
    }

    override fun afterSuccessGetAddress(accounts: MutableList<MyAccountItem>) {
        accountsAdapter.setNewData(accounts)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            EnterPassCodeActivity.REQUEST_ENTER_PASS_CODE -> {
                if (resultCode == Constants.RESULT_OK) {
//                    TODO: Multi account logic here
//                    adapter.addUnlockedAccount(item, position)
                }
            }
        }
    }

    companion object {
        const val WALLET_SCREEN = 0
        const val DEX_SCREEN = 1
        const val QUICK_ACTION_SCREEN = 2
        const val HISTORY_SCREEN = 3
        const val PROFILE_SCREEN = 4

        private const val TAG_NOT_CENTRAL_TAB = "not_central_tab"
        private const val TAG_CENTRAL_TAB = "central_tab"

        private const val MIN_15 = 900000L
    }

    interface OnElevationAppBarChangeListener {
        fun onChange(elevateEnable: Boolean)
    }
}
