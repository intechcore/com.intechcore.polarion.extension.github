package com.intechcore.polarion.extension.github.properties;

import ch.sbb.polarion.extension.generic.properties.CurrentExtensionConfiguration;
import ch.sbb.polarion.extension.generic.properties.ExtensionConfiguration;
import ch.sbb.polarion.extension.generic.properties.mappings.PropertyMapping;
import ch.sbb.polarion.extension.generic.properties.mappings.PropertyMappingDefaultValue;
import ch.sbb.polarion.extension.generic.properties.mappings.PropertyMappingDescription;
import ch.sbb.polarion.extension.generic.util.Discoverable;
import com.polarion.core.config.impl.SystemValueReader;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The properties of the extension in {@code polarion.properties}, under the prefix
 * {@code com.intechcore.polarion.extension.github.}. The About page lists them.
 */
@Discoverable
public class GithubExtensionConfiguration extends ExtensionConfiguration {

    public static final String TOKEN_SECRET = "token.secret";
    public static final String TOKEN_SECRET_DESCRIPTION = "Name of the Polarion secret holding a GitHub token."
            + " Empty: GitHub is read without a token, 60 requests per hour for the server.";
    public static final String TOKEN_SECRET_DEFAULT_VALUE = "";

    @PropertyMapping(TOKEN_SECRET)
    public String getTokenSecret() {
        return SystemValueReader.getInstance().readString(getPropertyPrefix() + TOKEN_SECRET, TOKEN_SECRET_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    @PropertyMappingDescription(TOKEN_SECRET)
    public String getTokenSecretDescription() {
        return TOKEN_SECRET_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    @PropertyMappingDefaultValue(TOKEN_SECRET)
    public String getTokenSecretDefaultValue() {
        return TOKEN_SECRET_DEFAULT_VALUE;
    }

    @Override
    public @NotNull List<String> getSupportedProperties() {
        List<String> supportedProperties = new ArrayList<>(super.getSupportedProperties());
        supportedProperties.add(TOKEN_SECRET);
        return supportedProperties;
    }

    public static GithubExtensionConfiguration getInstance() {
        return (GithubExtensionConfiguration) CurrentExtensionConfiguration.getInstance().getExtensionConfiguration();
    }
}
