package com.artemkopan.baseproject.utils;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;

import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class IntentHelper {

    public static void intentSharingText(Context context, String text, String title) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooserIntent = Intent.createChooser(shareIntent, title);
        chooserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooserIntent);
    }

    public static void intentSendSMS(Context context, String smsBody) throws ActivityNotFoundException {
        intentSendSMS(context, "", smsBody);
    }

    public static void intentSendSMS(Context context, String smsBody, String... phoneNumbers) throws ActivityNotFoundException {
        intentSendSMS(context, TextUtils.join("; ", phoneNumbers), smsBody);
    }

    public static void intentSendSMS(Context context, String phoneNumbers, String smsBody) throws ActivityNotFoundException {
        Intent sendIntent = new Intent(android.content.Intent.ACTION_VIEW);
        sendIntent.putExtra("address", phoneNumbers);
        sendIntent.putExtra("sms_body", smsBody);
        sendIntent.setType("vnd.android-dir/mms-sms");
        sendIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);

        if (isAvailable(context, sendIntent)) {
            context.startActivity(sendIntent);
            return;
        }

        sendIntent = new Intent(Intent.ACTION_SENDTO,
                               TextUtils.isEmpty(phoneNumbers) ? null : Uri.parse("sms:" + phoneNumbers));

        sendIntent.putExtra("sms_body", smsBody);
        sendIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(sendIntent);

    }

    @SuppressWarnings("MissingPermission")
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    public static void intentCall(Context context, String phoneNumber) throws ActivityNotFoundException {
        if (TextUtils.isEmpty(phoneNumber)) {
            Log.e("intentCall: Phone number is null or empty");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void sendEmail(Context context, String subject, String body, String... emails) throws ActivityNotFoundException {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.putExtra(Intent.EXTRA_EMAIL, emails);
        if (!StringUtils.isEmpty(subject)) intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (!StringUtils.isEmpty(body)) intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.setData(Uri.parse("mailto:"));
        context.startActivity(intent);
    }

    public static boolean isAvailable(Context ctx, Intent intent) {
        final PackageManager mgr = ctx.getPackageManager();
        List<ResolveInfo> list = mgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }
}