/*
 * Copyright (C) 2004-2023 Savoir-faire Linux Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package cx.ring.account

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import cx.ring.R
import cx.ring.account.pinInput.EditTextPinInputFragment
import cx.ring.account.pinInput.EditTextPinInputViewModel
import cx.ring.account.pinInput.QrCodePinInputFragment
import cx.ring.account.pinInput.QrCodePinInputViewModel
import cx.ring.databinding.FragAccJamiLinkPasswordBinding
import cx.ring.mvp.BaseSupportFragment
import dagger.hilt.android.AndroidEntryPoint
import net.jami.account.JamiLinkAccountPresenter
import net.jami.account.JamiLinkAccountView

@AndroidEntryPoint
class JamiLinkAccountPasswordFragment :
    BaseSupportFragment<JamiLinkAccountPresenter, JamiLinkAccountView>(),
    JamiLinkAccountView {
    private val model: AccountCreationViewModel by activityViewModels()
    private var binding: FragAccJamiLinkPasswordBinding? = null

    // the 2 view models connected to this fragment
    private val qrCodePinInputViewModel by lazy {
        ViewModelProvider(this)[QrCodePinInputViewModel::class.java]
    }
    private val editTextPinInputViewModel by lazy {
        ViewModelProvider(this)[EditTextPinInputViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragAccJamiLinkPasswordBinding.inflate(inflater, container, false).apply {
            viewPager.adapter = SectionsPagerAdapter(
                root.context,
                childFragmentManager
            )
            tabs.setupWithViewPager(viewPager)
            tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    presenter.resetPin()
                    // emit the pin again when switching tabs
                    if (tab?.position == 0) {
                        qrCodePinInputViewModel.emitPinAgain()
                    } else {
                        editTextPinInputViewModel.emitPinAgain()
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
            linkButton.setOnClickListener { presenter.linkClicked() }
            ringExistingPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    presenter.passwordChanged(s.toString())
                }
            })
            binding = this
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // init the 2 view models
        qrCodePinInputViewModel.init({
            presenter.pinChanged(it)
        }, { presenter.resetPin() })
        editTextPinInputViewModel.init({
            presenter.pinChanged(it)
        }, { presenter.resetPin() })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun initPresenter(presenter: JamiLinkAccountPresenter) {
        presenter.init(model.model)
    }

    override fun enableLinkButton(enable: Boolean) {
        binding!!.linkButton.isEnabled = enable
    }

    override fun showPin(show: Boolean) {
        val binding = binding ?: return
        binding.passwordBox.visibility = if (show) View.VISIBLE else View.GONE
        binding.linkButton.setText(if (show) R.string.account_link_device_button else R.string.account_link_archive_button)
    }

    override fun createAccount() {
        (activity as AccountWizardActivity?)?.createAccount()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(binding!!.ringExistingPassword.windowToken, 0)
    }

    override fun cancel() {
        activity?.onBackPressedDispatcher?.onBackPressed()
    }

    companion object {
        val TAG = JamiLinkAccountPasswordFragment::class.simpleName!!
    }

    internal class SectionsPagerAdapter(private val mContext: Context, fm: FragmentManager) :
        FragmentPagerAdapter(fm) {
        @StringRes
        private val TAB_TITLES =
            intArrayOf(R.string.connect_device_scanqr, R.string.connect_device_enterPIN)

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> QrCodePinInputFragment() // scan qr code
                1 -> EditTextPinInputFragment()  // or enter pin
                else -> throw IllegalArgumentException()
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return mContext.resources.getString(TAB_TITLES[position])
        }

        override fun getCount(): Int {
            return TAB_TITLES.size
        }
    }
}