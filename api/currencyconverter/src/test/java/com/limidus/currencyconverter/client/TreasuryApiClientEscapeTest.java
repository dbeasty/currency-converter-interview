package com.limidus.currencyconverter.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TreasuryApiClientEscapeTest {

    @Test
    void escapeDescriptorForFilter_replacesSpacesWithPercent20() {
        assertThat(TreasuryApiClient.escapeDescriptorForFilter("Canada Dollar")).isEqualTo("Canada%20Dollar");
        assertThat(TreasuryApiClient.escapeDescriptorForFilter("NoSpaces")).isEqualTo("NoSpaces");
    }
}
