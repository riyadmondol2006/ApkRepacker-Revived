package com.riyadm.apkrepacker.ui.keydialog

import android.util.Base64
import sun1.security.x509.AlgorithmId
import sun1.security.x509.CertificateAlgorithmId
import sun1.security.x509.CertificateExtensions
import sun1.security.x509.CertificateIssuerName
import sun1.security.x509.CertificateSerialNumber
import sun1.security.x509.CertificateSubjectName
import sun1.security.x509.CertificateValidity
import sun1.security.x509.CertificateVersion
import sun1.security.x509.CertificateX509Key
import sun1.security.x509.KeyIdentifier
import sun1.security.x509.PrivateKeyUsageExtension
import sun1.security.x509.SubjectKeyIdentifierExtension
import sun1.security.x509.X500Name
import sun1.security.x509.X509CertImpl
import sun1.security.x509.X509CertInfo
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit

/** Parameters of a new signing key. [type] indexes R.array.key_format: JKS, PKCS12, BKS, pk8 + x509.pem. */
data class KeyParams(
    val type: Int,
    /** Keystore file, or the pk8 private key file when [type] is [KeyGenerator.TYPE_PK8]. */
    val keyPath: String,
    /** Key alias inside the keystore, or the x509.pem certificate file for pk8 keys. */
    val certOrAlias: String,
    val storePass: String,
    val keyPass: String,
    val keySize: Int,
    val years: Long,
    val commonName: String,
    val organizationUnit: String,
    val organizationName: String,
    val localityName: String,
    val stateName: String,
    val country: String,
)

/** Self-signed RSA key + certificate generation, written as a keystore or a pk8 / x509.pem pair. */
object KeyGenerator {
    const val TYPE_JKS = 0
    const val TYPE_PKCS12 = 1
    const val TYPE_BKS = 2
    const val TYPE_PK8 = 3

    private const val SIGNATURE_ALGORITHM = "SHA512withRSA"

    /** Slow (RSA key pair generation): call off the main thread. */
    @Throws(Exception::class)
    fun generate(params: KeyParams) {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(params.keySize, SecureRandom.getInstance("SHA1PRNG"))
        val pair = generator.generateKeyPair()

        val start = Date()
        val end = Date(start.time + TimeUnit.DAYS.toMillis(params.years * 365))
        val subject = X500Name(
            params.commonName, params.organizationUnit, params.organizationName,
            params.localityName, params.stateName, params.country,
        )
        val extensions = CertificateExtensions().apply {
            set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
            set("PrivateKeyUsage", PrivateKeyUsageExtension(start, end))
        }
        val certificate = selfSignedCertificate(pair.private, pair.public, subject, start, end, extensions)
        write(pair.private, certificate, params)
    }

    private fun selfSignedCertificate(
        privateKey: PrivateKey,
        publicKey: PublicKey,
        name: X500Name,
        from: Date,
        to: Date,
        extensions: CertificateExtensions?,
    ): X509Certificate {
        try {
            val info = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(SIGNATURE_ALGORITHM)))
                set("subject", CertificateSubjectName(name))
                set("key", CertificateX509Key(publicKey))
                set("validity", CertificateValidity(from, to))
                set("issuer", CertificateIssuerName(name))
                if (extensions != null) set("extensions", extensions)
            }
            return X509CertImpl(info).also { it.sign(privateKey, SIGNATURE_ALGORITHM) }
        } catch (e: IOException) {
            throw CertificateEncodingException("getSelfCert: " + e.message)
        }
    }

    private fun write(privateKey: PrivateKey, certificate: X509Certificate, params: KeyParams) {
        val keyFile = File(params.keyPath)
        keyFile.parentFile?.mkdirs()

        if (params.type == TYPE_PK8) {
            keyFile.writeBytes(privateKey.encoded)
            val pem = "-----BEGIN CERTIFICATE-----\n" +
                Base64.encodeToString(certificate.encoded, Base64.DEFAULT) +
                "-----END CERTIFICATE-----\n"
            File(params.certOrAlias).writeText(pem)
            return
        }

        val keyStore = KeyStore.getInstance(
            when (params.type) {
                TYPE_JKS -> "JKS"
                TYPE_PKCS12 -> "PKCS12"
                else -> "BKS"
            },
        )
        val storePass = params.storePass.toCharArray()
        if (keyFile.exists()) {
            keyFile.inputStream().use { keyStore.load(it, storePass) }
        } else {
            keyStore.load(null)
        }
        keyStore.setKeyEntry(params.certOrAlias, privateKey, params.keyPass.toCharArray(), arrayOf(certificate))
        keyFile.outputStream().use { keyStore.store(it, storePass) }
    }
}
