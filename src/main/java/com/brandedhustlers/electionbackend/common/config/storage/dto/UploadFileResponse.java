package com.brandedhustlers.electionbackend.common.config.storage.dto;

public record UploadFileResponse(
        String fileName,
        String key,
        String contentType,
        String etag,
        Long size,
        String versionId
) {
}
