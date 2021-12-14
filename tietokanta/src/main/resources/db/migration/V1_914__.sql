-- salli materiaali null jos toimepide on KAR (41)

ALTER TABLE pot2_paallystekerros ALTER COLUMN materiaali DROP NOT NULL;

ALTER TABLE pot2_paallystekerros
    ADD CONSTRAINT materiaali_not_null_jos_ei_ole_toimenpide_kar
    CHECK ((toimenpide = 41) OR (materiaali IS NOT NULL));
