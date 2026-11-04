package com.campushub.submissions;

import com.campushub.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Evidence file endpoints on a submission (API_CONTRACT §10, MVP subset). */
@RestController
public class SubmissionFileController {

    private final SubmissionFileService files;

    public SubmissionFileController(SubmissionFileService files) {
        this.files = files;
    }

    @GetMapping("/submissions/{id}/files")
    public List<SubmissionFileResponse> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return files.list(caller, id);
    }

    @PostMapping(value = "/submissions/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionFileResponse upload(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                         @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return files.upload(caller, id, file, request);
    }

    /** Streams the stored bytes as an attachment. The storage key is never exposed. */
    @GetMapping("/submissions/{id}/files/{fileId}/content")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable UUID id, @PathVariable UUID fileId,
                                                        HttpServletRequest request) {
        SubmissionFileService.Download download = files.download(caller, id, fileId, request);
        SubmissionFileResponse file = download.file();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.content()));
    }
}
