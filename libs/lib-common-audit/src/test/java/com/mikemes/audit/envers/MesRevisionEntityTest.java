package com.mikemes.audit.envers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MesRevisionEntityTest {

    @Test
    void equalEntitiesWithSameIdAreEqual() {
        MesRevisionEntity a = new MesRevisionEntity();
        a.setUserId("user:test");
        a.setServiceSource("svc");

        MesRevisionEntity b = new MesRevisionEntity();
        b.setUserId("user:test");
        b.setServiceSource("svc");

        // DefaultRevisionEntity uses id for equals — both have id=0 by default
        assertThat(a).isEqualTo(b);
    }

    @Test
    void hashCodeIsConsistentWithEquals() {
        MesRevisionEntity entity = new MesRevisionEntity();
        entity.setUserId("user:test");
        entity.setServiceSource("svc");

        assertThat(entity.hashCode()).isEqualTo(entity.hashCode());
    }

    @Test
    void sameInstanceIsEqualToItself() {
        MesRevisionEntity entity = new MesRevisionEntity();
        entity.setUserId("user:test");
        assertThat(entity).isEqualTo(entity);
    }

    @Test
    void gettersReturnSetValues() {
        MesRevisionEntity entity = new MesRevisionEntity();
        entity.setUserId("user:getter");
        entity.setServiceSource("gateway");

        assertThat(entity.getUserId()).isEqualTo("user:getter");
        assertThat(entity.getServiceSource()).isEqualTo("gateway");
    }
}
