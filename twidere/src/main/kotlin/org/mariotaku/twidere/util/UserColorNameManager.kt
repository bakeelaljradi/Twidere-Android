/*
 *             Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.util

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.support.v4.util.ArrayMap
import android.support.v4.util.LruCache
import org.mariotaku.kpreferences.get
import org.mariotaku.microblog.library.model.microblog.User
import org.mariotaku.twidere.Constants.KEY_NAME_FIRST
import org.mariotaku.twidere.TwidereConstants.USER_COLOR_PREFERENCES_NAME
import org.mariotaku.twidere.TwidereConstants.USER_NICKNAME_PREFERENCES_NAME
import org.mariotaku.twidere.constant.nameFirstKey
import org.mariotaku.twidere.extension.model.api.key
import org.mariotaku.twidere.model.*
import org.mariotaku.twidere.singleton.PreferencesSingleton
import org.mariotaku.twidere.util.lang.ApplicationContextSingletonHolder
import org.mariotaku.twidere.util.preference.PreferenceChangeNotifier
import kotlin.collections.set

class UserColorNameManager private constructor(context: Context) {

    val colorPreferences: SharedPreferences = context.getSharedPreferences(USER_COLOR_PREFERENCES_NAME, Context.MODE_PRIVATE)
    val nicknamePreferences: SharedPreferences = context.getSharedPreferences(USER_NICKNAME_PREFERENCES_NAME, Context.MODE_PRIVATE)

    val nicknames: Map<String, Any?>
        get() = nicknamePreferences.all

    var nameFirst: Boolean = false

    private val colorCache = LruCache<String, Int>(512)
    private val nicknameCache = LruCache<String, String>(256)

    private val colorChangedListeners = ArrayMap<UserColorChangedListener, OnSharedPreferenceChangeListener>()
    private val nicknameChangedListeners = ArrayMap<UserNicknameChangedListener, OnSharedPreferenceChangeListener>()

    fun clearUserColor(userKey: UserKey) {
        val editor = colorPreferences.edit()
        val userKeyString = userKey.toString()
        colorCache.remove(userKeyString)
        editor.remove(userKeyString)
        editor.apply()
    }

    fun setUserColor(userKey: UserKey, color: Int) {
        val editor = colorPreferences.edit()
        val userKeyString = userKey.toString()
        colorCache.put(userKeyString, color)
        editor.putInt(userKeyString, color)
        editor.apply()
    }

    fun setUserNickname(userKey: UserKey, nickname: String) {
        val editor = nicknamePreferences.edit()
        val userKeyString = userKey.toString()
        nicknameCache.put(userKeyString, nickname)
        editor.putString(userKeyString, nickname)
        editor.apply()
    }

    fun clearUserNickname(userKey: UserKey) {
        val editor = nicknamePreferences.edit()
        val userKeyString = userKey.toString()
        nicknameCache.remove(userKeyString)
        editor.remove(userKeyString)
        editor.apply()
    }

    fun getDisplayName(user: ParcelableUser): String {
        return getDisplayName(user.key, user.name, user.screen_name)
    }

    fun getDisplayName(user: ParcelableLiteUser): String {
        return getDisplayName(user.key, user.name, user.screen_name)
    }

    fun getDisplayName(user: User): String {
        return getDisplayName(user.key, user.name, user.screenName)
    }

    fun getDisplayName(user: ParcelableUserList): String {
        return getDisplayName(user.user_key, user.user_name, user.user_screen_name)
    }

    fun getDisplayName(status: ParcelableStatus): String {
        return getDisplayName(status.user_key, status.user_name, status.user_screen_name)
    }

    fun getDisplayName(user: FiltersData.UserItem): String {
        return getDisplayName(user.userKey, user.name, user.screenName)
    }

    fun getDisplayName(userKey: UserKey, name: String, screenName: String): String {
        return getDisplayName(userKey.toString(), name, screenName)
    }

    fun getDisplayName(key: String, name: String, screenName: String): String {
        val nick = getUserNicknameInternal(key)
        return decideDisplayName(nick, name, screenName, nameFirst)
    }

    fun getUserColor(userKey: UserKey): Int {
        return getUserColor(userKey.toString())
    }

    fun getUserColor(userId: String): Int {
        val cached = colorCache.get(userId)
        if (cached != null) return cached
        val color = colorPreferences.getInt(userId, Color.TRANSPARENT)
        colorCache.put(userId, color)
        return color
    }

    fun getUserNickname(userKey: UserKey): String? {
        val userKeyString = userKey.toString()
        return getUserNicknameInternal(userKeyString)
    }

    fun getUserNickname(userKey: UserKey, name: String): String? {
        val nick = getUserNickname(userKey)
        return decideNickname(nick, name)
    }

    fun getUserNickname(key: String, name: String): String? {
        val nick = getUserNicknameInternal(key)
        return decideNickname(nick, name)
    }

    fun decideDisplayName(nickname: String?, name: String, screenName: String): String {
        return nickname ?: if (nameFirst) name else "@$screenName"
    }

    fun registerColorChangedListener(listener: UserColorChangedListener) {
        val preferenceChangeListener = OnColorPreferenceChangeListener(listener)
        colorPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        colorChangedListeners[listener] = preferenceChangeListener
    }

    fun registerNicknameChangedListener(listener: UserNicknameChangedListener) {
        val preferenceChangeListener = OnNickPreferenceChangeListener(listener)
        nicknamePreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        nicknameChangedListeners[listener] = preferenceChangeListener
    }

    fun unregisterColorChangedListener(listener: UserColorChangedListener) {
        val preferenceChangeListener = colorChangedListeners.remove(listener) ?: return
        colorPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun unregisterNicknameChangedListener(listener: UserNicknameChangedListener) {
        val preferenceChangeListener = nicknameChangedListeners.remove(listener) ?: return
        nicknamePreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun getUserNicknameInternal(key: String): String? {
        val cached = nicknameCache.get(key)
        if (NICKNAME_NULL == cached) return null
        if (cached != null) return cached
        val nickname = nicknamePreferences.getString(key, null)?.takeIf(String::isNotEmpty)
        if (nickname != null) {
            nicknameCache.put(key, nickname)
        } else {
            nicknameCache.put(key, NICKNAME_NULL)
        }
        return nickname
    }

    interface UserColorChangedListener {
        fun onUserColorChanged(userKey: UserKey, color: Int)
    }

    interface UserNicknameChangedListener {
        fun onUserNicknameChanged(userKey: UserKey, nick: String?)
    }

    private class OnColorPreferenceChangeListener internal constructor(private val mListener: UserColorChangedListener?) : OnSharedPreferenceChangeListener {

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
            val userKey = UserKey.valueOf(key)
            mListener?.onUserColorChanged(userKey, preferences.getInt(key, 0))
        }

    }

    private class OnNickPreferenceChangeListener internal constructor(private val mListener: UserNicknameChangedListener?) : OnSharedPreferenceChangeListener {

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
            val userKey = UserKey.valueOf(key)
            mListener?.onUserNicknameChanged(userKey, preferences.getString(key, null))
        }

    }

    companion object : ApplicationContextSingletonHolder<UserColorNameManager>(creator@{
        val preferences = PreferencesSingleton.get(it)
        val manager = UserColorNameManager(it)
        PreferenceChangeNotifier.get(it).register(KEY_NAME_FIRST) changed@{
            manager.nameFirst = preferences[nameFirstKey]
        }
        manager.nameFirst = preferences[nameFirstKey]
        return@creator manager
    }) {

        private val NICKNAME_NULL = ".#NULL#"

        fun decideDisplayName(nickname: String?, name: String, screenName: String,
                nameFirst: Boolean) = nickname ?: if (nameFirst) name else "@$screenName"

        fun decideNickname(nick: String?, name: String) = nick ?: name
    }

}
