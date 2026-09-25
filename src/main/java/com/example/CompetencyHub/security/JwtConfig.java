package com.example.CompetencyHub.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * The signing key, and the two objects that use it: a JwtEncoder to issue tokens at login,
 * and a JwtDecoder that the resource server calls on every request.
 *
 * <p><b>Why RSA (asymmetric) and not a shared secret (HMAC)?</b> With HMAC, the same secret
 * both signs and verifies -- every service that checks tokens could also forge them. With
 * RSA, only the holder of the PRIVATE key can sign; anyone with the PUBLIC key can verify.
 * When the notification service or an API gateway needs to check tokens later, it gets the
 * public key and cannot mint an admin token with it.
 *
 * <p><b>Where the key comes from.</b> If competencyhub.security.jwt.public-key/private-key
 * point at PEM files (production: mounted secrets), those are used. If not, a fresh key pair
 * is generated in memory at startup. That is fine for dev and tests and deliberately
 * inconvenient anywhere else: every restart invalidates every token, and two instances would
 * reject each other's tokens. Committing a private key to the repo is the alternative, and
 * a key in git is a key everyone with read access can sign admin tokens with.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RSAKey jwtSigningKey(JwtProperties properties) {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;

        // Exactly one key configured is a deployment mistake -- fail at startup rather than
        // quietly falling back to a throwaway key in production.
        if ((properties.publicKey() == null) != (properties.privateKey() == null)) {
            throw new IllegalStateException(
                    "Configure both competencyhub.security.jwt.public-key and private-key, or neither");
        }

        if (properties.publicKey() != null) {
            publicKey = read(properties.publicKey(), true);
            privateKey = read(properties.privateKey(), false);
            log.info("JWT signing key loaded from {}", properties.publicKey().getDescription());
        } else {
            KeyPair pair = generateRsaKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            log.warn("No JWT key configured: generated a temporary key pair. Tokens will not "
                    + "survive a restart. Set competencyhub.security.jwt.public-key and private-key "
                    + "outside development.");
        }

        // The key id ("kid") goes into every token header. When keys are rotated, the kid tells
        // a verifier which of several published public keys to use.
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("competencyhub-" + Integer.toHexString(publicKey.getModulus().hashCode()))
                .build();
    }

    /** Signs tokens. Only TokenService uses it. */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * Verifies tokens. Having a JwtDecoder bean is what switches the resource server on:
     * SecurityConfig's oauth2ResourceServer().jwt() finds this bean and calls it for every
     * request carrying "Authorization: Bearer ...".
     *
     * <p>Checks, in order: the signature (was it signed by our private key?), then the
     * validators -- expiry ("exp", with a 60-second clock-skew allowance), not-before, and the
     * issuer. Any failure is a 401 before the request reaches a controller.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static <T> T read(org.springframework.core.io.Resource resource, boolean isPublic) {
        try (InputStream in = resource.getInputStream()) {
            @SuppressWarnings("unchecked")
            T key = (T) (isPublic
                    ? RsaKeyConverters.x509().convert(in)
                    : RsaKeyConverters.pkcs8().convert(in));
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read JWT key " + resource.getDescription(), e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);   // the minimum anyone should use for RS256 today
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available in this JVM", e);
        }
    }
}
