package com.example.linereminder.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Google Calendar API を操作するサービス
 *
 * 認証方式: サービスアカウント (Service Account)
 * 事前準備:
 *   1. Google Cloud Console でプロジェクト作成
 *   2. Google Calendar API を有効化
 *   3. サービスアカウントを作成してJSONキーをダウンロード
 *   4. 自分のGoogleカレンダーをサービスアカウントのメールアドレスと共有（編集権限）
 */
@Slf4j
@Service
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "LINE Reminder Bot";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    /** サービスアカウントのJSONキーファイルパス (application.propertiesで設定) */
    @Value("${google.calendar.credentials-path}")
    private String credentialsPath;

    /** 予定を登録するGoogleカレンダーのID (通常はGmailアドレスまたは "primary") */
    @Value("${google.calendar.id}")
    private String calendarId;

    private Calendar calendarClient;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

            calendarClient = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            log.info("Google Calendar クライアント初期化完了");
        } catch (Exception e) {
            log.error("Google Calendar クライアントの初期化に失敗: {}", e.getMessage());
        }
    }

    /**
     * Googleカレンダーに予定を登録する
     *
     * @param title  予定のタイトル
     * @param start  開始日時
     * @param end    終了日時
     * @return 登録結果の返信テキスト
     */
    public String createEvent(String title, LocalDateTime start, LocalDateTime end) {
        if (calendarClient == null) {
            return "Google Calendarの初期化に失敗しています。設定を確認してください。";
        }

        try {
            Event event = new Event().setSummary(title);

            com.google.api.client.util.DateTime startDateTime =
                    new com.google.api.client.util.DateTime(
                            start.atZone(ZONE_ID).toInstant().toEpochMilli());
            event.setStart(new EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone("Asia/Tokyo"));

            com.google.api.client.util.DateTime endDateTime =
                    new com.google.api.client.util.DateTime(
                            end.atZone(ZONE_ID).toInstant().toEpochMilli());
            event.setEnd(new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Tokyo"));

            Event created = calendarClient.events().insert(calendarId, event).execute();
            log.info("カレンダー登録完了: eventId={}, title={}", created.getId(), title);

            return String.format(
                    "Googleカレンダーに予定を登録しました!\n\n" +
                    "タイトル: %s\n" +
                    "開始: %s\n" +
                    "終了: %s",
                    title,
                    start.format(DISPLAY_FORMATTER),
                    end.format(DISPLAY_FORMATTER)
            );

        } catch (IOException e) {
            log.error("カレンダー登録失敗: {}", e.getMessage());
            return "Googleカレンダーへの登録に失敗しました。しばらく経ってから再試行してください。";
        }
    }
}
