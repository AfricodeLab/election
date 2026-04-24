
CREATE TABLE auth.organization
(
    organization_id          UUID         NOT NULL,
    organization_name        VARCHAR(255) NOT NULL,
    organization_description TEXT,
    is_enabled               BOOLEAN      NOT NULL,
    created_at               TIMESTAMP WITHOUT TIME ZONE,
    updated_at               TIMESTAMP WITHOUT TIME ZONE,
    created_by_app_user_id   UUID,
    delete_yn                VARCHAR(255),
    CONSTRAINT pk_organization PRIMARY KEY (organization_id)
);


ALTER TABLE auth.organization
    ADD CONSTRAINT uc_organization_organizationname UNIQUE (organization_name);

ALTER TABLE auth.organization
    ADD CONSTRAINT FK_ORGANIZATION_ON_CREATEDBY_APPUSERID FOREIGN KEY (created_by_app_user_id) REFERENCES auth.app_user (app_user_id);
