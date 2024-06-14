package de.sovity.edc.extension.messenger;

import de.sovity.edc.extension.e2e.connector.config.ConnectorConfig;
import de.sovity.edc.extension.e2e.db.TestDatabase;
import de.sovity.edc.extension.e2e.db.TestDatabaseViaTestcontainers;
import de.sovity.edc.extension.messenger.api.MessageHandlerRegistry;
import de.sovity.edc.extension.messenger.api.SovityMessenger;
import de.sovity.edc.extension.messenger.api.SovityMessengerException;
import de.sovity.edc.extension.messenger.dto.Addition;
import de.sovity.edc.extension.messenger.dto.Answer;
import de.sovity.edc.extension.messenger.dto.Multiplication;
import de.sovity.edc.extension.messenger.dto.UnsupportedMessage;
import lombok.val;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.spi.iam.TokenDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static de.sovity.edc.extension.e2e.connector.config.ConnectorConfigFactory.forTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class SovityMessengerExtensionE2eTest {
    private static final String EMITTER_PARTICIPANT_ID = "emitter";
    private static final String RECEIVER_PARTICIPANT_ID = "receiver";

    @RegisterExtension
    static EdcExtension emitterEdcContext = new EdcExtension();
    @RegisterExtension
    static EdcExtension receiverEdcContext = new EdcExtension();

    @RegisterExtension
    static final TestDatabase EMITTER_DATABASE = new TestDatabaseViaTestcontainers();
    @RegisterExtension
    static final TestDatabase RECEIVER_DATABASE = new TestDatabaseViaTestcontainers();

    private ConnectorConfig providerConfig;
    private ConnectorConfig consumerConfig;

    private String counterPartyAddress;

    @BeforeEach
    void setup() {
        providerConfig = forTestDatabase(EMITTER_PARTICIPANT_ID, EMITTER_DATABASE);
        emitterEdcContext.setConfiguration(providerConfig.getProperties());
        emitterEdcContext.registerServiceMock(TokenDecorator.class, (td) -> td);

        consumerConfig = forTestDatabase(RECEIVER_PARTICIPANT_ID, RECEIVER_DATABASE);
        receiverEdcContext.setConfiguration(consumerConfig.getProperties());
        receiverEdcContext.registerServiceMock(TokenDecorator.class, (td) -> td);

        counterPartyAddress = "http://localhost:" + consumerConfig.getProtocolEndpoint().port() + consumerConfig.getProtocolEndpoint().path();
    }

    @Test
    void e2eTest() {
        val sovityMessenger = emitterEdcContext.getContext().getService(SovityMessenger.class);
        val handlers = receiverEdcContext.getContext().getService(MessageHandlerRegistry.class);
        handlers.register("add", (Function<Addition, Answer>) in -> new Answer(in.getOp1() + in.getOp2()));
        handlers.register("mul", (Function<Addition, Answer>) in -> new Answer(in.getOp1() * in.getOp2()));

        val counterPartyAddress = "http://localhost:" + consumerConfig.getProtocolEndpoint().port() + consumerConfig.getProtocolEndpoint().path();
        val added = sovityMessenger.send(Answer.class, counterPartyAddress, new Addition(20, 30));
        val multiplied = sovityMessenger.send(Answer.class, counterPartyAddress, new Multiplication(20, 30));

        // assert
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            added.get()
                .onFailure(it -> fail(it.getFailureDetail()))
                .onSuccess(it -> {
                    assertThat(it).isInstanceOf(Answer.class);
                    assertThat(it.getAnswer()).isEqualTo(50);
                });
        });

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            multiplied.get()
                .onFailure(it -> fail(it.getFailureDetail()))
                .onSuccess(it -> {
                    assertThat(it).isInstanceOf(Answer.class);
                    assertThat(it.getAnswer()).isEqualTo(600);
                });
        });
    }

    @Test
    void e2eNoHandlerTest() {
        val sovityMessenger = emitterEdcContext.getContext().getService(SovityMessenger.class);

        val added = sovityMessenger.send(Answer.class, counterPartyAddress, new UnsupportedMessage());

        // assert
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            val exception = assertThrows(ExecutionException.class, added::get);
            assertThat(exception.getCause()).isInstanceOf(SovityMessengerException.class);
            assertThat(exception.getCause().getMessage()).isEqualTo("No handler for message type unsupported");
        });
    }

}
