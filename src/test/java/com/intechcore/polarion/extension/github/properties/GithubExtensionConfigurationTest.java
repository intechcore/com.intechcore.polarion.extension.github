package com.intechcore.polarion.extension.github.properties;

import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import com.polarion.core.config.impl.SystemValueReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(CurrentContextExtension.class)
class GithubExtensionConfigurationTest {

    private MockedStatic<SystemValueReader> systemValueReaderStatic;
    private SystemValueReader systemValueReader;

    @BeforeEach
    void setUp() {
        systemValueReader = mock(SystemValueReader.class);
        systemValueReaderStatic = mockStatic(SystemValueReader.class);
        systemValueReaderStatic.when(SystemValueReader::getInstance).thenReturn(systemValueReader);
    }

    @AfterEach
    void tearDown() {
        systemValueReaderStatic.close();
    }

    /** The About page lists the property with its description, and the empty default reads GitHub anonymously. */
    @Test
    void namesTheSecretOfTheToken() {
        when(systemValueReader.readString(endsWith(".token.secret"), eq(""))).thenReturn("github-token");
        GithubExtensionConfiguration configuration = new GithubExtensionConfiguration();

        assertThat(configuration.getTokenSecret()).isEqualTo("github-token");
        assertThat(configuration.getSupportedProperties()).contains(GithubExtensionConfiguration.TOKEN_SECRET, "debug");
        assertThat(configuration.getTokenSecretDescription()).contains("Polarion secret").contains("60 requests per hour");
        assertThat(configuration.getTokenSecretDefaultValue()).isEmpty();
        when(systemValueReader.readString(endsWith(".mail.from"), eq(""))).thenReturn("github@example.com");
        assertThat(configuration.getMailFrom()).isEqualTo("github@example.com");
        assertThat(configuration.getSupportedProperties()).contains(GithubExtensionConfiguration.MAIL_FROM);
        assertThat(configuration.getMailFromDescription()).contains("sender");
        assertThat(configuration.getMailFromDefaultValue()).isEmpty();
    }
}
