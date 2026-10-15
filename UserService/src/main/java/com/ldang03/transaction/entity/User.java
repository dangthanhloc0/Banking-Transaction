package com.ldang03.transaction.entity;

import com.ldang03.common.customGenerateUUID.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedUuidV7
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String fullName;

    @Column(length = 255)
    private String avatarUrl;

    @CreationTimestamp
    private LocalDateTime userCreatedAt;

    @UpdateTimestamp
    private LocalDateTime userUpdatedAt;

    private LocalDateTime userDeletedAt;
}
