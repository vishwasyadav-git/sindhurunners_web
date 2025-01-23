package com.sindhueventpay.Repository;

import com.sindhueventpay.models.Registration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Registration save(Registration registration);

    Registration findByEmail(String email);
}
