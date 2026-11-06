package io.github.magisk317.mipush.notification.policy

/**
 * Wire keys for the mock-replay marker carried in `PushMetaInfo.extra`.
 *
 * The producer (push pipeline and the mock message builder), the shell publish path and the
 * notification slice all read and write these keys, so the contract lives in the shared layer
 * instead of inside the push module.
 */
object MockReplayContract {
    const val EXTRA_MOCK_REPLAY = "mipush_mock_replay"
    const val EXTRA_MOCK_REPLAY_SOURCE_ID = "mipush_mock_replay_source_id"
}
