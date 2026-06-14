package com.mes.engineering.workinstruction.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaStorageServiceTest {

    private MinioMediaStorageService service() {
        MediaProperties props = new MediaProperties();
        props.setEndpoint("http://localhost:9000");
        props.setAccessKey("minioadmin");
        props.setSecretKey("minioadmin");
        props.setBucket("wi-media");
        return new MinioMediaStorageService(props);
    }

    @Test
    void objectKeyFollowsOrgInstructionAttachmentLayoutWithExtension() {
        UUID org = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID instr = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID att = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String key = service().objectKey(org, instr, att, "photo.PNG");
        assertThat(key).isEqualTo(org + "/" + instr + "/" + att + ".png");
    }

    @Test
    void objectKeyOmitsExtensionWhenFileNameHasNone() {
        UUID org = UUID.randomUUID();
        UUID instr = UUID.randomUUID();
        UUID att = UUID.randomUUID();
        assertThat(service().objectKey(org, instr, att, "rawfile"))
                .isEqualTo(org + "/" + instr + "/" + att);
    }
}
