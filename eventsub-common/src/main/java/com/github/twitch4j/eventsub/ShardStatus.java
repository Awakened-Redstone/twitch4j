package com.github.twitch4j.eventsub;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.github.twitch4j.common.annotation.Unofficial;

public enum ShardStatus {

    /**
     * The shard is enabled.
     */
    ENABLED,

    /**
     * The shard is pending verification of the specified callback URL.
     */
    WEBHOOK_CALLBACK_VERIFICATION_PENDING,

    /**
     * The specified callback URL failed verification.
     */
    WEBHOOK_CALLBACK_VERIFICATION_FAILED,

    /**
     * The notification delivery failure rate was too high.
     */
    NOTIFICATION_FAILURES_EXCEEDED,

    /**
     * The client closed the connection.s
     */
    WEBSOCKET_DISCONNECTED,

    /**
     * The client failed to respond to a ping message.
     */
    WEBSOCKET_FAILED_PING_PONG,

    /**
     * A websocket conduit shard did not reconnect to a Twitch-specified url upon their request.
     */
    @Unofficial // https://github.com/twitchdev/issues/issues/931#issuecomment-2018532569
    WEBSOCKET_FAILED_TO_RECONNECT,

    /**
     * The client sent a non-pong message.
     */
    WEBSOCKET_RECEIVED_INBOUND_TRAFFIC,

    /**
     * The Twitch WebSocket server experienced an unexpected error.
     */
    WEBSOCKET_INTERNAL_ERROR,

    /**
     * The Twitch WebSocket server timed out writing the message to the client.
     */
    WEBSOCKET_NETWORK_TIMEOUT,

    /**
     * The Twitch WebSocket server experienced a network error writing the message to the client.
     */
    WEBSOCKET_NETWORK_ERROR,

    /**
     * Twitch assigned the shard an undocumented status; please report to our issue tracker!
     */
    @JsonEnumDefaultValue
    UNKNOWN

}
