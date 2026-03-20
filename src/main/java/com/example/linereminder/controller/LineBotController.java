package com.example.linereminder.controller;

import com.example.linereminder.service.ReminderService;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LINE Webhook コントローラー
 *
 * line-bot-spring-boot が /callback エンドポイントを自動生成し、
 * X-Line-Signature の署名検証後にここへイベントをルーティングする。
 *
 * 対応イベント:
 *   - テキストメッセージ → ReminderService に委譲
 */
@Slf4j
@LineMessageHandler
@RequiredArgsConstructor
public class LineBotController {

    private final ReminderService reminderService;
    private final LineMessagingClient lineMessagingClient;

    /**
     * テキストメッセージを受信したときの処理
     * 返り値が自動的に Reply として送信される
     */
    @EventMapping
    public TextMessage handleTextMessage(MessageEvent<TextMessageContent> event) {
        String userId = event.getSource().getUserId();
        String receivedText = event.getMessage().getText();

        log.info("メッセージ受信: userId={}, text={}", userId, receivedText);

        String replyText = reminderService.handleMessage(userId, receivedText);
        return new TextMessage(replyText);
    }
}
