package com.example.linereminder.repository;

import com.example.linereminder.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /**
     * 指定ユーザーの未通知リマインダーを全件取得
     * → "リスト" コマンドで使用
     */
    List<Reminder> findByUserIdAndNotifiedFalseOrderByReminderTimeAsc(String userId);

    /**
     * 指定時間範囲内の未通知リマインダーを取得
     * → スケジューラが毎分チェックするときに使用
     */
    List<Reminder> findByReminderTimeBetweenAndNotifiedFalse(
            LocalDateTime from, LocalDateTime to);

    /**
     * 指定ユーザーの指定IDのリマインダーを取得
     * → "削除 {id}" コマンドで使用
     */
    java.util.Optional<Reminder> findByIdAndUserId(Long id, String userId);
}
