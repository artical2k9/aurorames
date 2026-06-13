package com.mes.engineering.workinstruction.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitiserTest {

    @Test
    void stripsScriptTags() {
        String dirty = "<p>Tighten bolt</p><script>alert('xss')</script>";
        String clean = HtmlSanitiser.sanitise(dirty);
        assertThat(clean).doesNotContain("<script>").doesNotContain("alert");
        assertThat(clean).contains("Tighten bolt");
    }

    @Test
    void stripsEventHandlersAndJavascriptUrls() {
        String dirty = "<a href=\"javascript:steal()\" onclick=\"evil()\">link</a>";
        String clean = HtmlSanitiser.sanitise(dirty);
        assertThat(clean).doesNotContain("javascript:").doesNotContain("onclick");
    }

    @Test
    void preservesAllowedFormatting() {
        String input = "<h2>Step</h2><p><b>Bold</b> and <i>italic</i></p>"
                + "<ul><li>one</li><li>two</li></ul>";
        String clean = HtmlSanitiser.sanitise(input);
        assertThat(clean).contains("<h2>").contains("<b>").contains("<i>")
                .contains("<ul>").contains("<li>");
    }

    @Test
    void nullInNullOut() {
        assertThat(HtmlSanitiser.sanitise(null)).isNull();
    }
}
