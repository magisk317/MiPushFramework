package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Property 12: VoIP 构建器/序列过滤独立性
 *
 * For any (styleType, busiType) combination:
 * - Builder selection depends ONLY on notification_style_type == "6"
 * - Sequence filtering depends ONLY on msg_busi_type == "voip"
 * - The two decisions are independent: one does not affect the other
 *
 * Quadrants:
 * - style=6, busi="voip" → VoIP builder YES, sequence filter YES
 * - style=6, busi≠"voip" → VoIP builder YES, sequence filter NO (style-only)
 * - style≠6, busi="voip" → VoIP builder NO, sequence filter YES (busi-only)
 * - style≠6, busi≠"voip" → VoIP builder NO, sequence filter NO
 *
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
class Property12VoipBuilderSequenceIndependenceTest {

    @BeforeProperty
    fun setUp() {
        VoipNotificationHelper.resetForTest()
    }

    @AfterProperty
    fun tearDown() {
        VoipNotificationHelper.resetForTest()
    }

    // -- Arbitraries --

    /** Style types: "6" (VoIP) vs arbitrary non-6 values */
    @Provide
    fun styleTypes(): Arbitrary<String?> = Arbitraries.oneOf(
        Arbitraries.just("6"),
        Arbitraries.integers().between(0, 100).filter { it != 6 }.map { it.toString() },
        Arbitraries.just(null as String?),
    )

    /** Business types: "voip" vs arbitrary non-voip values */
    @Provide
    fun busiTypes(): Arbitrary<String?> = Arbitraries.oneOf(
        Arbitraries.just("voip"),
        Arbitraries.of("message", "notification", "im", "call", ""),
        Arbitraries.just(null as String?),
    )

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.strings()
        .ofMinLength(3)
        .ofMaxLength(40)
        .alpha()
        .numeric()
        .withChars('.')

    @Provide
    fun sequences(): Arbitrary<Long> = Arbitraries.longs().between(1, Long.MAX_VALUE / 2)

    @Provide
    fun styleAndBusiCombinations(): Arbitrary<StyleBusiCombination> = Combinators.combine(
        styleTypes(),
        busiTypes(),
    ).`as` { style, busi -> StyleBusiCombination(style, busi) }

    data class StyleBusiCombination(val styleType: String?, val busiType: String?)

    // -- Properties --

