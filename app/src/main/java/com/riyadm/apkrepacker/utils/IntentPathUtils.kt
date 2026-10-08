/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.riyadm.apkrepacker.utils

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import android.provider.DocumentsContract
import android.text.TextUtils
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path


/*import java8.nio.file.Path;
import java8.nio.file.Paths;*/

@Suppress("UNUSED_VARIABLE", "ControlFlowWithEmptyBody")
object IntentPathUtils {

    private val KEY_PREFIX = IntentPathUtils::class.java.name + '.'

    private val EXTRA_PATH_URI = KEY_PREFIX + "PATH_URI"

    private val EXTRA_PATH_URI_LIST = KEY_PREFIX + "PATH_URI_LIST"

    @JvmStatic
    fun putExtraPath(intent: Intent, path: String): Intent {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        // We cannot put URI into intent here either, because ShortcutInfo uses PersistableBundle
        // which doesn't support Serializable.
        return intent.putExtra(EXTRA_PATH_URI, path)
    }

    @JvmStatic
    fun getExtraPath(intent: Intent, allowDataContentUri: Boolean): Path? {

        val extraPathUriString = intent.getStringExtra(EXTRA_PATH_URI)
        if (extraPathUriString != null) {
            var extraPathUri: URI? = null
            try {
                extraPathUri = URI(extraPathUriString)
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }
            if (extraPathUri != null) {
//                return Paths.get(extraPathUri);
            }
        }

        val data = intent.data
        if (data != null) {
            val dataScheme = data.scheme
            if (dataScheme == "file") {
                val dataPath = data.path
                if (!TextUtils.isEmpty(dataPath)) {
//                    return Paths.get(dataPath);
                }
            } else if (allowDataContentUri && dataScheme == ContentResolver.SCHEME_CONTENT) {
                var dataUri: URI? = null
                try {
                    dataUri = URI(data.toString())
                } catch (e: URISyntaxException) {
                    e.printStackTrace()
                    // Some people use Uri.parse() without encoding their path. Let's try save them
                    // by calling the other URI constructor that encodes everything.
                    try {
                        dataUri = URI(data.scheme, data.userInfo, data.host,
                                data.port, data.path, data.query,
                                data.fragment)
                    } catch (e2: URISyntaxException) {
                        e2.printStackTrace()
                    }
                }
                if (dataUri != null) {
//                    return Paths.get(dataUri);
                }
            }
        }

        val extraInitialUri: Uri? = IntentCompat.getParcelableExtra(intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java)
        // TODO: Support DocumentsProvider Uri?
        if (extraInitialUri != null && extraInitialUri.scheme == "file") {
            val path = extraInitialUri.path
            if (!TextUtils.isEmpty(path)) {
//                return Paths.get(path);
            }
        }

        val extraAbsolutePath = intent.getStringExtra("org.openintents.extra.ABSOLUTE_PATH")
        if (extraAbsolutePath != null) {
//            return Paths.get(extraAbsolutePath);
        }

        return null
    }

/*    @Nullable
    public static String getExtraPath(@NonNull Intent intent) {
        return getExtraPath(intent, false);
    }

    @NonNull
    public static Intent putExtraPathList(@NonNull Intent intent, @NonNull Iterable<String> paths) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        ArrayList<URI> pathUris = Functional.map(paths, Path::toUri);
        return intent.putExtra(EXTRA_PATH_URI_LIST, pathUris);
    }

    @NonNull
    public static List<String> getExtraPathList(@NonNull Intent intent, boolean allowDataContentUri) {
        //noinspection unchecked
        List<URI> extraPathUris = (List<URI>) intent.getSerializableExtra(EXTRA_PATH_URI_LIST);
        if (extraPathUris != null) {
            return Functional.map(extraPathUris, (Function<URI, Path>) String::getBytes);
        }

        Path extraPath = getExtraPath(intent, allowDataContentUri);
        return CollectionUtils.singletonListOrEmpty(extraPath);
    }

    @NonNull
    public static List<Path> getExtraPathList(@NonNull Intent intent) {
        return getExtraPathList(intent, false);
    }*/
}
