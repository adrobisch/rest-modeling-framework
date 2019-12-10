package io.vrap.rmf.raml.generic.generator.php;

import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.resources.UriParameter;
import io.vrap.rmf.raml.model.security.OAuth20Settings;

import java.util.List;

public class ApiGenModel {
    final private Api api;

    public ApiGenModel(Api api) {
        this.api = api;
    }

    public Api getApi() {
        return api;
    }

    public String getBaseUri() {
        return api.getBaseUri().getTemplate();
    }

    public String getAuthUri() {
        return api.getSecuritySchemes().stream()
                .filter(securityScheme -> securityScheme.getSettings() instanceof OAuth20Settings)
                .map(securityScheme -> ((OAuth20Settings)securityScheme.getSettings()).getAccessTokenUri())
                .findFirst().orElse("");
    }

    public String[] getBaseUriVariables() {
        return api.getBaseUri().getValue().getVariables();
    }

    public Boolean getHasBaseUriVariables() { return api.getBaseUri().getValue().getVariables().length > 0; }

    public List<UriParameter> getBaseUriParameters() {
        return api.getBaseUriParameters();
    }
}
