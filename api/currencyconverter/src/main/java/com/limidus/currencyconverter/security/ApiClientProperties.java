package com.limidus.currencyconverter.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Holds the list of registered API clients, each identified by a {@code clientId} and
 * authenticated via a {@code clientSecret}.
 *
 * <p>Configured under {@code app.security} in {@code application.yml}:
 * <pre>
 * app:
 *   security:
 *     jwt-secret: "..."
 *     jwt-expiry-seconds: 3600
 *     clients:
 *       - client-id: my-service
 *         client-secret: supersecret
 * </pre>
 *
 * In production, override secrets via environment variables or a secrets manager rather
 * than committing them to the repository.
 */
@Component
@ConfigurationProperties(prefix = "app.security")
public class ApiClientProperties {

    private String jwtSecret;
    private long jwtExpirySeconds = 3600;
    private List<ClientEntry> clients = List.of();

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public long getJwtExpirySeconds() { return jwtExpirySeconds; }
    public void setJwtExpirySeconds(long jwtExpirySeconds) { this.jwtExpirySeconds = jwtExpirySeconds; }

    public List<ClientEntry> getClients() { return clients; }
    public void setClients(List<ClientEntry> clients) { this.clients = clients; }

    public static class ClientEntry {
        private String clientId;
        private String clientSecret;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }
}
