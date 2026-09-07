package com.sindhueventpay.services;

import com.sindhueventpay.config.AppProperties;
import com.sindhueventpay.exceptions.PaymentVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RazorpayService}.
 *
 * <p>Tests HMAC computation and signature verification logic without
 * making actual Razorpay API calls.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RazorpayService")
class RazorpayServiceTest {

    @Mock AppProperties appProperties;
    @Mock AppProperties.Razorpay razorpayProps;

    @InjectMocks RazorpayService razorpayService;

    private static final String TEST_KEY_SECRET = "test_key_secret_12345";
    private static final String TEST_WEBHOOK_SECRET = "test_webhook_secret_67890";

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.getRazorpay()).thenReturn(razorpayProps);
        lenient().when(razorpayProps.getKeySecret()).thenReturn(TEST_KEY_SECRET);
        lenient().when(razorpayProps.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment signature verification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should pass verification with a valid payment signature")
    void shouldPassWithValidPaymentSignature() throws Exception {
        String orderId    = "order_abc123";
        String paymentId  = "pay_xyz789";
        String payload    = orderId + "|" + paymentId;
        String validSig   = computeHmac(payload, TEST_KEY_SECRET);

        assertThatNoException().isThrownBy(() ->
                razorpayService.verifyPaymentSignature(orderId, paymentId, validSig));
    }

    @Test
    @DisplayName("should throw PaymentVerificationException with an invalid payment signature")
    void shouldFailWithInvalidPaymentSignature() {
        assertThatThrownBy(() ->
                razorpayService.verifyPaymentSignature(
                        "order_abc123", "pay_xyz789", "invalid_signature_here"))
                .isInstanceOf(PaymentVerificationException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("should fail when order ID or payment ID is tampered")
    void shouldFailWhenPayloadTampered() throws Exception {
        // Compute signature for the original payload
        String originalSig = computeHmac("order_real|pay_real", TEST_KEY_SECRET);

        // Use a different order ID — signature should not match
        assertThatThrownBy(() ->
                razorpayService.verifyPaymentSignature("order_tampered", "pay_real", originalSig))
                .isInstanceOf(PaymentVerificationException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook signature verification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should pass webhook verification with a valid signature")
    void shouldPassWithValidWebhookSignature() throws Exception {
        byte[] rawBody = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String validSig = computeHmac(new String(rawBody, StandardCharsets.UTF_8), TEST_WEBHOOK_SECRET);

        assertThatNoException().isThrownBy(() ->
                razorpayService.verifyWebhookSignature(rawBody, validSig));
    }

    @Test
    @DisplayName("should throw PaymentVerificationException with an invalid webhook signature")
    void shouldFailWithInvalidWebhookSignature() {
        byte[] rawBody = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() ->
                razorpayService.verifyWebhookSignature(rawBody, "bad_webhook_signature"))
                .isInstanceOf(PaymentVerificationException.class);
    }

    @Test
    @DisplayName("should fail webhook verification when body is modified")
    void shouldFailWhenWebhookBodyModified() throws Exception {
        byte[] original  = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered  = "{\"event\":\"payment.captured\",\"extra\":\"field\"}".getBytes(StandardCharsets.UTF_8);
        String validSig  = computeHmac(new String(original, StandardCharsets.UTF_8), TEST_WEBHOOK_SECRET);

        // Use tampered body with original signature — should fail
        assertThatThrownBy(() ->
                razorpayService.verifyWebhookSignature(tampered, validSig))
                .isInstanceOf(PaymentVerificationException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Amount conversion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should convert INR 500 to 50000 paise")
    void shouldConvertRupeesToPaise() {
        assertThat(RazorpayService.toPaise(new BigDecimal("500.00"))).isEqualTo(50_000L);
        assertThat(RazorpayService.toPaise(new BigDecimal("1.50"))).isEqualTo(150L);
        assertThat(RazorpayService.toPaise(new BigDecimal("1000"))).isEqualTo(100_000L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
