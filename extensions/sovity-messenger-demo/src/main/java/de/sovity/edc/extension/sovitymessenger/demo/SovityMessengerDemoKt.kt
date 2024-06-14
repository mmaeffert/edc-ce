package de.sovity.edc.extension.sovitymessenger.demo

import de.sovity.edc.extension.messenger.api.MessageHandlerRegistry
import de.sovity.edc.extension.messenger.api.SovityMessenger
import org.eclipse.edc.runtime.metamodel.annotation.Inject
import org.eclipse.edc.spi.system.ServiceExtension
import org.eclipse.edc.spi.system.ServiceExtensionContext
import kotlin.math.sqrt

class SovityMessengerDemoKt : ServiceExtension {
    @Inject
    private lateinit var sovityMessenger: SovityMessenger

    @Inject
    private lateinit var registry: MessageHandlerRegistry

    override fun initialize(context: ServiceExtensionContext) {

        registry.registerKt { it: Sqrt -> Answer(sqrt(it.a)) }

        val answer = sovityMessenger.send<Answer>("http://localhost/api/dsp", Sqrt(9.0))
    }

    companion object {
        const val NAME: String = "sovityMessengerDemo"
    }
}
