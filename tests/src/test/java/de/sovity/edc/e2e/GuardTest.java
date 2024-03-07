/*
 * Copyright (c) 2023 sovity GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *      sovity GmbH - init
 */

package de.sovity.edc.e2e;

import de.sovity.edc.extension.e2e.connector.ConnectorRemote;
import de.sovity.edc.extension.e2e.connector.MockDataAddressRemote;
import de.sovity.edc.extension.e2e.db.TestDatabase;
import de.sovity.edc.extension.e2e.db.TestDatabaseViaTestcontainers;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.edc.connector.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static de.sovity.edc.extension.e2e.connector.DataTransferTestUtil.validateDataTransferred;
import static de.sovity.edc.extension.e2e.connector.config.ConnectorConfigFactory.forTestDatabase;
import static de.sovity.edc.extension.e2e.connector.config.ConnectorRemoteConfigFactory.fromConnectorConfig;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardTest {

    private static final String PROVIDER_PARTICIPANT_ID = "provider";
    private static final String CONSUMER_PARTICIPANT_ID = "consumer";
    private static final String TEST_BACKEND_TEST_DATA = UUID.randomUUID().toString();

    @RegisterExtension
    static EdcExtension providerEdcContext = new EdcExtension();
    @RegisterExtension
    static EdcExtension consumerEdcContext = new EdcExtension();

    @RegisterExtension
    static final TestDatabase PROVIDER_DATABASE = new TestDatabaseViaTestcontainers();
    @RegisterExtension
    static final TestDatabase CONSUMER_DATABASE = new TestDatabaseViaTestcontainers();

    private ConnectorRemote providerConnector;
    private ConnectorRemote consumerConnector;
    private MockDataAddressRemote dataAddress;

    private Duration timeout = Duration.of(20, SECONDS);

    ContractNegotiationPendingGuard guard = mock(ContractNegotiationPendingGuard.class);

    @BeforeEach
    void setup() {
        var providerConfig = forTestDatabase(PROVIDER_PARTICIPANT_ID, 21000, PROVIDER_DATABASE);
        providerEdcContext.registerServiceMock(ContractNegotiationPendingGuard.class, guard);
        providerEdcContext.setConfiguration(providerConfig.getProperties());
        providerConnector = new ConnectorRemote(fromConnectorConfig(providerConfig));

        var consumerConfig = forTestDatabase(CONSUMER_PARTICIPANT_ID, 23000, CONSUMER_DATABASE);
        consumerEdcContext.setConfiguration(consumerConfig.getProperties());
        consumerConnector = new ConnectorRemote(fromConnectorConfig(consumerConfig));

        // We use the provider EDC as data sink / data source (it has the test-backend-controller extension)
        dataAddress = new MockDataAddressRemote(providerConnector.getConfig().getDefaultEndpoint());
    }

    @Test
    void testDontInterfereWithContractNegotiation() {
        // arrange
        when(guard.test(any())).thenReturn(false);

        var assetId = UUID.randomUUID().toString();
        providerConnector.createDataOffer(assetId, dataAddress.getDataSourceUrl(TEST_BACKEND_TEST_DATA));

        // act
        await().atMost(timeout).untilAsserted(() ->
                consumerConnector.consumeOffer(
                        providerConnector.getParticipantId(),
                        providerConnector.getConfig().getProtocolEndpoint().getUri(),
                        assetId,
                        dataAddress.getDataSinkJsonLd())
        );

        // assert
        validateDataTransferred(dataAddress.getDataSinkSpyUrl(), TEST_BACKEND_TEST_DATA);
    }

    @Test
    void testStopContractNegotiation() {
        // arrange
        when(guard.test(any())).thenReturn(true);

        var assetId = UUID.randomUUID().toString();
        providerConnector.createDataOffer(assetId, dataAddress.getDataSourceUrl(TEST_BACKEND_TEST_DATA));

        // act
        Assertions.assertThrows(ConditionTimeoutException.class, () ->
                await().atMost(timeout).untilAsserted(() ->
                        consumerConnector.consumeOffer(
                                providerConnector.getParticipantId(),
                                providerConnector.getConfig().getProtocolEndpoint().getUri(),
                                assetId,
                                dataAddress.getDataSinkJsonLd())
                )
        );

        // the above negotiation will now be stopped at "REQUESTED" on the provider side.
    }
}
