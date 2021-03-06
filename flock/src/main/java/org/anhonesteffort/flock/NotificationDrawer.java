/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.anhonesteffort.flock.sync.account.AccountStore;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.addressbook.AddressbookSyncScheduler;
import org.anhonesteffort.flock.sync.calendar.CalendarsSyncScheduler;
import org.anhonesteffort.flock.sync.key.KeySyncScheduler;

/**
 * rhodey
 */
public class NotificationDrawer extends BroadcastReceiver {

  private static final String TAG = "org.anhonesteffort.flock.NotificationDrawer";

  private static final int ID_NOTIFICATION_AUTH         = 1020;
  private static final int ID_NOTIFICATION_SUBSCRIPTION = 1021;
  private static final int ID_NOTIFICATION_DEBUG_LOG    = 1022;

  private static final String PREFERENCES_NAME            = "AbstractDavSyncAdapter.PREFERENCES_NAME";
  private static final String KEY_VOID_AUTH_NOTIFICATIONS = "KEY_VOID_AUTH_NOTIFICATIONS";

  private static final String ACTION_STOP_ASKING_FOR_LOGS = "org.anhonesteffort.flock.ACTION_STOP_ASKING_FOR_LOGS";
  private static final String KEY_STOP_ASKING_FOR_LOGS    = "KEY_STOP_ASKING_FOR_LOGS";

  private static SharedPreferences getSharedPreferences(Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  public static void disableAuthNotificationsForRunningAdapters(Context context, Account account) {
    AddressbookSyncScheduler addressbookSync = new AddressbookSyncScheduler(context);
    CalendarsSyncScheduler   calendarSync    = new CalendarsSyncScheduler(context);
    KeySyncScheduler         keySync         = new KeySyncScheduler(context);
    SharedPreferences        settings        = getSharedPreferences(context);

    if (addressbookSync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + addressbookSync.getAuthority(), true).apply();
      Log.w(TAG, "disabled auth notifications for " + addressbookSync.getAuthority());
    }

    if (calendarSync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + calendarSync.getAuthority(), true).apply();
      Log.w(TAG, "disabled auth notifications for " + calendarSync.getAuthority());
    }

