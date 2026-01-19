package com.wakita181009.cleanarchitecture.framework.runner

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import kotlin.system.exitProcess

abstract class SyncJobRunner<E> : ApplicationRunner {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val JOB_OPTION = "job"

        fun isJobMode(args: Array<String>): Boolean = args.any { it.startsWith("--$JOB_OPTION=") }
    }

    protected abstract val jobName: String
    protected abstract val entityName: String

    protected abstract suspend fun execute(): Either<E, Int>

    override fun run(args: ApplicationArguments) {
        if (!shouldRunJob(args)) {
            logger.debug("Skipping {} sync. Use --{}={} to enable.", entityName, JOB_OPTION, jobName)
            return
        }

        logger.info("Starting {} sync...", entityName)

        runBlocking {
            execute()
        }.fold(
            ifLeft = { error -> handleError(error) },
            ifRight = { count -> handleSuccess(count) },
        )
    }

    private fun shouldRunJob(args: ApplicationArguments): Boolean = args.getOptionValues(JOB_OPTION)?.firstOrNull() == jobName

    private fun handleSuccess(count: Int) {
        logger.info("{} sync completed successfully. Synced {} {}.", entityName.replaceFirstChar { it.uppercase() }, count, entityName)
        exitProcess(0)
    }

    private fun handleError(error: E) {
        logger.error("Error occurred during {} sync: {}", entityName, error)
        exitProcess(1)
    }
}
