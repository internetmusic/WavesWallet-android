package com.wavesplatform.wallet.v2.ui.home.wallet.address

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.widget.AppCompatImageView
import android.view.View
import android.widget.TextView
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.helpers.PublicKeyAccountHelper
import com.wavesplatform.wallet.v2.ui.base.view.BaseActivity
import com.wavesplatform.wallet.v2.ui.home.profile.addresses.alias.AddressesAndKeysBottomSheetFragment
import com.wavesplatform.wallet.v2.util.copyToClipboard
import com.wavesplatform.wallet.v2.util.notNull
import kotlinx.android.synthetic.main.activity_my_address_qr.*
import pers.victor.ext.click
import pers.victor.ext.findColor
import pyxis.uzuki.live.richutilskt.utils.runDelayed
import javax.inject.Inject


class MyAddressQRActivity : BaseActivity(), MyAddressQrView {

    override fun afterSuccessGenerateAvatar(bitmap: Bitmap, imageView: AppCompatImageView) {
        Glide.with(applicationContext)
                .load(bitmap)
                .apply(RequestOptions()
                        .circleCrop())
                .into(imageView)
    }

    @Inject
    @InjectPresenter
    lateinit var presenter: MyAddressQrPresenter

    @ProvidePresenter
    fun providePresenter(): MyAddressQrPresenter = presenter

    override fun configLayoutRes() = R.layout.activity_my_address_qr

    @Inject
    lateinit var publicKeyAccountHelper: PublicKeyAccountHelper

    override fun onViewReady(savedInstanceState: Bundle?) {
        setupToolbar(toolbar_view, View.OnClickListener { onBackPressed() }, true, icon = R.drawable.ic_toolbar_back_black)

        text_address.text = publicKeyAccountHelper.publicKeyAccount?.address
        frame_share.click {
            shareAddress()
        }

        text_aliases_count.text = String.format(getString(R.string.alias_dialog_you_have), 3)

        card_aliases.click {
            val bottomSheetFragment = AddressesAndKeysBottomSheetFragment()
            bottomSheetFragment.type = AddressesAndKeysBottomSheetFragment.TYPE_CONTENT
            bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
        }

        frame_copy.click {
            text_copy.text = getString(R.string.common_copied)
            text_copy.setTextColor(findColor(R.color.success400))
            text_copy.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_18_success_400, 0, 0, 0);
            text_address.copyToClipboard()

            runDelayed(1500, {
                this.text_copy.notNull {
                    text_copy.text = getString(R.string.my_address_qr_copy)
                    text_copy.setTextColor(findColor(R.color.black))
                    text_copy.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_copy_18_submit_400, 0, 0, 0);
                }
            })
        }

        presenter.generateAvatars(text_address.text.toString(), image_avatar)
        presenter.generateQRCode(text_address.text.toString(), resources.getDimension(R.dimen._200sdp).toInt())
    }

    override fun showQRCode(qrCode: Bitmap?) {
        image_view_recipient_action.setImageBitmap(qrCode)
    }

    private fun shareAddress() {
        val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, text_address.text)
        startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.app_name)))
    }

    private fun copyToClipboard(view: TextView, text: Int) {

    }
}