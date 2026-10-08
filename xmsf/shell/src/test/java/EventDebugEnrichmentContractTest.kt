import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Source contracts for the runtime event debug JSON enrichment.
 *
 * The manager event-detail debug panel is served by the XMSF runtime through
 * [io.github.magisk317.mipush.runtime.data.EventRepository.containerToJson]. These contracts pin
 * the wiring and the guarantees the enrichment makes:
 *  - the debug JSON carries the full thrift message body plus regSec resolution diagnostics;
 *  - the click-resolution description mirrors the publish-time route decision order;
 *  - the dispatch-gate section stays read-only (the duplicate-dispatch allowance is never
 *    evaluated at view time) and fails safe per section.
 */
class EventDebugEnrichmentContractTest {

    @Test
    fun `event repository injects the enrichment sections into container json`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/runtime/data/EventRepository.kt",
        ).readText()

        assertTrue(source.contains("EventDebugEnrichment.enrich(context, container, it)"))
    }

    @Test
    fun `convert utils serialises full thrift fields and regsec diagnostics`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/utils/ConvertUtils.kt",
        ).readText()

        assertTrue(source.contains("thriftFieldAccessors(base)"))
        assertTrue(source.contains("removePrefix(\"isSet\")"))
        assertTrue(source.contains("getter.invoke(base)"))
        assertTrue(source.contains("put(\"metaInfo\", thriftToJson(it))"))
        assertTrue(source.contains("put(\"candidateCount\", candidateRegSecs.size)"))
        assertTrue(source.contains("put(\"resolved\", message != null)"))
    }

    @Test
    fun `click resolution mirrors the publish time route order`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        val routeIndexes = listOf(
            "\"url\"",
            "\"launcher_fallback\"",
            "\"sdk_activity\"",
            "\"bridge_activity\"",
            "\"xmsf_service\"",
        ).map { route -> source.indexOf("put(\"route\", $route)") }

        assertTrue(routeIndexes.all { it >= 0 }, "all click routes must be described")
        assertTrue(
            routeIndexes == routeIndexes.sorted(),
            "click routes must appear in the publish-time decision order",
        )
    }

    @Test
    fun `dispatch gates stay read only and fail safe`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/runtime/data/EventDebugEnrichment.kt",
        ).readText()

        assertTrue(source.contains("put(\"allowance\", \"not_evaluated\")"))
        assertTrue(source.contains("runCatching(block)"))
    }

    private fun resolveSource(relativePath: String): File =
        listOf(File(relativePath), File("xmsf/shell/$relativePath"))
            .firstOrNull { it.exists() }
            ?: error("source not found: $relativePath")
}
