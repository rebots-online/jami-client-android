/*
 *  Copyright (C) 2004-2021 Savoir-faire Linux Inc.
 *
 *  Author: Aline Bonnet <aline.bonnet@savoirfairelinux.com>
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
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cx.ring.contactrequests

import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import cx.ring.R
import cx.ring.account.AccountEditionFragment
import cx.ring.adapters.SmartListAdapter
import cx.ring.client.HomeActivity
import cx.ring.databinding.FragPendingContactRequestsBinding
import cx.ring.mvp.BaseSupportFragment
import cx.ring.utils.ActionHelper
import cx.ring.utils.ClipboardHelper
import cx.ring.viewholders.SmartListViewHolder.SmartListListeners
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.jami.contactrequests.ContactRequestsPresenter
import net.jami.contactrequests.ContactRequestsView
import net.jami.model.Uri
import net.jami.smartlist.SmartListViewModel

@AndroidEntryPoint
class ContactRequestsFragment :
    BaseSupportFragment<ContactRequestsPresenter, ContactRequestsView>(), ContactRequestsView,
    SmartListListeners {
    private var mAdapter: SmartListAdapter? = null
    private var binding: FragPendingContactRequestsBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragPendingContactRequestsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mAdapter = null
        binding = null
    }

    fun presentForAccount(accountId: String?) {
        if (accountId != null)
            arguments?.putString(AccountEditionFragment.ACCOUNT_ID_KEY, accountId)
        presenter.updateAccount(accountId)
    }

    override fun onStart() {
        super.onStart()
        presenter.updateAccount(arguments?.getString(AccountEditionFragment.ACCOUNT_ID_KEY))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.clear()
    }

    override fun updateView(list: MutableList<SmartListViewModel>, disposable: CompositeDisposable) {
        val binding = binding ?: return
        if (list.isNotEmpty()) {
            binding.paneRingID.visibility = View.GONE
        }
        binding.placeholder.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val adapter = mAdapter
        if (adapter != null) {
            adapter.update(list)
        } else {
            binding.requestsList.layoutManager = LinearLayoutManager(activity)
            mAdapter = SmartListAdapter(list, this@ContactRequestsFragment, disposable).apply {
                binding.requestsList.adapter = this
            }
        }
        binding.requestsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                (activity as HomeActivity?)?.setToolbarElevation(recyclerView.canScrollVertically(SCROLL_DIRECTION_UP))
            }
        })
    }

    override fun updateItem(item: SmartListViewModel) {
        mAdapter?.update(item)
    }

    override fun goToConversation(accountId: String, contactId: Uri) {
        (requireActivity() as HomeActivity).startConversation(accountId, contactId)
    }

    override fun copyNumber(uri: Uri) {
        val number = uri.toString()
        ClipboardHelper.copyToClipboard(requireContext(), number)
        val snackbarText = getString(
            R.string.conversation_action_copied_peer_number_clipboard,
            ActionHelper.getShortenedNumber(number)
        )
        Snackbar.make(binding!!.root, snackbarText, Snackbar.LENGTH_LONG).show()
    }

    override fun onItemClick(item: SmartListViewModel) {
        presenter.contactRequestClicked(item.accountId, item.uri)
    }

    override fun onItemLongClick(item: SmartListViewModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(R.array.swarm_actions) { dialog, which ->
                when (which) {
                    0 -> presenter.copyNumber(item)
                    1 -> presenter.removeConversation(item)
                    2 -> presenter.banContact(item)
                }
            }
            .show()
    }

    companion object {
        private val TAG = ContactRequestsFragment::class.simpleName!!
        private const val SCROLL_DIRECTION_UP = -1
    }
}