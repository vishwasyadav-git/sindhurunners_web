package com.sindhueventpay.models;

import com.sindhueventpay.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    private String email;
    
    private String phone;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
}
