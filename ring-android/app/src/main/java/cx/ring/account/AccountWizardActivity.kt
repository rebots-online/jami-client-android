/*
 *  Copyright (C) 2004-2021 Savoir-faire Linux Inc.
 *
 *  Author: Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *  Author: Adrien Béraud <adrien.beraud@savoirfairelinux.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package cx.ring.account

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cx.ring.R
import cx.ring.application.JamiApplication
import cx.ring.client.HomeActivity
import cx.ring.fragments.AccountMigrationFragment
import cx.ring.fragments.SIPAccountCreationFragment
import cx.ring.mvp.BaseActivity
import cx.ring.services.VCardServiceImpl
import dagger.hilt.android.AndroidEntryPoint
import ezvcard.VCard
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import net.jami.account.AccountWizardPresenter
import net.jami.account.AccountWizardView
import net.jami.model.Account
import net.jami.model.AccountConfig
import net.jami.model.AccountCreationModel
import net.jami.utils.VCardUtils

@AndroidEntryPoint
class AccountWizardActivity : BaseActivity<AccountWizardPresenter>(), AccountWizardView {
    private var mProgress: ProgressDialog? = null
    private var mAccountType: String? = null
    private var mAlertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JamiApplication.instance?.startDaemon()
        setContentView(R.layout.activity_wizard)
        var accountToMigrate: String? = null
        val intent = intent
        if (intent != null) {
            mAccountType = intent.action
            val path = intent.data
            if (path != null) {
                accountToMigrate = path.lastPathSegment
            }
        }
        if (mAccountType == null) {
            mAccountType = AccountConfig.ACCOUNT_TYPE_RING
        }
        if (savedInstanceState == null) {
            if (accountToMigrate != null) {
                val fragment = AccountMigrationFragment().apply {
                    arguments = Bundle().apply { putString(AccountEditionFragment.ACCOUNT_ID_KEY, accountToMigrate) }
                }
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.wizard_container, fragment)
                    .commit()
            } else {
                presenter.init(getIntent().action ?: AccountConfig.ACCOUNT_TYPE_RING)
            }
        }
    }

    override fun onDestroy() {
        mProgress?.let { progress ->
            progress.dismiss()
            mProgress = null
        }
        mAlertDialog?.let { alertDialog ->
            alertDialog.setOnDismissListener(null)
            alertDialog.dismiss()
            mAlertDialog = null
        }
        super.onDestroy()
    }

    override fun saveProfile(account: Account, accountCreationModel: AccountCreationModel): Single<VCard> {
        val filedir = filesDir
        return accountCreationModel.toVCard()
            .flatMap { vcard: VCard ->
                account.loadedProfile = Single.fromCallable { VCardServiceImpl.readData(vcard) }.cache()
                VCardUtils.saveLocalProfileToDisk(vcard, account.accountId, filedir)
            }
            .subscribeOn(Schedulers.io())
    }

    fun createAccount(accountCreationModel: AccountCreationModel) {
        if (!TextUtils.isEmpty(accountCreationModel.managementServer)) {
            presenter.initJamiAccountConnect(accountCreationModel, getText(R.string.ring_account_default_name).toString())
        } else if (accountCreationModel.isLink) {
            presenter.initJamiAccountLink(accountCreationModel, getText(R.string.ring_account_default_name).toString())
        } else {
            presenter.initJamiAccountCreation(accountCreationModel, getText(R.string.ring_account_default_name).toString())
        }
    }

    override fun goToHomeCreation() {
        val fragmentManager = supportFragmentManager
        fragmentManager.beginTransaction()
            .replace(R.id.wizard_container, HomeAccountCreationFragment(), HomeAccountCreationFragment.TAG)
            .commit()
    }

    override fun goToSipCreation() {
        val fragment: Fragment = SIPAccountCreationFragment()
        val fragmentManager = supportFragmentManager
        fragmentManager.beginTransaction()
            .replace(R.id.wizard_container, fragment, SIPAccountCreationFragment.TAG)
            .commit()
    }

    override fun goToProfileCreation(accountCreationModel: AccountCreationModel) {
        val fragments = supportFragmentManager.fragments
        if (fragments.size > 0) {
            val fragment = fragments[0]
            if (fragment is JamiLinkAccountFragment) {
                fragment.scrollPagerFragment(accountCreationModel)
            } else if (fragment is JamiAccountConnectFragment) {
                profileCreated(accountCreationModel, false)
            }
        }
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.wizard_container)
        if (fragment is ProfileCreationFragment) finish() else super.onBackPressed()
    }

    override fun displayProgress(display: Boolean) {
        if (display) {
            mProgress = ProgressDialog(this@AccountWizardActivity).apply {
                setTitle(R.string.dialog_wait_create)
                setMessage(getString(R.string.dialog_wait_create_details))
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                show()
            }
        } else {
            mProgress?.apply {
                if (isShowing) dismiss()
                mProgress = null
            }
        }
    }

    override fun displayCreationError() {
        Toast.makeText(this@AccountWizardActivity, "Error creating account", Toast.LENGTH_SHORT)
            .show()
    }

    override fun blockOrientation() {
        //orientation is locked during the create of account to avoid the destruction of the thread
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    override fun finish(affinity: Boolean) {
        if (affinity) {
            startActivity(Intent(this@AccountWizardActivity, HomeActivity::class.java))
            finish()
        } else {
            finishAffinity()
        }
    }

    override fun displayGenericError() {
        if (mAlertDialog != null && mAlertDialog!!.isShowing) {
            return
        }
        mAlertDialog = MaterialAlertDialogBuilder(this@AccountWizardActivity)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.account_cannot_be_found_title)
            .setMessage(R.string.account_export_end_decryption_message)
            .show()
    }

    override fun displayNetworkError() {
        if (mAlertDialog != null && mAlertDialog!!.isShowing) {
            return
        }
        mAlertDialog = MaterialAlertDialogBuilder(this@AccountWizardActivity)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.account_no_network_title)
            .setMessage(R.string.account_no_network_message)
            .show()
    }

    override fun displayCannotBeFoundError() {
        if (mAlertDialog != null && mAlertDialog!!.isShowing) {
            return
        }
        mAlertDialog = MaterialAlertDialogBuilder(this@AccountWizardActivity)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.account_cannot_be_found_title)
            .setMessage(R.string.account_cannot_be_found_message)
            .setOnDismissListener { supportFragmentManager.popBackStack() }
            .show()
    }

    override fun displaySuccessDialog() {
        if (mAlertDialog != null && mAlertDialog!!.isShowing) {
            return
        }
        setResult(RESULT_OK, Intent())
        //unlock the screen orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        presenter.successDialogClosed()
    }

    fun profileCreated(accountCreationModel: AccountCreationModel, saveProfile: Boolean) {
        presenter.profileCreated(accountCreationModel, saveProfile)
    }

    companion object {
        val TAG = AccountWizardActivity::class.simpleName!!
    }
}