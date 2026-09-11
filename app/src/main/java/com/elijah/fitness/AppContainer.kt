package com.elijah.fitness

/**
 * Manual dependency container. Created once in [FitnessApp] and read by
 * ViewModel factories that need shared collaborators (repository, settings).
 *
 * Step 2 leaves this empty on purpose; Step 4 wires in the workout repository
 * and user settings.
 */
class AppContainer