    /**
     * Property: Builder selection depends ONLY on styleType == "6".
     * Regardless of busiType, isVoipNotification returns true iff styleType == "6".
     *
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 200)
    fun `builder selection depends only on styleType`(
        @ForAll("styleAndBusiCombinations") combo: StyleBusiCombination,
    ) {
        val meta = buildMeta(combo.styleType, combo.busiType)
        val usesVoipBuilder = VoipNotificationHelper.isVoipNotification(meta)

        val expectedBuilder = combo.styleType == "6"
        assertEquals(
            expectedBuilder,
            usesVoipBuilder,
            "VoIP builder selection must depend only on styleType==${combo.styleType}, " +
                "but got $usesVoipBuilder (busiType=${combo.busiType} should be irrelevant)",
        )
    }

    /**
     * Property: Sequence filtering depends ONLY on busiType == "voip".
     * Regardless of styleType, shouldDropStale engages only when busiType is "voip".
     *
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 200)
    fun `sequence filtering depends only on busiType`(
        @ForAll("styleAndBusiCombinations") combo: StyleBusiCombination,
        @ForAll("packageNames") pkg: String,
        @ForAll("sequences") seq: Long,
    ) {
        VoipNotificationHelper.resetForTest()

        // Set up a higher stored sequence so we can test if filtering engages
        val higherSeq = seq + 1000
        val setupMeta = PushMetaInfo().apply {
            extra = mutableMapOf(
                "msg_busi_type" to "voip",
                "sequence" to higherSeq.toString(),
            )
        }
        shouldDropStaleForTest(setupMeta, pkg)

        // Now test with arbitrary combo at a lower sequence
        val testMeta = buildMetaWithSequence(combo.styleType, combo.busiType, seq)
        val dropped = shouldDropStaleForTest(testMeta, pkg)

        val expectsFiltering = combo.busiType == "voip"
        if (expectsFiltering) {
            // busiType="voip" with lower sequence → should be dropped
            assertTrue(
                dropped,
                "With busiType='voip', sequence=$seq < stored=$higherSeq must be dropped " +
                    "(styleType=${combo.styleType} should be irrelevant)",
            )
        } else {
            // non-voip busiType → sequence filtering does NOT engage, never dropped
            assertFalse(
                dropped,
                "With busiType=${combo.busiType}, sequence filtering must not engage " +
                    "(styleType=${combo.styleType} should be irrelevant)",
            )
        }
    }

    /**
     * Property: Independence — changing styleType does not affect sequence filtering outcome,
     * and changing busiType does not affect builder selection outcome.
     *
     * For any combination, the builder result with styleType=X and busiType=A equals the
     * builder result with styleType=X and busiType=B (any B). Similarly, sequence filtering
     * with busiType=Y and styleType=A equals filtering with busiType=Y and styleType=B.
     *
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 200)
    fun `builder and sequence filter are mutually independent`(
        @ForAll("styleTypes") styleType1: String?,
        @ForAll("busiTypes") busiType1: String?,
        @ForAll("busiTypes") busiType2: String?,
        @ForAll("styleTypes") styleType2: String?,
    ) {
        // Builder independence: changing busiType does not change builder selection
        val builderWithBusi1 = VoipNotificationHelper.isVoipNotification(buildMeta(styleType1, busiType1))
        val builderWithBusi2 = VoipNotificationHelper.isVoipNotification(buildMeta(styleType1, busiType2))
        assertEquals(
            builderWithBusi1,
            builderWithBusi2,
            "Builder selection with styleType=$styleType1 must be the same " +
                "regardless of busiType ($busiType1 vs $busiType2)",
        )

        // Sequence filter independence: changing styleType does not change filter engagement
        val extras1 = buildExtras(styleType1, busiType1)
        val extras2 = buildExtras(styleType2, busiType1)
        val filterEngages1 = VoipNotificationHelper.isVoipBusiness(extras1)
        val filterEngages2 = VoipNotificationHelper.isVoipBusiness(extras2)
        assertEquals(
            filterEngages1,
            filterEngages2,
            "Sequence filter engagement with busiType=$busiType1 must be the same " +
                "regardless of styleType ($styleType1 vs $styleType2)",
        )
    }

    /**
     * Property: Style-only case — styleType=6 with busiType≠"voip" uses VoIP builder
     * but does NOT participate in sequence filtering (never drops).
     *
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 200)
    fun `style-only uses voip builder without sequence filtering`(
        @ForAll("packageNames") pkg: String,
        @ForAll("sequences") seq: Long,
    ) {
        VoipNotificationHelper.resetForTest()

        // Establish a high sequence via busi-type voip
        val setupMeta = PushMetaInfo().apply {
            extra = mutableMapOf(
                "msg_busi_type" to "voip",
                "sequence" to (seq + 1000).toString(),
            )
        }
        shouldDropStaleForTest(setupMeta, pkg)

        // style-only: styleType=6, busi=not "voip"
        val styleOnlyMeta = buildMetaWithSequence("6", "message", seq)

        // Should use VoIP builder
        assertTrue(
            VoipNotificationHelper.isVoipNotification(styleOnlyMeta),
            "styleType=6 must select VoIP builder even when busiType≠voip",
        )

        // Should NOT participate in sequence filtering (never dropped)
        assertFalse(
            shouldDropStaleForTest(styleOnlyMeta, pkg),
            "style-only (styleType=6, busiType≠voip) must not participate in sequence filtering",
        )
    }

    /**
     * Property: Busi-only case — busiType="voip" with styleType≠6 participates in
     * sequence filtering but does NOT use VoIP builder.
     *
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 200)
    fun `busi-only participates in sequence filtering without voip builder`(
        @ForAll("packageNames") pkg: String,
        @ForAll("sequences") seq: Long,
    ) {
        VoipNotificationHelper.resetForTest()

        // Establish a high sequence
        val higherSeq = seq + 1000
        val setupMeta = PushMetaInfo().apply {
            extra = mutableMapOf(
                "msg_busi_type" to "voip",
                "sequence" to higherSeq.toString(),
            )
        }
        shouldDropStaleForTest(setupMeta, pkg)

        // busi-only: busiType="voip", styleType=not 6 (e.g., "1")
        val busiOnlyMeta = buildMetaWithSequence("1", "voip", seq)

        // Should NOT use VoIP builder
        assertFalse(
            VoipNotificationHelper.isVoipNotification(busiOnlyMeta),
            "styleType≠6 must not select VoIP builder even when busiType=voip",
        )

        // Should participate in sequence filtering (lower seq is dropped)
        assertTrue(
            shouldDropStaleForTest(busiOnlyMeta, pkg),
            "busi-only (busiType=voip, styleType≠6) with seq=$seq < stored=$higherSeq must be dropped",
        )
    }

    // -- Helpers --

    private fun shouldDropStaleForTest(
        metaInfo: PushMetaInfo,
        packageName: String,
    ): Boolean = VoipNotificationHelper.shouldDropStale(metaInfo, packageName, userId = 0)

    private fun buildMeta(styleType: String?, busiType: String?): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf<String, String>().also { map ->
                styleType?.let { map["notification_style_type"] = it }
                busiType?.let { map["msg_busi_type"] = it }
            }
        }
    }

    private fun buildMetaWithSequence(styleType: String?, busiType: String?, sequence: Long): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf<String, String>().also { map ->
                styleType?.let { map["notification_style_type"] = it }
                busiType?.let { map["msg_busi_type"] = it }
                map["sequence"] = sequence.toString()
            }
        }
    }

    private fun buildExtras(styleType: String?, busiType: String?): Map<String, String> {
        return mutableMapOf<String, String>().also { map ->
            styleType?.let { map["notification_style_type"] = it }
            busiType?.let { map["msg_busi_type"] = it }
        }
    }
}
