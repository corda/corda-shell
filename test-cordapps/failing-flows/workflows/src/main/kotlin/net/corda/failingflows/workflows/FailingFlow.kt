package net.corda.failingflows.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
@SuppressWarnings("TooGenericExceptionThrown")
class FailingFlow : FlowLogic<Unit>() {

    object STARTING : ProgressTracker.Step("StartingUP")
    object THROWING : ProgressTracker.Step("AboutToThrow")
    object THROWED : ProgressTracker.Step("Throwed")

    override val progressTracker: ProgressTracker = ProgressTracker(STARTING, THROWING, THROWED)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = STARTING
        progressTracker.currentStep = THROWING
        throw Exception("KA-BOOM")
    }
}
