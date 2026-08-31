package com.anuj.notesai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Note entity — stores a user's note along with its AI-generated summary and key points.
 *
 * Key points are stored in a separate join table (note_key_points) via @ElementCollection,
 * which is the right JPA tool for persisting a collection of value types without creating
 * a full child entity.
 */
@Entity
@Table(name = "notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String originalText;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @ElementCollection
    @CollectionTable(
            name = "note_key_points",
            joinColumns = @JoinColumn(name = "note_id")
    )
    @Column(name = "key_point", columnDefinition = "TEXT")
    private List<String> keyPoints = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType sourceType;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
