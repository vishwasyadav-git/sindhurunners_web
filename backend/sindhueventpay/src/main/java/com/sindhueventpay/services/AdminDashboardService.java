package com.sindhueventpay.services;

import com.sindhueventpay.Repository.EventCategoryRepository;
import com.sindhueventpay.Repository.RegistrationRepository;
import com.sindhueventpay.dto.AdminRegistrationResponse;
import com.sindhueventpay.dto.RegistrationStatsResponse;
import com.sindhueventpay.enums.RegistrationStatus;
import com.sindhueventpay.models.EventCategory;
import com.sindhueventpay.models.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Autowired
    private S3Service s3Service;

    public RegistrationStatsResponse getEventStats(Long eventId) {
        long totalRegistrations = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.PAID);
        BigDecimal totalRevenue = registrationRepository.sumCalculatedFeeByEventIdAndStatus(eventId, RegistrationStatus.PAID);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        // Category breakdown
        List<Object[]> catData = registrationRepository.countByEventIdAndStatusGroupByCategoryId(eventId, RegistrationStatus.PAID);
        Map<Long, String> categoryNames = categoryRepository.findByEventId(eventId)
                .stream().collect(Collectors.toMap(EventCategory::getId, EventCategory::getCategoryName));
        
        Map<String, Long> categoryBreakdown = new HashMap<>();
        for (Object[] row : catData) {
            Long catId = (Long) row[0];
            Long count = (Long) row[1];
            categoryBreakdown.put(categoryNames.getOrDefault(catId, "Unknown"), count);
        }

        // Gender breakdown
        List<Object[]> genderData = registrationRepository.countByEventIdAndStatusGroupByGender(eventId, RegistrationStatus.PAID);
        Map<String, Long> genderBreakdown = new HashMap<>();
        for (Object[] row : genderData) {
            String gender = (String) row[0];
            Long count = (Long) row[1];
            genderBreakdown.put(gender != null ? gender.trim().toUpperCase() : "UNKNOWN", count);
        }

        return RegistrationStatsResponse.builder()
                .totalRegistrations(totalRegistrations)
                .totalRevenue(totalRevenue)
                .categoryBreakdown(categoryBreakdown)
                .genderBreakdown(genderBreakdown)
                .build();
    }

    public Page<AdminRegistrationResponse> getRegistrations(Long eventId, String gender, Long categoryId, RegistrationStatus status, Pageable pageable) {
        Specification<Registration> spec = buildSpecification(eventId, gender, categoryId, status);
        Page<Registration> page = registrationRepository.findAll(spec, pageable);

        Map<Long, String> categoryNames = categoryRepository.findByEventId(eventId)
                .stream().collect(Collectors.toMap(EventCategory::getId, EventCategory::getCategoryName));

        return page.map(r -> mapToResponse(r, categoryNames));
    }

    public String generateCsvExport(Long eventId, String gender, Long categoryId, RegistrationStatus status) {
        Specification<Registration> spec = buildSpecification(eventId, gender, categoryId, status);
        List<Registration> registrations = registrationRepository.findAll(spec);

        Map<Long, String> categoryNames = categoryRepository.findByEventId(eventId)
                .stream().collect(Collectors.toMap(EventCategory::getId, EventCategory::getCategoryName));

        StringBuilder sb = new StringBuilder();
        // CSV Header
        sb.append("Registration Number,Full Name,Email,Mobile,Gender,Age,DOB,School/College,T-Shirt Size,Address,City,District,State,PIN Code,Emergency Contact Name,Emergency Contact Number,Category,Status,Fee,Presigned ID URL,Registered At\n");

        for (Registration r : registrations) {
            String presignedUrl = "";
            if (r.getDocumentS3Key() != null && !r.getDocumentS3Key().isBlank()) {
                presignedUrl = s3Service.generatePresignedUrl(r.getDocumentS3Key(), Duration.ofHours(24));
            }

            sb.append(escapeCsv(r.getRegistrationNumber())).append(",")
              .append(escapeCsv(r.getFullName())).append(",")
              .append(escapeCsv(r.getEmailId())).append(",")
              .append(escapeCsv(r.getMobileNumber())).append(",")
              .append(escapeCsv(r.getGender())).append(",")
              .append(r.getCalculatedAge()).append(",")
              .append(r.getDob() != null ? r.getDob().toString() : "").append(",")
              .append(escapeCsv(r.getSchoolName())).append(",")
              .append(escapeCsv(r.getTShirtSize())).append(",")
              .append(escapeCsv(r.getAddress())).append(",")
              .append(escapeCsv(r.getCity())).append(",")
              .append(escapeCsv(r.getDistrict())).append(",")
              .append(escapeCsv(r.getState())).append(",")
              .append(escapeCsv(r.getPinCode())).append(",")
              .append(escapeCsv(r.getEmergencyContactName())).append(",")
              .append(escapeCsv(r.getEmergencyContactNumber())).append(",")
              .append(escapeCsv(categoryNames.getOrDefault(r.getCategoryId(), "Unknown"))).append(",")
              .append(r.getStatus()).append(",")
              .append(r.getCalculatedFee()).append(",")
              .append(escapeCsv(presignedUrl)).append(",")
              .append(r.getCreatedAt())
              .append("\n");
        }

        return sb.toString();
    }

    private Specification<Registration> buildSpecification(Long eventId, String gender, Long categoryId, RegistrationStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("eventId"), eventId));
            
            if (gender != null && !gender.isBlank()) {
                predicates.add(cb.equal(root.get("gender"), gender));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminRegistrationResponse mapToResponse(Registration r, Map<Long, String> categoryNames) {
        String presignedUrl = "";
        if (r.getDocumentS3Key() != null && !r.getDocumentS3Key().isBlank()) {
            // Short duration for UI views (1 hour)
            presignedUrl = s3Service.generatePresignedUrl(r.getDocumentS3Key(), Duration.ofHours(1));
        }

        return AdminRegistrationResponse.builder()
                .id(r.getId())
                .registrationNumber(r.getRegistrationNumber())
                .fullName(r.getFullName())
                .emailId(r.getEmailId())
                .mobileNumber(r.getMobileNumber())
                .gender(r.getGender())
                .calculatedAge(r.getCalculatedAge())
                .categoryName(categoryNames.getOrDefault(r.getCategoryId(), "Unknown"))
                .calculatedFee(r.getCalculatedFee())
                .status(r.getStatus())
                .documentUrl(presignedUrl)
                .createdAt(r.getCreatedAt())
                .build();
    }

    private String escapeCsv(String data) {
        if (data == null) {
            return "";
        }
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }
}
