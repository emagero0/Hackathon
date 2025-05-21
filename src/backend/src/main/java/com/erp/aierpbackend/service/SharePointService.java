package com.erp.aierpbackend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
// import java.net.MalformedURLException; // Not strictly needed if using URI first
import java.net.URI;
import java.net.URISyntaxException;
// import java.net.URL; // Not strictly needed if using URI first
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SharePointService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;

    private String cachedAccessToken;
    private long tokenExpirationTime;

    private static final String GRAPH_API_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String SITES_PREFIX = "/sites/";


    @Autowired
    public SharePointService(
            WebClient.Builder webClientBuilder,
            @Value("${sharepoint.client.id}") String clientId,
            @Value("${sharepoint.client.secret}") String clientSecret,
            @Value("${sharepoint.tenant.id}") String tenantId) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        log.info("SharePointService initialized with client ID: {}, tenant ID: {}", clientId, tenantId);
    }

    public Mono<String> getAccessToken() {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpirationTime) {
            log.debug("Using cached access token");
            return Mono.just(cachedAccessToken);
        }
        log.info("Requesting new access token for Microsoft Graph API using tenant-specific endpoint");
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        log.debug("Token request details - URL: {}, Client ID: {}, Tenant ID: {}", tokenUrl, clientId, tenantId);
        String scope = "https://graph.microsoft.com/.default";
        log.debug("Request body: client_id={}, scope={}, grant_type=client_credentials", clientId, scope);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("scope", scope)
                        .with("client_secret", clientSecret)
                        .with("grant_type", "client_credentials"))
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(TokenResponse.class)
                                .map(tokenResponse -> {
                                    cachedAccessToken = tokenResponse.accessToken;
                                    int expiresIn = tokenResponse.expiresIn;
                                    tokenExpirationTime = System.currentTimeMillis() + ((expiresIn - 300) * 1000L); // 5 min buffer
                                    log.info("Successfully obtained Microsoft Graph API access token from tenant-specific endpoint, valid for {} seconds", expiresIn);
                                    return cachedAccessToken;
                                });
                    } else {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Token request failed with status: {}, Error: {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Failed to obtain access token: " + response.statusCode() + " - " + errorBody));
                                });
                    }
                })
                .doOnError(e -> log.error("Error obtaining Microsoft Graph API access token: {}", e.getMessage(), e));
    }

    public Mono<byte[]> downloadFile(String sharepointUrlString) {
        log.info("Attempting to download from SharePoint URL (using robust multi-step): {}", sharepointUrlString);

        // Special handling for job J069026
        if (sharepointUrlString != null && sharepointUrlString.contains("J069026")) {
            log.error("SPECIAL DIAGNOSTIC: Attempting to download file for problematic job J069026 from URL: {}", sharepointUrlString);
        }

        return downloadFileFromSharePointUrl(sharepointUrlString)
            .doOnSuccess(data -> {
                // Special handling for job J069026
                if (sharepointUrlString != null && sharepointUrlString.contains("J069026")) {
                    log.error("SPECIAL DIAGNOSTIC: Successfully downloaded file for problematic job J069026, size: {} bytes",
                            data != null ? data.length : 0);
                }
            })
            .onErrorResume(e -> {
                log.warn("Robust download approach (downloadFileFromSharePointUrl) failed for URL {}: {}. No further fallbacks implemented in this path.", sharepointUrlString, e.getMessage(), e);

                // Special handling for job J069026
                if (sharepointUrlString != null && sharepointUrlString.contains("J069026")) {
                    log.error("SPECIAL DIAGNOSTIC: Failed to download file for problematic job J069026: {}", e.getMessage(), e);
                }

                return Mono.error(new IOException("All download attempts failed for " + sharepointUrlString, e));
            });
    }

    private Mono<String> getSiteIdByPath(String hostname, String serverRelativePath) {
        String siteIdentifier = hostname + ":" + serverRelativePath;
        String graphApiSiteUrl = GRAPH_API_BASE_URL + SITES_PREFIX + siteIdentifier;
        log.info("Getting site ID using Graph API URL: {}", graphApiSiteUrl);

        return getAccessToken()
            .flatMap(token -> {
                URI uri;
                try {
                    uri = new URI(graphApiSiteUrl);
                } catch (URISyntaxException e) {
                    log.error("URISyntaxException constructing site ID URI for {}: {}", graphApiSiteUrl, e.getMessage(), e);
                    return Mono.error(new IOException("Invalid URI syntax for site ID request: " + graphApiSiteUrl, e));
                }
                log.debug("Site ID URI (ASCII): {}", uri.toASCIIString());

                return webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Error getting site ID from Graph API. Status: {}, Body: {}", clientResponse.statusCode(), errorBody);
                            return Mono.error(new WebClientResponseException(
                                    "Error getting site ID: " + clientResponse.statusCode() + " " + errorBody,
                                    clientResponse.statusCode().value(), errorBody,
                                    clientResponse.headers().asHttpHeaders(), null, null));
                        }))
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        if (response != null && response.has("id")) {
                            String siteId = response.get("id").asText();
                            log.info("Retrieved site ID: {}", siteId);
                            return siteId;
                        } else {
                            log.error("Site ID field 'id' not found in Graph API response: {}", response);
                            throw new RuntimeException("Site ID not found in response for " + graphApiSiteUrl);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error in getSiteIdByPath for {}: {}", graphApiSiteUrl, e.getMessage(), e);
                        return Mono.error(new IOException("Failed to retrieve site ID for " + graphApiSiteUrl, e));
                    });
            });
    }

    private Mono<String> getDriveIdByName(String siteId, String driveName) {
        String drivesUrl = String.format("%s/sites/%s/drives", GRAPH_API_BASE_URL, siteId);
        log.info("Getting drives for site ID: {}, looking for drive name: '{}'", siteId, driveName);

        return getAccessToken()
            .flatMap(token -> {
                URI uri = URI.create(drivesUrl);
                log.debug("Get Drives URI: {}", uri.toASCIIString());

                return webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Error getting drives list. Status: {}, Body: {}", clientResponse.statusCode(), errorBody);
                            return Mono.error(new WebClientResponseException(
                                    "Error getting drives list: " + clientResponse.statusCode() + " " + errorBody,
                                    clientResponse.statusCode().value(), errorBody,
                                    clientResponse.headers().asHttpHeaders(), null, null));
                        }))
                    .bodyToMono(JsonNode.class)
                    .flatMap(response -> {
                        if (response != null && response.has("value") && response.get("value").isArray()) {
                            for (JsonNode driveNode : response.get("value")) {
                                if (driveNode.has("name") && driveNode.get("name").asText().equalsIgnoreCase(driveName)) {
                                    if (driveNode.has("id")) {
                                        String driveId = driveNode.get("id").asText();
                                        log.info("Found drive ID for '{}': {}", driveName, driveId);
                                        return Mono.just(driveId);
                                    }
                                }
                            }
                            log.warn("Drive with name '{}' not found in site '{}'", driveName, siteId);
                            return Mono.empty(); // Drive not found
                        } else {
                            log.error("No 'value' array or invalid format in drives response: {}", response);
                            return Mono.empty();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error in getDriveIdByName for site {} and drive {}: {}",
                                siteId, driveName, e.getMessage(), e);
                        return Mono.error(new IOException("Failed to retrieve drive ID for " + driveName, e));
                    });
            });
    }

    private Mono<byte[]> downloadFileFromSharePointUrl(String sharepointUrlString) {
        log.info("Attempting to download (robust method) from SharePoint URL: {}", sharepointUrlString);
        try {
            URI sharepointUri = new URI(sharepointUrlString);
            String hostname = sharepointUri.getHost();
            String path = sharepointUri.getPath(); // URI.getPath() returns decoded path segments

            log.debug("Parsed SharePoint URL - Hostname: {}, Path: {}", hostname, path);

            String siteServerRelativePath;
            String pathWithinSite;

            int sitesPathIndex = path.indexOf(SITES_PREFIX);
            if (sitesPathIndex == -1) {
                log.error("SharePoint URL does not contain the expected '{}' structure: {}", SITES_PREFIX, sharepointUrlString);
                return Mono.error(new IOException("URL does not contain '" + SITES_PREFIX + "' pattern: " + sharepointUrlString));
            }

            // Find the end of the site collection name (the next '/' after "/sites/").
            // For "/sites/DynamicsBusinessCentral/DocumentLinks/...", endOfSiteCollectionName will be the index of "/" before "DocumentLinks".
            int endOfSiteCollectionName = path.indexOf('/', sitesPathIndex + SITES_PREFIX.length());
            if (endOfSiteCollectionName == -1) {
                // This case implies the path is just "/sites/SiteName", which is unusual for a file URL.
                siteServerRelativePath = path; // The whole path is the site path
                pathWithinSite = ""; // No further path segments within the site for drive/item
                log.warn("URL structure suggests file might be at site root or path is incomplete: {}. Site path: {}, Path within site: (empty)", sharepointUrlString, siteServerRelativePath);
                return Mono.error(new IOException("Cannot determine path segments within site for URL: " + sharepointUrlString));
            } else {
                siteServerRelativePath = path.substring(0, endOfSiteCollectionName); // e.g., "/sites/DynamicsBusinessCentral"
                pathWithinSite = path.substring(endOfSiteCollectionName);          // e.g., "/DocumentLinks/KENYA/Sales quote_130.pdf"
            }
            log.debug("Extracted site server-relative path: {}, Path within site: {}", siteServerRelativePath, pathWithinSite);

            return getSiteIdByPath(hostname, siteServerRelativePath)
                .switchIfEmpty(Mono.<String>error(new IOException("Could not retrieve site ID for " + hostname + siteServerRelativePath)))
                .flatMap(siteId -> {
                    String[] pathSegments = pathWithinSite.startsWith("/") ?
                            pathWithinSite.substring(1).split("/") : // Remove leading slash before split
                            pathWithinSite.split("/");

                    if (pathSegments.length == 0 || pathSegments[0].isEmpty()) {
                        return Mono.error(new IOException("Path within site is empty or invalid after splitting: " + pathWithinSite));
                    }
                    if (pathSegments.length < 2) { // Need at least DriveName and FileName
                         return Mono.error(new IOException("Cannot determine drive name and item path from path segments for: " + pathWithinSite));
                    }

                    String targetDriveNameFromPath = pathSegments[0]; // e.g., "DocumentLinks" (already decoded by URI.getPath())
                    log.debug("Target drive name from path (decoded): {}", targetDriveNameFromPath);

                    return getDriveIdByName(siteId, targetDriveNameFromPath)
                        .switchIfEmpty(Mono.<String>error(new IOException("Could not retrieve drive ID for drive '" +
                                targetDriveNameFromPath + "' in site " + siteId)))
                        .flatMap(targetDriveId -> {
                            String itemPathInDrive = Arrays.stream(pathSegments)
                                    .skip(1) // Skip the drive name itself
                                    .map(segment -> {
                                        try {
                                            return URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                                                    .replace("+", "%20"); // URLEncoder encodes space as +, Graph API path expects %20
                                        } catch (UnsupportedEncodingException e) {
                                            log.warn("Encoding error for segment '{}': {}", segment, e.getMessage());
                                            return segment; // Fallback: use segment as is, might cause issues
                                        }
                                    })
                                    .collect(Collectors.joining("/"));
                            log.debug("Item path within drive (re-encoded for Graph): {}", itemPathInDrive);

                            return getAccessToken()
                                .flatMap(accessToken -> {
                                    String graphApiContentUrlString = String.format(
                                            "%s/sites/%s/drives/%s/root:/%s:/content",
                                            GRAPH_API_BASE_URL, siteId, targetDriveId, itemPathInDrive
                                    );
                                    log.info("Graph API URL String for content: {}", graphApiContentUrlString);
                                    URI graphApiUri = URI.create(graphApiContentUrlString);
                                    log.info("URI object for content passed to WebClient: {}", graphApiUri.toASCIIString());

                                    return webClient.get()
                                        .uri(graphApiUri)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                        .exchangeToMono(clientResponse -> { // Use exchangeToMono to handle 302
                                            if (clientResponse.statusCode().is3xxRedirection()) {
                                                String redirectUrl = clientResponse.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
                                                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                                                    log.info("Graph API returned {}, following redirect to: {}", clientResponse.statusCode(), redirectUrl);
                                                    return webClient.get()
                                                            .uri(URI.create(redirectUrl)) // Download from the pre-signed URL
                                                            // No Authorization header usually needed for pre-signed @microsoft.graph.downloadUrl
                                                            .retrieve()
                                                            .bodyToMono(byte[].class);
                                                } else {
                                                    log.error("Graph API returned {} without a Location header.", clientResponse.statusCode());
                                                    return Mono.error(new IOException("Graph API returned redirect without Location header."));
                                                }
                                            } else if (clientResponse.statusCode().isError()) {
                                                return clientResponse.bodyToMono(String.class)
                                                    .flatMap(errorBody -> {
                                                        log.error("Error downloading file content from Graph (initial call). Status: {}, Body: {}", clientResponse.statusCode(), errorBody);
                                                        return Mono.error(new WebClientResponseException(
                                                                "Error downloading file content from Graph (initial call): " + clientResponse.statusCode(),
                                                                clientResponse.statusCode().value(), errorBody,
                                                                clientResponse.headers().asHttpHeaders(), null, null));
                                                    });
                                            }
                                            // Successful 2xx response from the initial /content call (less common but possible for small files)
                                            return clientResponse.bodyToMono(byte[].class);
                                        });
                                })
                                .timeout(Duration.ofSeconds(60)) // Timeout for the entire sequence (token + graph call(s))
                                .doOnSuccess(data -> {
                                    if (data != null) { // Should have data if successful
                                        log.info("Successfully downloaded file from Graph API, final size: {} bytes", ((byte[])data).length);
                                    } else {
                                        // This condition should ideally not be hit if 302s are handled correctly or errors are thrown
                                        log.warn("Downloaded data is null after Graph API content call (unexpected).");
                                    }
                                })
                                .onErrorResume(e -> {
                                    log.error("Error in file content download sequence: {}", e.getMessage(), e);
                                    return Mono.error(new IOException("Failed to download file content from Graph API: " + e.getMessage(), e));
                                });
                        });
                });
        } catch (URISyntaxException e) {
            log.error("Invalid SharePoint URL syntax: {}", sharepointUrlString, e);
            return Mono.error(new IOException("Invalid SharePoint URL (URISyntaxException): " + sharepointUrlString, e));
        }
    }

    @Data
    private static class TokenResponse {
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("expires_in")
        private int expiresIn;
        @JsonProperty("ext_expires_in")
        private int extExpiresIn;
        @JsonProperty("access_token")
        private String accessToken;
    }
}