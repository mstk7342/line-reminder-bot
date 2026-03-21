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
 *   リマインド YYYY-MM-DD HH:mm メッセージ          → リマインダー登録
 *   カレンダー YYYY-MM-DD HH:mm HH:mm タイトル      → Googleカレンダー登録
 *   リスト                                          → 登録中リマインダー一覧
 *   削除 {id}                                       → リマインダー削除
 *   ヘルプ                                           → 使い方表示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final GoogleCalendarService googleCalendarService;

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

    // カレンダーパターン: "カレンダー 今日 14:00 会議"
    private static final Pattern CALENDAR_PATTERN_TODAY =
            Pattern.compile("^カレンダー\\s+今日\\s+(\\d{1,2}:\\d{2})\\s+(.+)$");

    // カレンダーパターン: "カレンダー 明日 14:00 会議"
    private static final Pattern CALENDAR_PATTERN_TOMORROW =
            Pattern.compile("^カレンダー\\s+明日\\s+(\\d{1,2}:\\d{2})\\s+(.+)$");

    // カレンダーパターン: "カレンダー 3/21 14:00 会議" (M/DD or MM/DD)
    private static final Pattern CALENDAR_PATTERN_SLASH =
            Pattern.compile("^カレンダー\\s+(\\d{1,2}/\\d{1,2})\\s+(\\d{1,2}:\\d{2})\\s+(.+)$");

    // カレンダーパターン: "カレンダー 2026-03-21 14:00 会議"
    private static final Pattern CALENDAR_PATTERN_DATE =
            Pattern.compile("^カレンダー\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s+(.+)$");

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

        // 今日指定: カレンダー 今日 14:00 タイトル
        Matcher calTodayMatcher = CALENDAR_PATTERN_TODAY.matcher(trimmed);
        if (calTodayMatcher.matches()) {
            String dateStr  = LocalDate.now().toString();
            String startStr = calTodayMatcher.group(1);
            String title    = calTodayMatcher.group(2);
            return registerCalendarEvent(dateStr, startStr, title);
        }

        // 明日指定: カレンダー 明日 14:00 タイトル
        Matcher calTomorrowMatcher = CALENDAR_PATTERN_TOMORROW.matcher(trimmed);
        if (calTomorrowMatcher.matches()) {
            String dateStr  = LocalDate.now().plusDays(1).toString();
            String startStr = calTomorrowMatcher.group(1);
            String title    = calTomorrowMatcher.group(2);
            return registerCalendarEvent(dateStr, startStr, title);
        }

        // M/DD指定: カレンダー 3/21 14:00 タイトル
        Matcher calSlashMatcher = CALENDAR_PATTERN_SLASH.matcher(trimmed);
        if (calSlashMatcher.matches()) {
            String slashDate = calSlashMatcher.group(1); // "3/21"
            String startStr  = calSlashMatcher.group(2);
            String title     = calSlashMatcher.group(3);
            String dateStr   = resolveSlashDate(slashDate);
            if (dateStr == null) {
                return "日付の形式が正しくありません。\n例: カレンダー 3/21 14:00 会議";
            }
            return registerCalendarEvent(dateStr, startStr, title);
        }

        // 日付直接指定: カレンダー 2026-03-21 14:00 タイトル
        Matcher calDateMatcher = CALENDAR_PATTERN_DATE.matcher(trimmed);
        if (calDateMatcher.matches()) {
            String dateStr  = calDateMatcher.group(1);
            String startStr = calDateMatcher.group(2);
            String title    = calDateMatcher.group(3);
            return registerCalendarEvent(dateStr, startStr, title);
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
     * Googleカレンダーに予定を登録する（終了時刻は開始の1時間後で自動設定）
     */
    private String registerCalendarEvent(String dateStr, String startStr, String title) {
        // 時刻が "9:00" のように1桁の場合に備えてゼロ埋め
        String normalizedTime = startStr.length() == 4 ? "0" + startStr : startStr;
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.parse(dateStr + " " + normalizedTime, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return "日時の形式が正しくありません。\n例: カレンダー 今日 14:00 会議";
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            return "過去の日時は登録できません。未来の日時を指定してください。";
        }

        // 終了時刻は自動で1時間後
        LocalDateTime endTime = startTime.plusHours(1);

        log.info("カレンダー登録: date={}, start={}, title={}", dateStr, startStr, title);
        return googleCalendarService.createEvent(title, startTime, endTime);
    }

    /**
     * "3/21" や "12/5" 形式の日付を "2026-03-21" に変換する
     * 月が現在より前の場合は翌年として扱う
     */
    private String resolveSlashDate(String slashDate) {
        try {
            String[] parts = slashDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int day   = Integer.parseInt(parts[1]);
            int year  = LocalDate.now().getYear();
            LocalDate date = LocalDate.of(year, month, day);
            // 指定日が過去なら翌年扱い
            if (date.isBefore(LocalDate.now())) {
                date = date.plusYears(1);
            }
            return date.toString();
        } catch (Exception e) {
            return null;
        }
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
               "■ Googleカレンダー登録 (終了は自動で1時間後)\n" +
               "カレンダー 今日 HH:mm タイトル\n" +
               "例: カレンダー 今日 14:00 会議\n\n" +
               "カレンダー 明日 HH:mm タイトル\n" +
               "例: カレンダー 明日 9:00 歯医者\n\n" +
               "カレンダー M/DD HH:mm タイトル\n" +
               "例: カレンダー 3/25 18:00 飲み会\n\n" +
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
