package com.brandedhustlers.electionbackend.common.config.Fabric;

import org.hyperledger.fabric.gateway.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Configuration
public class FabricConfig {

    private static final Logger logger = LoggerFactory.getLogger(FabricConfig.class);

    @Value("${fabric.connectionProfile}")
    Resource profile;

    @Value("${fabric.mspId}")
    String mspId;

    @Value("${fabric.certPath}")
    private Resource certRes;

    @Value("${fabric.keyPath}")
    private Resource keyRes;

    @Value("${fabric.channelName}")
    String channel;

    @Value("${fabric.chaincodeName}")
    String chaincode;

    @Bean(destroyMethod = "close")
    public Gateway gateway() throws Exception {
        logger.info("🔧 Initializing Fabric Gateway...");

        // Load certificate
        X509Certificate cert;
        try (InputStream in = certRes.getInputStream()) {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
            logger.info("Certificate loaded successfully");
        }

        // Load private key
        String keyPem;
        try (InputStream in = keyRes.getInputStream()) {
            keyPem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Private key loaded successfully");
        }
        PrivateKey privateKey = Identities.readPrivateKey(new StringReader(keyPem));

        // Create identity
        Identity id = Identities.newX509Identity(mspId, cert, privateKey);
        Wallet wallet = Wallets.newInMemoryWallet();
        wallet.put("appUser", id);
        logger.info("Identity created for MSP: {}", mspId);

        // Build gateway with proper configuration
        Gateway.Builder builder = Gateway.createBuilder()
                .identity(wallet, "appUser")
                .discovery(true)
                .commitTimeout(30, TimeUnit.SECONDS);

        try (InputStream in = profile.getInputStream()) {
            builder.networkConfig(in);
            logger.info("Network config loaded from connection profile");
        }

        Gateway gw = builder.connect();
        logger.info("Gateway connected successfully to Fabric network!");

        return gw;
    }

    @Bean
    public Contract anchorContract(Gateway gw) {
        logger.info("Loading contract '{}' from channel '{}'", chaincode, channel);

        try {
            Network network = gw.getNetwork(channel);
            logger.info("Network '{}' retrieved", channel);

            Contract contract = network.getContract(chaincode);
            logger.info("Contract '{}' loaded successfully!", chaincode);

            return contract;
        } catch (Exception e) {
            logger.error("Failed to load contract: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Fabric contract", e);
        }
    }
}