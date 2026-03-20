package com.example.linereminder.scheduler;

import com.example.linereminder.entity.Reminder;
import com.example.linereminder.service.ReminderService;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * リマインダースケジューラ
 *
 * 毎分実行し、通知すべきリマインダーがあれば LINE Push 通知を送る。
 *
 * 通知タイミング:
 *   - リマインド時刻の10分前
 *   - リマインド時刻ちょうど (1分の実行幅内)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final LineMessagingClient lineMessagingClient;

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    /**
     * 毎分0秒に実行
     * cron = "秒 分 時 日 月 曜日"
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkAndSendReminders() {
        LocalDateTime now = LocalDateTime.now();

        // (1) リマインド時刻ちょうど: now 〜 now+1分 の範囲を通知
        sendRemindersInRange(now, now.plusMinutes(1), false);

        // (2) 10分前アラート: now+9分 〜 now+10分 の範囲を「もうすぐ」として通知
        sendRemindersInRange(now.plusMinutes(9), now.plusMinutes(10), true);
    }

    private void sendRemindersInRange(LocalDateTime from, LocalDateTime to, boolean isPreAlert) {
        List<Reminder> reminders = reminderService.findDueReminders(from, to);

        for (Reminder reminder : reminders) {
            try {
                String messageText = buildPushMessage(reminder, isPreAlert);
                PushMessage pushMessage = new PushMessage(
                        reminder.getUserId(),
                        new TextMessage(messageText)
                );

                lineMessagingClient.pushMessage(pushMessage).get();
                log.info("Push通知送信: id={}, userId={}, preAlert={}",
                        reminder.getId(), reminder.getUserId(), isPreAlert);

                // 10分前アラートでは notified フラグを立てない
                // (本番通知時に立てる)
                if (!isPreAlert) {
                    reminderService.markAsNotified(reminder.getId());
                }

            } catch (Exception e) {
                log.error("Push通知の送信に失敗しました: id={}, error={}",
                        reminder.getId(), e.getMessage(), e);
            }
        }
    }

    private String buildPushMessage(Reminder reminder, boolean isPreAlert) {
        String timeStr = reminder.getReminderTime().format(DISPLAY_FORMATTER);
        if (isPreAlert) {
            return String.format(
                    "【リマインド 10分前】\n\n" +
                    "もうすぐ時間です!\n" +
                    "日時: %s\n" +
                    "内容: %s",
                    timeStr, reminder.getMessage()
            );
        } else {
            return String.format(
                    "【リマインド】\n\n" +
                    "%s\n\n" +
                    "設定時刻: %s",
                    reminder.getMessage(), timeStr
            );
        }
    }
}
