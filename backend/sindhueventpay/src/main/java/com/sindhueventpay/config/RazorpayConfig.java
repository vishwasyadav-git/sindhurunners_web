package com.sindhueventpay.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Razorpay Java SDK client.
 *
 * <p>The {@link RazorpayClient} is a singleton bean that holds an authenticated
 * HTTP client. Credentials are injected from {@link AppProperties} — never
 * hardcoded.
 */
@Configuration
public class RazorpayConfig {

    @Autowired
    private AppProperties appProperties;

    @Bean
    public RazorpayClient razorpayClient() {
        AppProperties.Razorpay rp = appProperties.getRazorpay();
        try {
            return new RazorpayClient(rp.getKeyId(), rp.getKeySecret());
        } catch (RazorpayException e) {
            throw new IllegalStateException(
                    "Failed to initialise Razorpay client. " +
                    "Check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET environment variables.", e);
        }
    }
}
