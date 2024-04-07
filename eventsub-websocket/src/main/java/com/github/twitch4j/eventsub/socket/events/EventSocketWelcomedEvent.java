package com.github.twitch4j.eventsub.socket.events;

import com.github.twitch4j.eventsub.socket.TwitchEventSocket;
import lombok.Value;

/**
 * Fired when a {@link TwitchEventSocket} receives a
 * {@link com.github.twitch4j.eventsub.socket.enums.SocketMessageType#SESSION_WELCOME} message.
 * <p>
 * As a result, {@link TwitchEventSocket#getWebsocketId()} will be populated,
 * which allows for EventSub subscriptions to be registered with Twitch's Helix API.
 */
@Value
public class EventSocketWelcomedEvent {

    /**
     * The EventSocket instance whose connection status changed.
     */
    TwitchEventSocket connection;

    /**
     * The ID that was assigned to the websocket upon connect.
     */
    String sessionId;

}
