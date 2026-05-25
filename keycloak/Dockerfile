# Custom Keycloak image with Aurora MES SPI and realm baked in.
# Build requires the SPI fat JAR to be pre-built by Gradle:
#   ./gradlew :libs:lib-keycloak-audit-spi:jar --no-daemon
# The publish.yml workflow handles this automatically.
FROM quay.io/keycloak/keycloak:25.0.6
COPY libs/lib-keycloak-audit-spi/build/libs/keycloak-spi.jar /opt/keycloak/providers/lib-keycloak-audit-spi.jar
COPY keycloak/mes-realm.json /opt/keycloak/data/import/mes-realm.json
