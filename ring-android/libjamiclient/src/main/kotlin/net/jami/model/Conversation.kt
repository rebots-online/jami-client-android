/*
 *  Copyright (C) 2004-2022 Savoir-faire Linux Inc.
 *
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package net.jami.model

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.SingleSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlin.jvm.Synchronized
import net.jami.utils.Log
import java.lang.IllegalStateException
import java.util.*

class Conversation : ConversationHistory {
    val accountId: String
    val uri: Uri
    val contacts: MutableList<Contact>
    private val rawHistory: NavigableMap<Long, Interaction> = TreeMap()
    private val currentCalls = ArrayList<Conference>()
    val aggregateHistory = ArrayList<Interaction>(32)
    private var lastDisplayed: Interaction? = null
    private val updatedElementSubject: Subject<Pair<Interaction, ElementStatus>> = PublishSubject.create()
    private val lastDisplayedSubject: Subject<Interaction> = BehaviorSubject.create()
    private val clearedSubject: Subject<List<Interaction>> = PublishSubject.create()
    private val callsSubject: Subject<List<Conference>> = BehaviorSubject.createDefault(emptyList())
    private val composingStatusSubject: Subject<Account.ComposingStatus> = BehaviorSubject.createDefault(Account.ComposingStatus.Idle)
    private val color: Subject<Int> = BehaviorSubject.create()
    private val symbol: Subject<CharSequence> = BehaviorSubject.create()
    private val mContactSubject: Subject<List<Contact>> = BehaviorSubject.create()
    var loaded: Single<Conversation>? = null
    var lastElementLoaded: Completable? = null
    private val mRoots: MutableSet<String> = HashSet(2)
    private val mMessages: MutableMap<String, Interaction> = HashMap(16)
    var lastRead: String? = null
        private set
    var lastNotified: String? = null
        private set
    private val mMode: Subject<Mode>

    // runtime flag set to true if the user is currently viewing this conversation
    private var mVisible = false
    private val mVisibleSubject: Subject<Boolean> = BehaviorSubject.createDefault(mVisible)

    // indicate the list needs sorting
    private var mDirty = false
    private var mLoadingSubject: SingleSubject<Conversation>? = null

    val mode: Observable<Mode>
        get() = mMode
    val isSwarm: Boolean
        get() = Uri.SWARM_SCHEME == uri.scheme

    val contactUpdates: Observable<List<Contact>>
        get() = mContactSubject

    var loading: SingleSubject<Conversation>?
        get() = mLoadingSubject
        set(l) {
            mLoadingSubject?.let { loading ->
                if (!loading.hasValue() && !loading.hasThrowable())
                    loading.onError(IllegalStateException())
            }
            mLoadingSubject = l
        }

    enum class ElementStatus {
        UPDATE, REMOVE, ADD
    }

    val updatedElements: Observable<Pair<Interaction, ElementStatus>>
        get() = updatedElementSubject

    val cleared: Observable<List<Interaction>>
        get() = clearedSubject
    val calls: Observable<List<Conference>>
        get() = callsSubject
    val composingStatus: Observable<Account.ComposingStatus>
        get() = composingStatusSubject

    val sortedHistory: Single<List<Interaction>> = Single.fromCallable {
        sortHistory()
        aggregateHistory
    }
    val lastEventSubject: Subject<Interaction> = BehaviorSubject.create()
    val currentStateSubject: Observable<Pair<Interaction, Boolean>> = Observable.combineLatest(lastEventSubject, callsSubject.map { calls ->
        for (call in calls)
            if (call.isOnGoing)
                return@map true
        false
    }) { event, hasCurrentCall -> Pair(event, hasCurrentCall) }

    val swarmRoot: Collection<String>
        get() = mRoots

    constructor(accountId: String, contact: Contact) {
        this.accountId = accountId
        contacts = mutableListOf(contact)
        uri = contact.uri
        participant = contact.uri.uri
        mContactSubject.onNext(contacts)
        mMode = BehaviorSubject.createDefault(Mode.Legacy)
    }

    constructor(accountId: String, uri: Uri, mode: Mode) {
        this.accountId = accountId
        this.uri = uri
        contacts = ArrayList(3)
        mMode = BehaviorSubject.createDefault(mode)
    }

    fun getConference(confId: String?): Conference? {
        val id = confId ?: return null
        for (c in currentCalls)
            if (c.id == id || c.getCallById(id) != null)
                return c
        return null
    }

    fun composingStatusChanged(contact: Contact?, composing: Account.ComposingStatus) {
        composingStatusSubject.onNext(composing)
    }

    fun matches(query: String): Boolean {
        for (contact in contacts) {
            if (contact.matches(query)) return true
        }
        return false
    }

    /*val displayName: String?
        get() = contacts[0].displayName*/

    fun addContact(contact: Contact) {
        contacts.add(contact)
        mContactSubject.onNext(contacts)
    }

    fun removeContact(contact: Contact) {
        contacts.remove(contact)
        mContactSubject.onNext(contacts)
    }

    @Synchronized
    fun readMessages(): List<Interaction> {
        val interactions = ArrayList<Interaction>()
        if (isSwarm) {
            if (aggregateHistory.isNotEmpty()) {
                var n = aggregateHistory.size
                do {
                    if (n == 0) break
                    val i = aggregateHistory[--n]
                    if (!i.isRead) {
                        i.read()
                        interactions.add(i)
                        lastRead = i.messageId
                    }
                } while (i.type == Interaction.InteractionType.INVALID)
            }
        } else {
            for (e in rawHistory.descendingMap().values) {
                if (e.type != Interaction.InteractionType.TEXT) continue
                if (e.isRead) {
                    break
                }
                e.read()
                interactions.add(e)
            }
        }
        // Update the last event if it was just read
        interactions.firstOrNull { it.type != Interaction.InteractionType.INVALID }?.let {
            lastEventSubject.onNext(it)
        }
        return interactions
    }

    @Synchronized
    fun getMessage(messageId: String): Interaction? {
        return mMessages[messageId]
    }

    fun setLastMessageRead(lastMessageRead: String?) {
        lastRead = lastMessageRead
    }

    fun setLastMessageNotified(lastMessage: String?) {
        lastNotified = lastMessage
    }

    fun stopLoading(): Boolean {
        val ret = mLoadingSubject
        mLoadingSubject = null
        if (ret != null) {
            ret.onSuccess(this)
            return true
        }
        return false
    }

    fun setMode(mode: Mode) {
        mMode.onNext(mode)
    }

    fun getLastDisplayed(): Observable<Interaction> = lastDisplayedSubject

    fun addConference(conference: Conference?) {
        if (conference == null) {
            return
        }
        for (i in currentCalls.indices) {
            val currentConference = currentCalls[i]
            if (currentConference === conference) {
                return
            }
            if (currentConference.id == conference.id) {
                currentCalls[i] = conference
                return
            }
        }
        currentCalls.add(conference)
        callsSubject.onNext(currentCalls)
    }

    fun removeConference(c: Conference) {
        currentCalls.remove(c)
        callsSubject.onNext(currentCalls)
    }

    var isVisible: Boolean
        get() = mVisible
        set(visible) {
            mVisible = visible
            mVisibleSubject.onNext(mVisible)
        }

    fun getVisible(): Observable<Boolean> = mVisibleSubject

    val contact: Contact?
        get() {
            if (contacts.size == 1) return contacts[0]
            if (isSwarm) {
                check(contacts.size <= 2) { "getContact() called for group conversation of size " + contacts.size }
            }
            for (contact in contacts) {
                if (!contact.isUser) return contact
            }
            return null
        }

    fun addCall(call: Call) {
        if (!isSwarm && callHistory.contains(call)) {
            return
        }
        mDirty = true
        aggregateHistory.add(call)
        updatedElementSubject.onNext(Pair(call, ElementStatus.ADD))
    }

    private fun setInteractionProperties(interaction: Interaction) {
        interaction.account = accountId
        if (interaction.contact == null) {
            if (contacts.size == 1) interaction.contact = contacts[0] else {
                if (interaction.author == null) {
                    Log.e(TAG, "Can't set interaction properties: no author for type:" + interaction.type + " id:" + interaction.id + " status:" + interaction.mStatus)
                } else {
                    interaction.contact = findContact(Uri.fromString(interaction.author!!))
                }
            }
        }
    }

    fun findContact(uri: Uri): Contact? {
        for (contact in contacts) {
            if (contact.uri == uri) {
                return contact
            }
        }
        return null
    }

    fun addTextMessage(txt: TextMessage) {
        if (mVisible)
            txt.read()
        setInteractionProperties(txt)
        rawHistory[txt.timestamp] = txt
        mDirty = true
        aggregateHistory.add(txt)
        updatedElementSubject.onNext(Pair(txt, ElementStatus.ADD))
    }

    fun addRequestEvent(request: TrustRequest, contact: Contact) {
        if (isSwarm) return
        val event = ContactEvent(contact, request)
        mDirty = true
        aggregateHistory.add(event)
        updatedElementSubject.onNext(Pair(event, ElementStatus.ADD))
    }

    fun addContactEvent(contact: Contact) {
        addContactEvent(ContactEvent(contact))
    }

    fun addContactEvent(contactEvent: ContactEvent) {
        mDirty = true
        aggregateHistory.add(contactEvent)
        updatedElementSubject.onNext(Pair(contactEvent, ElementStatus.ADD))
    }

    fun addFileTransfer(dataTransfer: DataTransfer) {
        if (aggregateHistory.contains(dataTransfer)) {
            return
        }
        mDirty = true
        aggregateHistory.add(dataTransfer)
        updatedElementSubject.onNext(Pair(dataTransfer, ElementStatus.ADD))
    }

    private fun isAfter(previous: Interaction, query: Interaction?): Boolean {
        var query = query
        return if (isSwarm) {
            while (query?.parentId != null) {
                if (query.parentId == previous.messageId)
                    return true
                query = mMessages[query.parentId]
            }
            false
        } else {
            previous.timestamp < query!!.timestamp
        }
    }

    fun updateInteraction(element: Interaction) {
        Log.e(TAG, "updateInteraction: ${element.messageId} ${element.status}")
        val lastDisplayed = lastDisplayed
        if (isSwarm) {
            val e = mMessages[element.messageId]
            if (e != null) {
                e.status = element.status
                updatedElementSubject.onNext(Pair(e, ElementStatus.UPDATE))
                if (e.status == Interaction.InteractionStatus.DISPLAYED) {
                    if (lastDisplayed == null || isAfter(lastDisplayed, e)) {
                        this.lastDisplayed = e
                        lastDisplayedSubject.onNext(e)
                    }
                }
            } else {
                Log.e(TAG, "Can't find swarm message to update: ${element.messageId}")
            }
        } else {
            setInteractionProperties(element)
            val time = element.timestamp
            val msgs = rawHistory.subMap(time, true, time, true)
            for (txt in msgs.values) {
                if (txt.id == element.id) {
                    txt.status = element.status
                    updatedElementSubject.onNext(Pair(txt, ElementStatus.UPDATE))
                    if (element.status == Interaction.InteractionStatus.DISPLAYED) {
                        if (lastDisplayed == null || isAfter(lastDisplayed, element)) {
                            this.lastDisplayed = element
                            lastDisplayedSubject.onNext(element)
                        }
                    }
                    return
                }
            }
            Log.e(TAG, "Can't find message to update: ${element.id}")
        }
    }

    fun sortHistory() {
        if (mDirty) {
            Log.w(TAG, "sortHistory()")
            synchronized(aggregateHistory) {
                aggregateHistory.sortWith { c1, c2 -> c1.timestamp.compareTo(c2.timestamp) }
                for (i in aggregateHistory.asReversed())
                    if (i.type != Interaction.InteractionType.INVALID) {
                        lastEventSubject.onNext(aggregateHistory.last())
                        break
                    }
            }
            mDirty = false
        }
    }

    val lastEvent: Interaction?
        get() {
            sortHistory()
            return aggregateHistory.lastOrNull { it.type != Interaction.InteractionType.INVALID }
        }

    val currentCall: Conference?
        get() = if (currentCalls.isEmpty()) null else currentCalls[0]
    private val callHistory: Collection<Call>
        get() {
            val result: MutableList<Call> = ArrayList()
            for (interaction in aggregateHistory) {
                if (interaction.type == Interaction.InteractionType.CALL) {
                    result.add(interaction as Call)
                }
            }
            return result
        }
    val unreadTextMessages: TreeMap<Long, TextMessage>
        get() {
            val texts = TreeMap<Long, TextMessage>()
            if (isSwarm) {
                for (j in aggregateHistory.indices.reversed()) {
                    val i = aggregateHistory[j]
                    if (i !is TextMessage) continue
                    if (i.isRead || i.isNotified) break
                    texts[i.timestamp] = i
                }
            } else {
                for ((key, value) in rawHistory.descendingMap()) {
                    if (value.type == Interaction.InteractionType.TEXT) {
                        val message = value as TextMessage
                        if (message.isRead || message.isNotified) break
                        texts[key] = message
                    }
                }
            }
            return texts
        }

    private fun findConversationElement(transferId: Int): Interaction? {
        for (interaction in aggregateHistory) {
            if (interaction.type == Interaction.InteractionType.DATA_TRANSFER) {
                if (transferId == interaction.id) {
                    return interaction
                }
            }
        }
        return null
    }

    private fun removeSwarmInteraction(messageId: String): Boolean {
        val i = mMessages.remove(messageId)
        if (i != null) {
            aggregateHistory.remove(i)
            return true
        }
        return false
    }

    private fun removeInteraction(interactionId: Long): Boolean {
        val it = aggregateHistory.iterator()
        while (it.hasNext()) {
            val interaction = it.next()
            if (interactionId == interaction.id.toLong()) {
                it.remove()
                return true
            }
        }
        return false
    }

    /**
     * Clears the conversation cache.
     * @param delete true if you do not want to re-add contact events
     */
    fun clearHistory(delete: Boolean) {
        aggregateHistory.clear()
        rawHistory.clear()
        mDirty = false
        if (!delete && contacts.size == 1)
            aggregateHistory.add(ContactEvent(contacts[0]))
        clearedSubject.onNext(aggregateHistory)
    }

    fun setHistory(loadedConversation: List<Interaction>) {
        aggregateHistory.ensureCapacity(loadedConversation.size)
        var last: Interaction? = null
        for (i in loadedConversation) {
            val interaction = getTypedInteraction(i)
            setInteractionProperties(interaction)
            aggregateHistory.add(interaction)
            rawHistory[interaction.timestamp] = interaction
            if (!i.isIncoming && i.status == Interaction.InteractionStatus.DISPLAYED) last = i
        }
        if (last != null) {
            lastDisplayed = last
            lastDisplayedSubject.onNext(last)
        }
        lastEvent?.let { lastEventSubject.onNext(it) }
        mDirty = false
    }

    fun addElement(interaction: Interaction) {
        setInteractionProperties(interaction)
        when (interaction.type) {
            Interaction.InteractionType.TEXT -> addTextMessage(TextMessage(interaction))
            Interaction.InteractionType.CALL -> addCall(Call(interaction))
            Interaction.InteractionType.CONTACT -> addContactEvent(ContactEvent(interaction))
            Interaction.InteractionType.DATA_TRANSFER -> addFileTransfer(DataTransfer(interaction))
            else -> {}
        }
    }

    fun addSwarmElement(interaction: Interaction): Boolean {
        if (mMessages.containsKey(interaction.messageId)) {
            return false
        }
        mMessages[interaction.messageId!!] = interaction
        mRoots.remove(interaction.messageId)
        if (interaction.parentId != null && !mMessages.containsKey(interaction.parentId)) {
            mRoots.add(interaction.parentId!!)
            // Log.w(TAG, "@@@ Found new root for " + getUri() + " " + parent + " -> " + mRoots);
        }
        if (lastRead != null && lastRead == interaction.messageId) interaction.read()
        if (lastNotified != null && lastNotified == interaction.messageId) interaction.isNotified = true
        var newLeaf = false
        var added = false
        if (aggregateHistory.isEmpty() || aggregateHistory.last().messageId == interaction.parentId) {
            // New leaf
            added = true
            newLeaf = true
            aggregateHistory.add(interaction)
            updatedElementSubject.onNext(Pair(interaction, ElementStatus.ADD))
        } else {
            // New root or normal node
            for (i in aggregateHistory.indices) {
                if (interaction.messageId == aggregateHistory[i].parentId) {
                    //Log.w(TAG, "@@@ New root node at " + i);
                    aggregateHistory.add(i, interaction)
                    updatedElementSubject.onNext(Pair(interaction, ElementStatus.ADD))
                    added = true
                    break
                }
            }
            if (!added) {
                for (i in aggregateHistory.indices.reversed()) {
                    if (aggregateHistory[i].messageId == interaction.parentId) {
                        added = true
                        newLeaf = true
                        aggregateHistory.add(i + 1, interaction)
                        updatedElementSubject.onNext(Pair(interaction, ElementStatus.ADD))
                        break
                    }
                }
            }
        }
        if (newLeaf) {
            if (isVisible) {
                interaction.read()
                setLastMessageRead(interaction.messageId)
            }
            if (interaction.type != Interaction.InteractionType.INVALID)
                lastEventSubject.onNext(interaction)
        }
        if (!added) {
            Log.e(TAG, "Can't attach interaction ${interaction.messageId} with parent ${interaction.parentId}")
        }
        return newLeaf
    }

    fun isLoaded(): Boolean {
        return mMessages.isNotEmpty() && mRoots.isEmpty()
    }

    fun updateFileTransfer(transfer: DataTransfer, eventCode: Interaction.InteractionStatus) {
        val dataTransfer = (if (isSwarm) transfer else findConversationElement(transfer.id)) as DataTransfer?
        if (dataTransfer != null) {
            dataTransfer.status = eventCode
            updatedElementSubject.onNext(Pair(dataTransfer, ElementStatus.UPDATE))
        }
    }

    fun removeInteraction(interaction: Interaction) {
        if (isSwarm) {
            if (removeSwarmInteraction(interaction.messageId!!)) updatedElementSubject.onNext(Pair(interaction, ElementStatus.REMOVE))
        } else {
            if (removeInteraction(interaction.id.toLong())) updatedElementSubject.onNext(Pair(interaction, ElementStatus.REMOVE))
        }
    }

    fun removeAll() {
        aggregateHistory.clear()
        currentCalls.clear()
        rawHistory.clear()
        mDirty = true
    }

    fun setColor(c: Int) {
        color.onNext(c)
    }

    fun setSymbol(s: CharSequence) {
        symbol.onNext(s)
    }

    fun getColor(): Observable<Int> {
        return color
    }

    fun getSymbol(): Observable<CharSequence> {
        return symbol
    }

    enum class Mode {
        OneToOne, AdminInvitesOnly, InvitesOnly,  // Non-daemon modes
        Syncing, Public, Legacy, Request
    }

    interface ConversationActionCallback {
        fun removeConversation(accountId: String, conversationUri: Uri)
        fun clearConversation(accountId: String, conversationUri: Uri)
        fun copyContactNumberToClipboard(contactNumber: String)
    }

    companion object {
        private val TAG = Conversation::class.simpleName!!
        private fun getTypedInteraction(interaction: Interaction): Interaction {
            return when (interaction.type) {
                Interaction.InteractionType.TEXT -> TextMessage(interaction)
                Interaction.InteractionType.CALL -> Call(interaction)
                Interaction.InteractionType.CONTACT -> ContactEvent(interaction)
                Interaction.InteractionType.DATA_TRANSFER -> DataTransfer(interaction)
                else -> interaction
            }
        }
    }
}