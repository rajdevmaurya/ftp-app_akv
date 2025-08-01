package com.echohealthcare.ftp.controller;

import com.echohealthcare.ftp.config.AppConfig;
import com.echohealthcare.ftp.service.FtpService;
import com.echohealthcare.ftp.service.KeyVaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ftp")
public class FtpController {

    @Autowired
    private FtpService ftpService;

    @Autowired
    private KeyVaultService keyVaultService;

    @Autowired
    private AppConfig appConfig;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("keyVaultAccessible", keyVaultService.isKeyVaultAccessible());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Get configuration (masked sensitive data)
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("ftpServer", appConfig.getFtpServer());
        config.put("ftpUsername", appConfig.getFtpUsername());
        config.put("ftpPassword", maskPassword(appConfig.getFtpPassword()));
        config.put("databaseConnectionString", maskConnectionString(appConfig.getDatabaseConnectionString()));
        config.put("apiKey", maskApiKey(appConfig.getApiKey()));
        return ResponseEntity.ok(config);
    }

    /**
     * Test FTP connection
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testFtpConnection() {
        Map<String, Object> response = new HashMap<>();
        boolean connected = ftpService.connectToFtp();
        response.put("connected", connected);
        response.put("message", connected ? "FTP connection successful" : "FTP connection failed");
        return ResponseEntity.ok(response);
    }

    /**
     * Upload file via FTP
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam String localPath,
            @RequestParam String remotePath) {

        Map<String, Object> response = new HashMap<>();
        boolean success = ftpService.uploadFile(localPath, remotePath);
        response.put("success", success);
        response.put("message", success ? "File uploaded successfully" : "File upload failed");
        response.put("localPath", localPath);
        response.put("remotePath", remotePath);
        return ResponseEntity.ok(response);
    }

    /**
     * Download file via FTP
     */
    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadFile(
            @RequestParam String remotePath,
            @RequestParam String localPath) {

        Map<String, Object> response = new HashMap<>();
        boolean success = ftpService.downloadFile(remotePath, localPath);
        response.put("success", success);
        response.put("message", success ? "File downloaded successfully" : "File download failed");
        response.put("remotePath", remotePath);
        response.put("localPath", localPath);
        return ResponseEntity.ok(response);
    }

    /**
     * Get specific secret from Key Vault
     */
    @GetMapping("/secret/{secretName}")
    public ResponseEntity<Map<String, String>> getSecret(@PathVariable String secretName) {
        Map<String, String> response = new HashMap<>();
        String secretValue = keyVaultService.getSecret(secretName);

        if (secretValue != null) {
            response.put("secretName", secretName);
            response.put("secretValue", maskSecretValue(secretValue));
            response.put("status", "found");
        } else {
            response.put("secretName", secretName);
            response.put("status", "not found");
        }

        return ResponseEntity.ok(response);
    }

    // Helper methods to mask sensitive data
    private String maskPassword(String password) {
        return password != null && password.length() > 2 ?
                password.substring(0, 2) + "****" : "****";
    }

    private String maskConnectionString(String connectionString) {
        return connectionString != null ?
                connectionString.replaceAll("password=[^;]+", "password=****") : "****";
    }

    private String maskApiKey(String apiKey) {
        return apiKey != null && apiKey.length() > 4 ?
                apiKey.substring(0, 4) + "****" : "****";
    }

    private String maskSecretValue(String value) {
        return value != null && value.length() > 4 ?
                value.substring(0, 4) + "****" : "****";
    }
}