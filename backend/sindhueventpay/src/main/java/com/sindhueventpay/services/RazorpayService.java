package com.sindhueventpay.services;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.exceptions.PaymentVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Service wrapping all Razorpay SDK operations.
 *
 * <h3>Security notes:</h3>
 * <ul>
 *   <li>The Razorpay Key Secret is used only here and in config — never elsewhere.</li>
 *   <li>Signature verification uses HMAC-SHA256 with a time-constant comparison
 *       (MessageDigest.isEqual) to prevent timing attacks.</li>
 *   <li>Credentials are never logged.</li>
 * </ul>
 */
@Service
@Slf4j
public class RazorpayService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private AppProperties appProperties;

    // ─────────────────────────────────────────────────────────────────────────
    // Order creation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a Razorpay order for the given amount.
     *
     * @param receipt       unique receipt ID (use registrationId/UUID, max 40 chars)
     * @param amountInPaise amount in paise (INR × 100)
     * @param currency      ISO currency code, e.g. {@code INR}
     * @return the created {@link Order} object
     * @throws RuntimeException if the Razorpay API call fails
     */
    public Order createOrder(String receipt, long amountInPaise, String currency) {
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", currency);
        // receipt must be ≤ 40 characters for Razorpay
        orderRequest.put("receipt", receipt.length() > 40 ? receipt.substring(0, 40) : receipt);

        try {
            Order order = razorpayClient.orders.create(orderRequest);
            log.info("Razorpay order created. orderId=[{}] amount=[{}] currency=[{}]",
                    order.get("id"), amountInPaise, currency);
            return order;
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for receipt=[{}]: {}", receipt, e.getMessage());
            throw new RuntimeException("Failed to create payment order. Please try again.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signature verification (frontend callback)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the Razorpay payment signature from the frontend callback.
     *
     * <p>Razorpay computes:<br>
     * {@code signature = HMAC-SHA256(orderId + "|" + paymentId, keySecret)}
     *
     * <p>We recompute it server-side and compare. If it does not match, the
     * callback is rejected as forged/tampered.
     *
     * @throws PaymentVerificationException if verification fails
     */
    public void verifyPaymentSignature(String orderId, String paymentId, String signature) {
        String payload = orderId + "|" + paymentId;
        String computed = computeHmac(payload, appProperties.getRazorpay().getKeySecret());

        if (!timingSafeEquals(computed, signature)) {
            log.warn("Payment signature mismatch. orderId=[{}] paymentId=[{}]", orderId, paymentId);
            throw new PaymentVerificationException("Razorpay payment signature verification failed.");
        }
        log.debug("Payment signature verified. orderId=[{}] paymentId=[{}]", orderId, paymentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook signature verification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the Razorpay webhook HMAC signature.
     *
     * <p>Razorpay computes:<br>
     * {@code X-Razorpay-Signature = HMAC-SHA256(rawBody, webhookSecret)}
     *
     * <p>The raw request body bytes must be passed here — never the parsed JSON —
     * because any serialisation difference would invalidate the HMAC.
     *
     * @param rawBody   raw HTTP request body bytes
     * @param signature value of the {@code X-Razorpay-Signature} header
     * @throws PaymentVerificationException if verification fails
     */
    public void verifyWebhookSignature(byte[] rawBody, String signature) {
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        String computed = computeHmac(payload, appProperties.getRazorpay().getWebhookSecret());

        if (!timingSafeEquals(computed, signature)) {
            log.warn("Webhook signature mismatch — possible forged webhook.");
            throw new PaymentVerificationException("Razorpay webhook signature verification failed.");
        }
        log.debug("Webhook signature verified.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Amount helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts INR (rupees as BigDecimal) to paise (Razorpay's integer unit).
     */
    public static long toPaise(BigDecimal amountInRupees) {
        return amountInRupees.multiply(BigDecimal.valueOf(100)).longValue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Timing-safe string comparison using {@link java.security.MessageDigest#isEqual}.
     * Prevents timing-based side-channel attacks on HMAC comparison.
     */
    private boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }
}
