package com.example.linereminder.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * リマインダーエンティティ
 * ユーザーが登録した各リマインドを1レコードとして管理する
 */
@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LINEのユーザーID (Push通知の宛先) */
    @Column(nullable = false)
    private String userId;

    /** リマインドする日時 */
    @Column(nullable = false)
    private LocalDateTime reminderTime;

    /** リマインドメッセージ本文 */
    @Column(nullable = false)
    private String message;

    /**
     * 通知済みフラグ
     * true の場合はスケジューラがスキップする
     */
    @Column(nullable = false)
    private boolean notified = false;

    /** 登録日時 */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Reminder(String userId, LocalDateTime reminderTime, String message) {
        this.userId = userId;
        this.reminderTime = reminderTime;
        this.message = message;
        this.notified = false;
        this.createdAt = LocalDateTime.now();
    }
}
