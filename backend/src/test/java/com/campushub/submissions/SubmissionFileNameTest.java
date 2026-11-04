package com.campushub.submissions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmissionFileNameTest {

    @Test
    void keepsOnlyTheLastPathSegment() {
        assertThat(SubmissionFileService.sanitizeName("../../etc/report.pdf")).isEqualTo("report.pdf");
        assertThat(SubmissionFileService.sanitizeName("C:\\Users\\me\\thesis.pdf")).isEqualTo("thesis.pdf");
    }

    @Test
    void stripsControlCharactersAndFallsBackWhenNothingRemains() {
        assertThat(SubmissionFileService.sanitizeName("re\u0000port.pdf\n")).isEqualTo("report.pdf");
        assertThat(SubmissionFileService.sanitizeName(null)).isEqualTo("file");
        assertThat(SubmissionFileService.sanitizeName("   ")).isEqualTo("file");
        assertThat(SubmissionFileService.sanitizeName("dir/")).isEqualTo("file");
    }

    @Test
    void capsTheLengthAtTheColumnWidth() {
        String long300 = "a".repeat(296) + ".pdf";
        String name = SubmissionFileService.sanitizeName("x/" + long300 + "z".repeat(10));
        assertThat(name).hasSize(255);
    }
}
