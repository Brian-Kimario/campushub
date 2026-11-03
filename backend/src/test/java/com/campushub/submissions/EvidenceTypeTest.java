package com.campushub.submissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EvidenceTypeTest {

    @Test
    void detectsEachAllowedTypeFromItsLeadingBytes() {
        assertThat(EvidenceType.detect("%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII))).contains(EvidenceType.PDF);
        assertThat(EvidenceType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(EvidenceType.PNG);
        assertThat(EvidenceType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1}))
                .contains(EvidenceType.JPEG);
    }

    @Test
    void refusesContentWithoutAKnownSignature() {
        assertThat(EvidenceType.detect("<html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(EvidenceType.detect(new byte[0])).isEmpty();
        assertThat(EvidenceType.detect(new byte[] {(byte) 0x89, 'P', 'N'})).isEmpty();
    }

    @Test
    void probeLengthCoversTheLongestSignature() {
        assertThat(EvidenceType.probeLength()).isEqualTo(8);
    }

    @Test
    void nameWithoutExtensionIsAcceptedAndMatchingExtensionIsCaseInsensitive() {
        assertThat(EvidenceType.PDF.accepts("thesis")).isTrue();
        assertThat(EvidenceType.PDF.accepts("THESIS.PDF")).isTrue();
        assertThat(EvidenceType.JPEG.accepts("photo.jpeg")).isTrue();
        assertThat(EvidenceType.JPEG.accepts("photo.JPG")).isTrue();
    }

    @Test
    void nameWithAnotherTypesExtensionIsRefused() {
        assertThat(EvidenceType.PNG.accepts("photo.pdf")).isFalse();
        assertThat(EvidenceType.PDF.accepts("photo.")).isFalse();
    }

    @Test
    void contentTypeFollowsTheDetectedType() {
        assertThat(EvidenceType.PNG.contentType()).isEqualTo("image/png");
        assertThat(EvidenceType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(EvidenceType.PDF.contentType()).isEqualTo("application/pdf");
    }
}
