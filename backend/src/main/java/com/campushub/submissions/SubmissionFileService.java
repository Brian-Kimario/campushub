package com.campushub.submissions;

import com.campushub.identity.Role;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.infrastructure.storage.FileStorage;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Evidence files on a submission revision (API_CONTRACT §10). Order: admin and invisible project (404),
 * role (403 FORBIDDEN_ROLE), team membership (403 FORBIDDEN_STATE), archived (422), sealed revision
 * (409 VERSION_SEALED), then file checks (413, 422). Files attach only while the revision is SUBMITTED.
 */
@Service
public class SubmissionFileService {

    private static final String SUBMISSION_FILE = "submission_file";
    private static final String NAME_FALLBACK = "file";
    private static final int NAME_LIMIT = 255;

    private final SubmissionRepository submissions;
    private final SubmissionFileRepository files;
    private final ProjectService projects;
    private final FileStorage storage;
    private final AuditService audit;
    private final Clock clock;
    private final long maxFileBytes;
    private final long maxFilesPerRevision;

    public SubmissionFileService(SubmissionRepository submissions, SubmissionFileRepository files,
                                 ProjectService projects, FileStorage storage, AuditService audit, Clock clock,
                                 @Value("${campushub.upload.max-file-bytes}") long maxFileBytes,
                                 @Value("${campushub.upload.max-files-per-version}") long maxFilesPerRevision) {
        this.submissions = submissions;
        this.files = files;
        this.projects = projects;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
        this.maxFileBytes = maxFileBytes;
        this.maxFilesPerRevision = maxFilesPerRevision;
    }

    /** A file's metadata, and the open stream when the caller asked to download it. */
    public record Download(SubmissionFileResponse file, InputStream content) {
    }

    @Transactional(readOnly = true)
    public List<SubmissionFileResponse> list(AuthenticatedUser caller, UUID submissionId) {
        Submission submission = findSubmission(submissionId);
        visibleTo(caller, submission);
        return files.findBySubmissionIdOrderByUploadedAtAsc(submissionId).stream()
                .map(SubmissionFileService::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionFileResponse upload(AuthenticatedUser caller, UUID submissionId, MultipartFile upload,
                                         HttpServletRequest request) {
        Submission locked = submissions.lockById(submissionId).orElseThrow(SubmissionFileService::notFound);
        Project project = visibleTo(caller, locked);
        if (caller.role() != Role.STUDENT) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Only students on the team can upload evidence.");
        }
        if (!projects.canWriteTeamWork(caller, project)) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner or a team member can upload evidence.");
        }
        ProjectService.requireNotArchived(project);
        if (locked.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.VERSION_SEALED, "Files can be added only while the submission is waiting for review.");
        }
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a file to upload.");
        }
        if (upload.getSize() > maxFileBytes) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, "The file is larger than the allowed size.");
        }
        if (files.countBySubmissionId(submissionId) >= maxFilesPerRevision) {
            throw new ApiException(ErrorCode.FILE_LIMIT_EXCEEDED, "This submission already has the maximum number of files.");
        }
        String name = sanitizeName(upload.getOriginalFilename());
        try (BufferedInputStream in = new BufferedInputStream(upload.getInputStream())) {
            in.mark(EvidenceType.probeLength());
            EvidenceType type = EvidenceType.detect(in.readNBytes(EvidenceType.probeLength()))
                    .filter(detected -> detected.accepts(name))
                    .orElseThrow(() -> new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                            "Only PDF, PNG and JPEG files are allowed, and the extension must match the content."));
            in.reset();
            FileStorage.Stored stored = storage.write(in);
            if (stored.sizeBytes() > maxFileBytes) {
                storage.delete(stored.key());
                throw new ApiException(ErrorCode.FILE_TOO_LARGE, "The file is larger than the allowed size.");
            }
            return persist(caller, locked, name, type, stored, request);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "File storage is unavailable. Try again later.");
        }
    }

    /** Not read-only: the download is audited. */
    @Transactional
    public Download download(AuthenticatedUser caller, UUID submissionId, UUID fileId, HttpServletRequest request) {
        Submission submission = findSubmission(submissionId);
        visibleTo(caller, submission);
        SubmissionFile row = files.findById(fileId)
                .filter(file -> file.getSubmissionId().equals(submissionId))
                .orElseThrow(SubmissionFileService::notFound);
        audit.record("submission_file.downloaded", caller.userId(), SUBMISSION_FILE, fileId.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        try {
            return new Download(toResponse(row), storage.open(row.getStorageKey()));
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "File storage is unavailable. Try again later.");
        }
    }

    private SubmissionFileResponse persist(AuthenticatedUser caller, Submission submission, String name,
                                           EvidenceType type, FileStorage.Stored stored, HttpServletRequest request) {
        SubmissionFile row = new SubmissionFile(UUID.randomUUID(), submission.getId(), name, type.contentType(),
                stored.sizeBytes(), stored.sha256(), stored.key(), caller.userId(), clock.instant());
        try {
            files.saveAndFlush(row);
        } catch (RuntimeException ex) {
            storage.delete(stored.key());
            throw ex;
        }
        audit.record("submission_file.uploaded", caller.userId(), SUBMISSION_FILE, row.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of("submissionId", submission.getId().toString()));
        return toResponse(row);
    }

    private Submission findSubmission(UUID id) {
        return submissions.findById(id).orElseThrow(SubmissionFileService::notFound);
    }

    /**
     * Admins are concealed from evidence files (ADR-007), and a hidden project looks like a missing
     * submission. Returns the project when the caller can see it.
     */
    private Project visibleTo(AuthenticatedUser caller, Submission submission) {
        if (caller.role() == Role.ADMIN) {
            throw notFound();
        }
        return projects.findVisible(caller, submission.getProjectId()).orElseThrow(SubmissionFileService::notFound);
    }

    /** Keeps the last path segment only, strips control characters, and falls back to a neutral name. */
    static String sanitizeName(String raw) {
        if (raw == null) {
            return NAME_FALLBACK;
        }
        String base = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (base.isEmpty()) {
            return NAME_FALLBACK;
        }
        return base.length() > NAME_LIMIT ? base.substring(base.length() - NAME_LIMIT) : base;
    }

    private static SubmissionFileResponse toResponse(SubmissionFile file) {
        return new SubmissionFileResponse(file.getId(), file.getSubmissionId(), file.getOriginalName(),
                file.getContentType(), file.getSizeBytes(), file.getSha256(), file.getUploadedBy(),
                file.getUploadedAt());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No file was found.");
    }
}
