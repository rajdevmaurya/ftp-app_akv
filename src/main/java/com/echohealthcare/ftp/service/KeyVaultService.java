package com.echohealthcare.ftp.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeyVaultService {

    private static final Logger logger = LoggerFactory.getLogger(KeyVaultService.class);

    @Value("${spring.cloud.azure.keyvault.secret.endpoint}")
    private String keyVaultUrl;

    private SecretClient secretClient;

    @PostConstruct
    public void init() {
        try {
            this.secretClient = new SecretClientBuilder()
                    .vaultUrl(keyVaultUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            logger.info("Azure Key Vault client initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Azure Key Vault client: {}", e.getMessage());
        }
    }

    /**
     * Retrieve a secret from Azure Key Vault
     */
    public String getSecret(String secretName) {
        try {
            KeyVaultSecret secret = secretClient.getSecret(secretName);
            logger.debug("Successfully retrieved secret: {}", secretName);
            return secret.getValue();
        } catch (Exception e) {
            logger.error("Failed to retrieve secret '{}': {}", secretName, e.getMessage());
            return null;
        }
    }

    /**
     * Retrieve multiple secrets from Azure Key Vault
     */
    public Map<String, String> getSecrets(String... secretNames) {
        Map<String, String> secrets = new HashMap<>();
        for (String secretName : secretNames) {
            String value = getSecret(secretName);
            if (value != null) {
                secrets.put(secretName, value);
            }
        }
        return secrets;
    }

    /**
     * Check if Key Vault is accessible
     */
    public boolean isKeyVaultAccessible() {
        try {
            // Try to list secrets to check connectivity
            secretClient.listPropertiesOfSecrets().iterator().hasNext();
            return true;
        } catch (Exception e) {
            logger.error("Key Vault is not accessible: {}", e.getMessage());
            return false;
        }
    }
}