    if (keySync.syncInProgress(account)) {
      settings.edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + keySync.getAuthority(), true).apply();
      Log.w(TAG, "disabled auth notifications for " + keySync.getAuthority());
    }
  }

  public static void enableAuthNotifications(Context context, String authority) {
    getSharedPreferences(context).edit().putBoolean(KEY_VOID_AUTH_NOTIFICATIONS + authority, false).apply();
    Log.w(TAG, "enabled auth notification for " + authority);
  }

  public static boolean isAuthNotificationDisabled(Context context, String authority) {
    if (getSharedPreferences(context).getBoolean(KEY_VOID_AUTH_NOTIFICATIONS + authority, false)) {
      Log.w(TAG, "auth notification is disabled for " + authority);
      return true;
    }

    return false;
  }

  private static NotificationManager getNotificationManager(Context context) {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  private static PendingIntent getPendingActivityIntent(Context context, Intent intent) {
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public static void handleInvalidatePasswordAndShowAuthNotification(Context context) {
    Log.w(TAG, "handleInvalidatePasswordAndShowAuthNotification()");

    DavAccountHelper.invalidateAccountPassword(context);
    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
    Intent                     clickIntent         = new Intent(context, CorrectPasswordActivity.class);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_login_error));
    notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_correct_password));
    notificationBuilder.setSmallIcon(R.drawable.flock_actionbar_icon);
    notificationBuilder.setAutoCancel(true);

    notificationBuilder.setContentIntent(getPendingActivityIntent(context, clickIntent));
    getNotificationManager(context).notify(ID_NOTIFICATION_AUTH, notificationBuilder.build());
  }

  public static void cancelAuthNotification(Context context) {
    getNotificationManager(context).cancel(ID_NOTIFICATION_AUTH);
  }

  public static void showSubscriptionExpiredNotification(Context context) {
    Log.w(TAG, "showSubscriptionExpiredNotification()");

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
    Intent                     clickIntent         = new Intent(context, ManageSubscriptionActivity.class);

    notificationBuilder.setContentTitle(context.getString(R.string.notification_flock_subscription_expired));
    notificationBuilder.setSmallIcon(R.drawable.flock_actionbar_icon);
    notificationBuilder.setAutoCancel(true);

    Optional<Long> daysRemaining = AccountStore.getDaysRemaining(context);

    if (!daysRemaining.isPresent() || daysRemaining.get() > 0)
      notificationBuilder.setContentText(context.getString(R.string.notification_tap_to_update_subscription));
    else {
      Integer limitDaysExpired = context.getResources().getInteger(R.integer.limit_days_expired);
      Long    daysTillExpire   = limitDaysExpired - (-1 * daysRemaining.get());

      if (daysTillExpire < 0)
        daysTillExpire = 0L;

      notificationBuilder.setContentText(context.getString(
          R.string.account_will_be_deleted_in_days_tap_to_update_subscription,
          daysTillExpire
      ));
    }

    Optional<DavAccount> account = DavAccountHelper.getAccount(context);
    clickIntent.putExtra(ManageSubscriptionActivity.KEY_DAV_ACCOUNT_BUNDLE, account.get().toBundle());

    notificationBuilder.setContentIntent(getPendingActivityIntent(context, clickIntent));
    getNotificationManager(context).notify(ID_NOTIFICATION_SUBSCRIPTION, notificationBuilder.build());
  }

  private static boolean isStopAskingForLogsSet(Context context) {
    return getSharedPreferences(context).getBoolean(KEY_STOP_ASKING_FOR_LOGS, false);
  }

  private static void setStopAskingForLogs(Context context) {
    Log.w(TAG, "will stop asking for debug logs :(");
    getSharedPreferences(context).edit().putBoolean(KEY_STOP_ASKING_FOR_LOGS, true).apply();
  }

  public static void handlePromptForDebugLogIfNotDisabled(Context context) {
    if (isStopAskingForLogsSet(context)) {
      Log.w(TAG, "user doesn't care to send us logs, not going to ask.");
      return;
    }

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
    Intent                     sendLogIntent       = new Intent(context, SendDebugLogActivity.class);
    Intent                     stopAskingIntent    = new Intent(ACTION_STOP_ASKING_FOR_LOGS);

    String                          contentText = context.getString(R.string.something_strange_happened_send_us_your_debug_log);
    NotificationCompat.BigTextStyle textStyle   = new NotificationCompat.BigTextStyle().bigText(contentText);

    notificationBuilder.setSmallIcon(R.drawable.flock_actionbar_icon);
    notificationBuilder.setContentTitle(context.getString(R.string.flock_debug_log));
    notificationBuilder.setContentText(contentText);
    notificationBuilder.setStyle(textStyle);
    notificationBuilder.setAutoCancel(true);

    notificationBuilder.addAction(R.drawable.navigation_cancel,
                                  context.getString(R.string.dont_ask_again),
                                  PendingIntent.getBroadcast(context, 0, stopAskingIntent, PendingIntent.FLAG_UPDATE_CURRENT));

    notificationBuilder.addAction(R.drawable.social_send_now,
                                  context.getString(R.string.send_log),
                                  getPendingActivityIntent(context, sendLogIntent));

    getNotificationManager(context).notify(ID_NOTIFICATION_DEBUG_LOG, notificationBuilder.build());
  }

  public static void cancelDebugLogPrompt(Context context) {
    getNotificationManager(context).cancel(ID_NOTIFICATION_DEBUG_LOG);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(ACTION_STOP_ASKING_FOR_LOGS)) {
      setStopAskingForLogs(context);
      cancelDebugLogPrompt(context);
    }
  }
}
