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
     * Google Calendar のカラーID対応表
     * キー: 日本語色名, 値: Google Calendar colorId
     */
    private static final java.util.Map<String, String> COLOR_MAP =
            java.util.Map.ofEntries(
                    java.util.Map.entry("赤",   "11"),  // トマト
                    java.util.Map.entry("ピンク", "4"),  // フラミンゴ
                    java.util.Map.entry("黄",    "5"),  // バナナ
                    java.util.Map.entry("オレンジ","6"), // タンジェリン
                    java.util.Map.entry("緑",   "10"),  // バジル
                    java.util.Map.entry("薄緑",  "2"),  // セージ
                    java.util.Map.entry("水色",  "7"),  // ピーコック
                    java.util.Map.entry("青",    "9"),  // ブルーベリー
                    java.util.Map.entry("紫",    "3"),  // グレープ
                    java.util.Map.entry("薄紫",  "1"),  // ラベンダー
                    java.util.Map.entry("グレー", "8")  // グラファイト
            );

    /**
     * 日本語色名を Google Calendar の colorId に変換する
     * 対応していない色名の場合は null を返す（デフォルト色を使用）
     */
    public static String resolveColorId(String colorName) {
        if (colorName == null) return null;
        return COLOR_MAP.get(colorName);
    }

    /**
     * Googleカレンダーに予定を登録する
     *
     * @param title    予定のタイトル
     * @param start    開始日時
     * @param end      終了日時
     * @param colorId  カラーID (null の場合はカレンダーのデフォルト色)
     * @return 登録結果の返信テキスト
     */
    public String createEvent(String title, LocalDateTime start, LocalDateTime end, String colorId) {
        if (calendarClient == null) {
            return "Google Calendarの初期化に失敗しています。設定を確認してください。";
        }

        try {
            Event event = new Event().setSummary(title);

            if (colorId != null) {
                event.setColorId(colorId);
            }

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
            log.info("カレンダー登録完了: eventId={}, title={}, colorId={}", created.getId(), title, colorId);

            String colorLabel = colorId != null
                    ? COLOR_MAP.entrySet().stream()
                        .filter(e -> e.getValue().equals(colorId))
                        .map(java.util.Map.Entry::getKey)
                        .findFirst().orElse("")
                    : "";

            return String.format(
                    "Googleカレンダーに予定を登録しました!\n\n" +
                    "タイトル: %s\n" +
                    "開始: %s\n" +
                    "終了: %s%s",
                    title,
                    start.format(DISPLAY_FORMATTER),
                    end.format(DISPLAY_FORMATTER),
                    colorLabel.isEmpty() ? "" : "\n色: " + colorLabel
            );

        } catch (IOException e) {
            log.error("カレンダー登録失敗: {}", e.getMessage());
            return "Googleカレンダーへの登録に失敗しました。しばらく経ってから再試行してください。";
        }
    }
}
