package com.sms.edgenode.data.db

object SmsStatus {
    const val RECEIVED = "RECEIVED"
    const val QUEUED = "QUEUED"
    const val SENDING = "SENDING"
    const val SUBMITTED = "SUBMITTED"     // Radio accepted
    const val SENT = "SENT"               // Carrier handoff result
    const val DELIVERED = "DELIVERED"     // Handset ack
    const val FAILED = "FAILED"
    const val EXPIRED = "EXPIRED"
    const val DELIVERED_UNKNOWN = "DELIVERED_UNKNOWN" // TTL expired post-sent
    const val EXPIRED_BACKPRESSURE = "EXPIRED_BACKPRESSURE"
    const val HELD = "HELD" // Compliance hold
    const val SYSTEM = "SYSTEM" // Carrier system messages (Balance, bundles, etc)
}
