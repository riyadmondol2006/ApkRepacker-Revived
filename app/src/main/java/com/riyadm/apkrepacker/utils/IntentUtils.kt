package com.riyadm.apkrepacker.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import com.riyadm.apkrepacker.App
import java.io.File

object IntentUtils {

    private const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"

    private const val MIME_TYPE_TEXT_PLAIN = "text/plain"
    private const val MIME_TYPE_IMAGE_ANY = "image/*"
    private const val MIME_TYPE_ANY = "*/*"


    @JvmStatic
    fun withChooser(intent: Intent?): Intent {
        return Intent.createChooser(intent, null)
    }

    /**
     * "Open with" chooser for [file]. Uses a content:// Uri from the app's provider with a read
     * grant: file:// Uris crash since API 24 (the old reflection workaround for that is a
     * blocked non-SDK API), and other apps can't read shared storage paths under scoped
     * storage. The chooser also avoids ActivityNotFoundException when nothing handles the type.
     */
    @JvmStatic
    fun openFileWithIntent(file: File): Intent {
        val extension = file.name.substring(file.name.lastIndexOf(".") + 1)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: MIME_TYPE_ANY

        val uri = FileProvider.getUriForFile(App.get(), file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, type)
        intent.clipData = ClipData.newRawUri(file.name, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, null)
    }

    @JvmStatic
    fun makeCaptureImage(outputUri: Uri): Intent {
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
    }

/*    @NonNull
    public static Intent makeInstallPackage(@NonNull Uri uri) {
        return new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, MimeTypes.APK_MIME_TYPE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }*/

    @Suppress("DEPRECATION")
    @JvmStatic
    fun makeInstallShortcut(iconRes: Int, nameRes: Int, intentClass: Class<*>, context: Context): Intent {
        return Intent()
                .setAction(ACTION_INSTALL_SHORTCUT)
                .putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(context.applicationContext,
                        intentClass))
                .putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(nameRes))
                .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(context, iconRes))
    }

    @JvmStatic
    fun makeLaunchApp(packageName: String, context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun makeMediaScan(uri: Uri): Intent {
        return Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                .setData(uri)
    }

    @JvmStatic
    fun makeMediaScan(file: File): Intent {
        return makeMediaScan(Uri.fromFile(file))
    }

    @JvmStatic
    fun makePickFile(allowMultiple: Boolean): Intent {
        return makePickFile(MIME_TYPE_ANY, null, allowMultiple)
    }

    @JvmStatic
    fun makePickFile(mimeType: String, allowMultiple: Boolean): Intent {
        return makePickFile(mimeType, arrayOf(mimeType), allowMultiple)
    }

    @JvmStatic
    fun makePickFile(mimeTypes: Array<String>?, allowMultiple: Boolean): Intent {
        val mimeType = if (mimeTypes != null && mimeTypes.size == 1) mimeTypes[0] else MIME_TYPE_ANY
        return makePickFile(mimeType, mimeTypes, allowMultiple)
    }

    private fun makePickFile(mimeType: String, mimeTypes: Array<String>?,
                             allowMultiple: Boolean): Intent {
        // If not using ACTION_OPEN_DOCUMENT, URI permission can be lost after ~10 seconds.
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            Intent.ACTION_OPEN_DOCUMENT
        else
            Intent.ACTION_GET_CONTENT
        val intent = Intent(action)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (mimeTypes != null && mimeTypes.isNotEmpty()) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (allowMultiple) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        return intent
    }

    @JvmStatic
    fun makePickImage(allowMultiple: Boolean): Intent {
        return makePickFile(MIME_TYPE_IMAGE_ANY, allowMultiple)
    }

    @JvmStatic
    fun makePickOrCaptureImageWithChooser(allowPickMultiple: Boolean,
                                          captureOutputUri: Uri): Intent {
        return withChooser(makePickImage(allowPickMultiple))
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(
                        makeCaptureImage(captureOutputUri)
                ))
    }

    // TODO: Use android.support.v4.app.ShareCompat ?

    @JvmStatic
    fun makeSendText(text: CharSequence, htmlText: String?): Intent {
        val intent = Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
        if (htmlText != null) {
            intent.putExtra(IntentCompat.EXTRA_HTML_TEXT, htmlText)
        }
        return intent.setType(MIME_TYPE_TEXT_PLAIN)
    }

    @JvmStatic
    fun makeSendText(text: CharSequence): Intent {
        return makeSendText(text, null)
    }

/*    @NonNull
    public static Intent makeSendImage(@NonNull Uri uri, @Nullable CharSequence text) {
        Intent intent = makeSendStream(uri, MIME_TYPE_IMAGE_ANY);
        if (text != null) {
            intent
                    // For maximum compatibility.
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_TITLE, text)
                    .putExtra(Intent.EXTRA_SUBJECT, text)
                    // HACK: WeChat moments respects this extra only.
                    .putExtra("Kdescription", text);
        }
        return intent;
    }*/

/*    @NonNull
    public static Intent makeSendImage(@NonNull Uri uri) {
        return makeSendImage(uri, null);
    }*/

/*    @NonNull
    public static Intent makeSendStream(@NonNull Uri stream, @NonNull String type) {
        return new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, stream)
                .setType(MimeTypes.getIntentType(type));
    }*/

/*    @NonNull
    public static Intent makeSendStream(@NonNull List<Uri> streams, @NonNull List<String> types) {
        if (streams.size() == 1) {
            return makeSendStream(streams.get(0), types.get(0));
        }
        return new Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(streams))
                .setType(MimeTypes.getIntentType(types));
    }*/

/*    @NonNull
    public static Intent makeSyncSettings(@Nullable String[] authorities,
                                          @Nullable String[] accountTypes) {
        Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
        if (!ArrayUtils.isEmpty(authorities)) {
            intent.putExtra(Settings.EXTRA_AUTHORITIES, authorities);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (!ArrayUtils.isEmpty(accountTypes)) {
                intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, accountTypes);
            }
        }
        return intent;
    }*/

/*    @NonNull
    public static Intent makeSyncSettingsWithAuthority(@Nullable String authority) {
        return makeSyncSettings(authority != null ? new String[] { authority } : null, null);
    }*/

/*    @NonNull
    public static Intent makeSyncSettingsWithAccountType(@Nullable String accountType) {
        return makeSyncSettings(null, accountType != null ? new String[] { accountType } : null);
    }*/

/*
    @NonNull
    public static Intent makeSyncSettings() {
        return makeSyncSettings(null, null);
    }
*/

    @JvmStatic
    fun makeView(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri)
    }

    @JvmStatic
    fun makeView(uri: Uri, type: String): Intent {
        return Intent(Intent.ACTION_VIEW)
                // Calling setType() will clear data.
                .setDataAndType(uri, "")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @JvmStatic
    fun makeViewAppInMarket(packageName: String): Intent {
        return makeView(Uri.parse("market://details?id=$packageName"))
    }
}
