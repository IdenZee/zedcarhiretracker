package com.zedcarhire.zedcarhiretracker.model;


import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "raw_messages")
public class RawMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String rawHex;
}
