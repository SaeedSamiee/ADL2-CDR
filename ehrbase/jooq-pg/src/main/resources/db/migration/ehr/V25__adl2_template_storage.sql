-- ADL2 operational template storage alongside legacy ADL 1.4 XML content.
ALTER TABLE template_store
    ADD COLUMN IF NOT EXISTS template_format text NOT NULL DEFAULT 'adl1.4';

ALTER TABLE template_store
    ADD COLUMN IF NOT EXISTS opt_adl2_json text;

COMMENT ON COLUMN template_store.template_format IS 'Template wire format: adl1.4 or adl2';
COMMENT ON COLUMN template_store.opt_adl2_json IS 'Archie ADL2 operational template JSON when template_format = adl2';
