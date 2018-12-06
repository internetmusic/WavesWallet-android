package com.wavesplatform.wallet.v2.ui.home.wallet.assets.details

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.Menu
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.vicpin.krealmextensions.save
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.Constants
import com.wavesplatform.wallet.v2.data.model.remote.response.AssetBalance
import com.wavesplatform.wallet.v2.ui.base.view.BaseActivity
import com.wavesplatform.wallet.v2.ui.home.MainActivity
import com.wavesplatform.wallet.v2.ui.home.wallet.assets.AssetsFragment.Companion.RESULT_NEED_UPDATE
import com.wavesplatform.wallet.v2.ui.welcome.AlphaScalePageTransformer
import kotlinx.android.synthetic.main.activity_asset_details.*
import kotlinx.android.synthetic.main.activity_main_v2.*
import pers.victor.ext.click
import pers.victor.ext.dp2px
import pers.victor.ext.gone
import pers.victor.ext.visiable
import javax.inject.Inject


class AssetDetailsActivity : BaseActivity(), AssetDetailsView {

    @Inject
    @InjectPresenter
    lateinit var presenter: AssetDetailsPresenter

    @ProvidePresenter
    fun providePresenter(): AssetDetailsPresenter = presenter

    @Inject
    lateinit var adapterAvatar: AssetDetailsAvatarPagerAdapter

    lateinit var menu: Menu

    override fun configLayoutRes() = R.layout.activity_asset_details

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(R.anim.slide_in_right, R.anim.null_animation)
        super.onCreate(savedInstanceState)
    }

    override fun onViewReady(savedInstanceState: Bundle?) {
        setStatusBarColor(R.color.basic50)
        setNavigationBarColor(R.color.basic50)
        setupToolbar(toolbar_view, true, icon = R.drawable.ic_toolbar_back_black)

        app_bar_layout.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                    if (presenter.scrollRange == -1f) {
                        presenter.scrollRange = app_bar_layout.totalScrollRange.toFloat()
                    }
                    linear_top_content.alpha = (presenter.scrollRange + verticalOffset) / presenter.scrollRange

                    if (presenter.scrollRange + verticalOffset == 0f) {
                        view_pager.setPagingEnabled(false)
                        toolbar_view.title = text_asset_name.text.toString()
                        presenter.isShow = true
                    } else if (presenter.isShow) {
                        view_pager.setPagingEnabled(true)
                        toolbar_view.title = " "
                        presenter.isShow = false
                    }
                })

        view_pager.adapter = adapterAvatar
        view_pager.offscreenPageLimit = 10
        view_pager.clipToPadding = false
        view_pager.setPadding(dp2px(160) / 2 - dp2px(24), 0, dp2px(160) / 2 - dp2px(24), 0)
        view_pager.setPageTransformer(false, AlphaScalePageTransformer(0.58f))
        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                configureTitleForAssets(position)
                showFavorite(view_pager.currentItem)
                view_pager_content.setCurrentItem(position, true)
            }
        })

        view_pager_content.offscreenPageLimit = 3
        view_pager_content.clipToPadding = false
        view_pager_content.setPagingEnabled(false)

        image_favorite.click {
            presenter.needToUpdate = true
            changeFavorite()
        }

        presenter.loadAssets(intent.getIntExtra(BUNDLE_ASSET_TYPE, 0))
    }

    private fun configureTitleForAssets(position: Int) {
        if (position >= adapterAvatar.items.size ) {
            text_asset_description.text = ""
            return
        }

        val item = adapterAvatar.items[position]
        text_asset_name.text = item.getName()

        text_asset_description.visiable()
        spam_tag.gone()

        when {
            item.isWaves() -> text_asset_description.setText(R.string.asset_details_waves_token)
            item.isFiatMoney -> text_asset_description.setText(R.string.asset_details_flat_money)
            item.isGateway -> text_asset_description.setText(R.string.asset_details_cryptocurrency)
            item.isSpam -> {
                text_asset_description.gone()
                spam_tag.visiable()
            }
            else -> text_asset_description.setText(R.string.asset_details_waves_token)
        }
    }

    override fun afterSuccessLoadAssets(sortedToFirstFavoriteList: MutableList<AssetBalance>) {
        // configure top avatars pager
        adapterAvatar.items = sortedToFirstFavoriteList
        adapterAvatar.notifyDataSetChanged()
        view_pager.setCurrentItem(intent.getIntExtra(BUNDLE_ASSET_POSITION, 0), false)
        view_pager.post {
            if (view_pager.beginFakeDrag() && adapterAvatar.count != 0) {
                view_pager.fakeDragBy(0f)
                view_pager.endFakeDrag()
            }
        }

        // configure contents pager
        view_pager_content.adapter = AssetDetailsContentPageAdapter(supportFragmentManager,
                ArrayList(sortedToFirstFavoriteList))
        view_pager_content.setCurrentItem(intent.getIntExtra(BUNDLE_ASSET_POSITION, 0),
                false)

        configureTitleForAssets(view_pager.currentItem)
        showFavorite(view_pager.currentItem)
    }

    fun showFavorite(currentItem: Int) {
        if (adapterAvatar.items[currentItem].isSpam) image_favorite.gone()
        else image_favorite.visiable()

        if (adapterAvatar.items[view_pager.currentItem].isFavorite) {
            markAsFavorite()
        } else {
            unmarkAsFavorite()
        }
    }

    private fun changeFavorite() {
        if (!adapterAvatar.items[view_pager.currentItem].isWaves()) {
            if (adapterAvatar.items[view_pager.currentItem].isFavorite) {
                unmarkAsFavorite()
            } else {
                markAsFavorite()
            }
        }
    }

    private fun unmarkAsFavorite() {
        val item = adapterAvatar.items[view_pager.currentItem]
        item.isFavorite = false
        item.save()
        image_favorite.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_toolbar_favorite_off))
    }

    private fun markAsFavorite() {
        val item = adapterAvatar.items[view_pager.currentItem]
        item.isFavorite = true
        item.isHidden = false
        item.save()
        image_favorite.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_toolbar_favorite_on))
    }

    override fun onBackPressed() {
        setResult(Constants.RESULT_OK, Intent().apply {
            putExtra(RESULT_NEED_UPDATE, presenter.needToUpdate)
        })
        finish()
        overridePendingTransition(R.anim.null_animation, R.anim.slide_out_right)
    }

    override fun needToShowNetworkMessage() = true

    companion object {
        var BUNDLE_ASSET_POSITION = "position"
        var BUNDLE_ASSET_TYPE = "type"
    }
}
