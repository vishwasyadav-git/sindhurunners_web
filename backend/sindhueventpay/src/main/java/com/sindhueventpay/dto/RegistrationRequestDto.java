package com.sindhueventpay.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@Data
public class RegistrationRequestDto {
    private String fullName;
    private LocalDate dob;
    private String gender;
    private String schoolName;
    private String tShirtSize;
    private String mobileNumber;
    private String emailId;
    private String address;
    private String city;
    private String district;
    private String state;
    private String pinCode;
    private String emergencyContactName;
    private String emergencyContactNumber;
    private MultipartFile aadhaarDocument;
    private boolean consentParent;
    private boolean consentInfo;
    private boolean consentFit;
    private boolean consentTerms;
    private boolean consentMedia;
}
