package com.echohealthcare.ftp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {

    // These values will be automatically injected from Azure Key Vault
    @Value("${ftp-username:defaultUser}")
    private String ftpUsername;

    @Value("${ftp-password:defaultPass}")
    private String ftpPassword;

    @Value("${ftp-server:localhost}")
    private String ftpServer;

    @Value("${database-connection-string:jdbc:mysql://localhost:3306/testdb}")
    private String databaseConnectionString;

    @Value("${api-key:default-api-key}")
    private String apiKey;

    // Getters
    public String getFtpUsername() {
        return ftpUsername;
    }

    public String getFtpPassword() {
        return ftpPassword;
    }

    public String getFtpServer() {
        return ftpServer;
    }

    public String getDatabaseConnectionString() {
        return databaseConnectionString;
    }

    public String getApiKey() {
        return apiKey;
    }
}