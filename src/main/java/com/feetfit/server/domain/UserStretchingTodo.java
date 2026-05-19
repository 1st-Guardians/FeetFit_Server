package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.HealthType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_stretching_todo")
@Getter
@DynamicUpdate
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserStretchingTodo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthType healthType;

    @Column
    private String youtubeUrl;

    @Column(nullable = false)
    private LocalDateTime todoDate;

    @OneToMany(mappedBy = "stretchingTodo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserStretchingTodoAssignment> assignments = new ArrayList<>();
}
