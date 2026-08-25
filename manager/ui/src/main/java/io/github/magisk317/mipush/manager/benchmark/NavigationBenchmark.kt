package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort

/** The four stable top-level Manager destinations used by the device benchmark. */
enum class BenchmarkPage(val index: Int) {
    OVERVIEW(0),
    APPLICATIONS(1),
    EVENTS(2),
    SETTINGS(3),
}

data class BenchmarkEnvironment(
    val device: String,
    val buildVariant: String,
    val dataset: String,
    val actionScript: String,
) {
    init {
        require(device.isNotBlank()) { "device must not be blank" }
        require(buildVariant.isNotBlank()) { "buildVariant must not be blank" }
        require(dataset.isNotBlank()) { "dataset must not be blank" }
        require(actionScript.isNotBlank()) { "actionScript must not be blank" }
    }
}

data class NavigationBenchmarkConfig(
    val runId: String,
    val rounds: Int = DEFAULT_ROUNDS,
    val environment: BenchmarkEnvironment,
) {
    init {
        require(runId.isNotBlank()) { "runId must not be blank" }
        require(rounds in MIN_ROUNDS..MAX_ROUNDS) {
            "rounds must be between $MIN_ROUNDS and $MAX_ROUNDS"
        }
    }

    companion object {
        const val MIN_ROUNDS = 3
        const val MAX_ROUNDS = 5
        const val DEFAULT_ROUNDS = 3
    }
}

/** One deterministic top-level navigation in the fixed action script. */
data class NavigationBenchmarkAction(
    val round: Int,
    val switchIndex: Int,
    val from: BenchmarkPage,
    val to: BenchmarkPage,
    val cohort: NavigationCohort,
    val transitionToken: String,
)

/** A fixed, device-independent action plan. It performs Overview → ... → Settings → Overview. */
class FixedNavigationActionScript private constructor(
    val config: NavigationBenchmarkConfig,
    val actions: List<NavigationBenchmarkAction>,
) {
    init {
        require(actions.size == config.rounds * ACTIONS_PER_ROUND)
        actions.groupBy { it.round }.values.forEach { roundActions ->
            require(roundActions.map { it.from }.containsAll(BenchmarkPage.entries))
            require(roundActions.size >= MIN_SWITCHES_PER_ROUND)
        }
    }

    fun actionsForRound(round: Int): List<NavigationBenchmarkAction> =
        actions.filter { it.round == round }

    companion object {
        const val ACTIONS_PER_ROUND = 4
        const val MIN_SWITCHES_PER_ROUND = 4

        /**
         * Builds the same four-switch round for every run. No Android or runtime interaction is
         * performed here; a device harness executes the returned actions.
         */
        fun create(config: NavigationBenchmarkConfig): FixedNavigationActionScript {
            val actions = buildList {
                repeat(config.rounds) { roundIndex ->
                    val pages = listOf(
                        BenchmarkPage.OVERVIEW,
                        BenchmarkPage.APPLICATIONS,
                        BenchmarkPage.EVENTS,
                        BenchmarkPage.SETTINGS,
                        BenchmarkPage.OVERVIEW,
                    )
                    pages.zipWithNext().forEachIndexed { switchIndex, (from, to) ->
                        val cohort = when {
                            roundIndex == 0 && from == BenchmarkPage.OVERVIEW &&
                                to == BenchmarkPage.APPLICATIONS ->
                                NavigationCohort.COLD_FIRST_NAVIGATION
                            to == BenchmarkPage.OVERVIEW && from != BenchmarkPage.OVERVIEW ->
                                NavigationCohort.RETURN_OVERVIEW
                            else -> NavigationCohort.WARM_ROUND_TRIP
                        }
                        add(
                            NavigationBenchmarkAction(
                                round = roundIndex + 1,
                                switchIndex = switchIndex + 1,
                                from = from,
                                to = to,
                                cohort = cohort,
                                transitionToken = "${config.runId}-r${roundIndex + 1}-s${switchIndex + 1}",
                            ),
                        )
                    }
                }
            }
            return FixedNavigationActionScript(config, actions)
        }
    }
}

enum class BenchmarkActionStatus { SUCCESS, FAILURE, TIMEOUT, CANCELLED }

data class NavigationBenchmarkActionResult(
    val action: NavigationBenchmarkAction,
    val transitionToken: String,
    val status: BenchmarkActionStatus,
    val startedAtNanos: Long,
    val finishedAtNanos: Long,
) {
    init {
        require(transitionToken.isNotBlank()) { "transitionToken must not be blank" }
        require(finishedAtNanos >= startedAtNanos) { "finishedAtNanos must not precede startedAtNanos" }
    }
}

data class NavigationBenchmarkResult(
    val config: NavigationBenchmarkConfig,
    val actions: List<NavigationBenchmarkActionResult>,
) {
    init {
        require(actions.size == config.rounds * FixedNavigationActionScript.ACTIONS_PER_ROUND)
        require(actions.map { it.action.round }.toSet() == (1..config.rounds).toSet())
        require(actions.map { it.action }.toSet().size == actions.size) {
            "each fixed action must have exactly one result"
        }
    }

    val cohorts: Set<NavigationCohort>
        get() = actions.map { it.action.cohort }.toSet()
}

fun interface NavigationBenchmarkExecutor {
    suspend fun execute(action: NavigationBenchmarkAction): NavigationBenchmarkActionResult
}

/** Executes only the supplied fixed plan; it does not install, update, or control any APK. */
suspend fun FixedNavigationActionScript.execute(
    executor: NavigationBenchmarkExecutor,
): NavigationBenchmarkResult = NavigationBenchmarkResult(
    config = config,
    actions = actions.map { action ->
        executor.execute(action).also { result ->
            require(result.action == action) { "executor returned a result for another action" }
            require(result.transitionToken == action.transitionToken) {
                "executor result must preserve the action transition token"
            }
        }
    },
)
