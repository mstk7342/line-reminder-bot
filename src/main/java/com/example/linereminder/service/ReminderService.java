package com.example.linereminder.service;

import com.example.linereminder.entity.Reminder;
import com.example.linereminder.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * リマインダーのビジネスロジックを担当するサービス
 *
 * 対応コマンド:
 *   リマインド YYYY-MM-DD HH:mm メッセージ  → リマインダー登録
 *   リスト                                 → 登録中リマインダー一覧
 *   削除 {id}                             → リマインダー削除
 *   ヘルプ                                → 使い方表示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;

    // パターン: "リマインド 2026-03-21 14:00 会議" または "リマインド 明日 14:00 会議"
    private static final Pattern REMIND_PATTERN_DATE =
            Pattern.compile("^リマインド\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$");

    // パターン: "リマインド 明日 14:00 会議"
    private static final Pattern REMIND_PATTERN_TOMORROW =
            Pattern.compile("^リマインド\\s+明日\\s+(\\d{2}:\\d{2})\\s+(.+)$");

    // パターン: "リマインド 今日 14:00 会議"
    private static final Pattern REMIND_PATTERN_TODAY =
            Pattern.compile("^リマインド\\s+今日\\s+(\\d{2}:\\d{2})\\s+(.+)$");

    private static final Pattern DELETE_PATTERN =
            Pattern.compile("^削除\\s+(\\d+)$");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    /**
     * ユーザーのテキストメッセージを解析して適切な処理を行い、返信テキストを返す
     */
    @Transactional
    public String handleMessage(String userId, String text) {
        String trimmed = text.trim();

        if (trimmed.equals("ヘルプ") || trimmed.equals("help")) {
            return buildHelpMessage();
        }

        if (trimmed.equals("リスト") || trimmed.equals("list")) {
            return buildReminderList(userId);
        }

        Matcher deleteMatcher = DELETE_PATTERN.matcher(trimmed);
        if (deleteMatcher.matches()) {
            long id = Long.parseLong(deleteMatcher.group(1));
            return deleteReminder(userId, id);
        }

        // 日付直接指定: リマインド 2026-03-21 14:00 メッセージ
        Matcher dateMatcher = REMIND_PATTERN_DATE.matcher(trimmed);
        if (dateMatcher.matches()) {
            String dateStr = dateMatcher.group(1);
            String timeStr = dateMatcher.group(2);
            String message = dateMatcher.group(3);
            return registerReminder(userId, dateStr + " " + timeStr, message);
        }

        // 明日指定: リマインド 明日 14:00 メッセージ
        Matcher tomorrowMatcher = REMIND_PATTERN_TOMORROW.matcher(trimmed);
        if (tomorrowMatcher.matches()) {
            String timeStr = tomorrowMatcher.group(1);
            String message = tomorrowMatcher.group(2);
            String dateStr = LocalDate.now().plusDays(1).toString();
            return registerReminder(userId, dateStr + " " + timeStr, message);
        }

        // 今日指定: リマインド 今日 14:00 メッセージ
        Matcher todayMatcher = REMIND_PATTERN_TODAY.matcher(trimmed);
        if (todayMatcher.matches()) {
            String timeStr = todayMatcher.group(1);
            String message = todayMatcher.group(2);
            String dateStr = LocalDate.now().toString();
            return registerReminder(userId, dateStr + " " + timeStr, message);
        }

        // どのコマンドにもマッチしない場合
        return "コマンドが認識できませんでした。\n「ヘルプ」と送ると使い方を確認できます。";
    }

    /**
     * リマインダーをDBに登録する
     */
    private String registerReminder(String userId, String dateTimeStr, String message) {
        LocalDateTime reminderTime;
        try {
            reminderTime = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return "日時の形式が正しくありません。\n例: リマインド 2026-03-21 14:00 会議";
        }

        if (reminderTime.isBefore(LocalDateTime.now())) {
            return "過去の日時は登録できません。未来の日時を指定してください。";
        }

        Reminder reminder = new Reminder(userId, reminderTime, message);
        Reminder saved = reminderRepository.save(reminder);

        log.info("リマインダー登録: id={}, userId={}, time={}, message={}",
                saved.getId(), userId, reminderTime, message);

        return String.format(
                "リマインダーを登録しました!\n\n" +
                "ID: %d\n" +
                "日時: %s\n" +
                "内容: %s\n\n" +
                "時間になるとお知らせします。",
                saved.getId(),
                reminderTime.format(DISPLAY_FORMATTER),
                message
        );
    }

    /**
     * ユーザーの未通知リマインダー一覧を返す
     */
    private String buildReminderList(String userId) {
        List<Reminder> reminders =
                reminderRepository.findByUserIdAndNotifiedFalseOrderByReminderTimeAsc(userId);

        if (reminders.isEmpty()) {
            return "登録中のリマインダーはありません。\n「ヘルプ」で登録方法を確認できます。";
        }

        StringBuilder sb = new StringBuilder("登録中のリマインダー一覧:\n\n");
        for (Reminder r : reminders) {
            sb.append(String.format("[ID:%d] %s\n  %s\n\n",
                    r.getId(),
                    r.getReminderTime().format(DISPLAY_FORMATTER),
                    r.getMessage()
            ));
        }
        sb.append("削除する場合は「削除 {ID}」と送ってください。");
        return sb.toString();
    }

    /**
     * 指定IDのリマインダーを削除する
     */
    private String deleteReminder(String userId, Long id) {
        Optional<Reminder> reminderOpt = reminderRepository.findByIdAndUserId(id, userId);
        if (reminderOpt.isEmpty()) {
            return String.format("ID:%d のリマインダーは見つかりませんでした。", id);
        }
        reminderRepository.delete(reminderOpt.get());
        log.info("リマインダー削除: id={}, userId={}", id, userId);
        return String.format("ID:%d のリマインダーを削除しました。", id);
    }

    /**
     * ヘルプメッセージを返す
     */
    private String buildHelpMessage() {
        return "【LINEリマインダーBot 使い方】\n\n" +
               "■ リマインダー登録\n" +
               "リマインド YYYY-MM-DD HH:mm メッセージ\n" +
               "例: リマインド 2026-03-21 14:00 会議があります\n\n" +
               "リマインド 今日 HH:mm メッセージ\n" +
               "例: リマインド 今日 18:00 薬を飲む\n\n" +
               "リマインド 明日 HH:mm メッセージ\n" +
               "例: リマインド 明日 09:00 朝のミーティング\n\n" +
               "■ 一覧表示\n" +
               "リスト\n\n" +
               "■ 削除\n" +
               "削除 {ID番号}\n" +
               "例: 削除 3\n\n" +
               "■ このヘルプ\n" +
               "ヘルプ";
    }

    /**
     * リマインダーを通知済みにマークする
     * スケジューラから呼ばれる
     */
    @Transactional
    public void markAsNotified(Long id) {
        reminderRepository.findById(id).ifPresent(r -> {
            r.setNotified(true);
            reminderRepository.save(r);
        });
    }

    /**
     * 通知対象のリマインダーを取得する
     * from 〜 to の間に設定されている未通知リマインダーを返す
     */
    public List<Reminder> findDueReminders(LocalDateTime from, LocalDateTime to) {
        return reminderRepository.findByReminderTimeBetweenAndNotifiedFalse(from, to);
    }
}
