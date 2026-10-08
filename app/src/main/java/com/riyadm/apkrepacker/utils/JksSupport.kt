package com.riyadm.apkrepacker.utils

import com.riyadm.apkrepacker.utils.common.DLog
import java.security.KeyStore
import java.security.Provider
import java.security.Security

/**
 * Android's runtime has PKCS12 and BKS key stores but no JKS, the format `keytool` creates by
 * default and the first choice in this app's keystore dialogs (loading or generating one threw
 * "JKS not found"). The app already bundles a JKS implementation (`sun1-security.jar`); this
 * registers it so `KeyStore.getInstance("JKS")` works. Checked on a device: loads a JKS file,
 * reads its RSA key, stores and reloads it.
 */
object JksSupport {

    @JvmStatic
    fun install() {
        if (runCatching { KeyStore.getInstance("JKS") }.isSuccess) return
        try {
            Security.addProvider(object : Provider("ApkRepackerJKS", 1.0, "JKS key store support") {
                init {
                    put("KeyStore.JKS", "sun1.security.provider.JavaKeyStore\$JKS")
                    put("KeyStore.CaseExactJKS", "sun1.security.provider.JavaKeyStore\$CaseExactJKS")
                }
            })
        } catch (t: Throwable) {
            DLog.e("JksSupport", t)
        }
    }
}
