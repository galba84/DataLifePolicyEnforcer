package com.example.datalifepolicyenforcer.connection;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "database_connection")
@Getter @Setter
public class DatabaseConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String host;
    private int port;
    @Column(name = "database_name")
    private String database;
    @JsonIgnore
    private String username;
    @JsonIgnore
    private String password;
    private String sslMode;
    private String status = "UNKNOWN";
    private Instant lastTestedAt;
}

