CREATE TYPE kuittausvaroitus AS ENUM ('varoitus', 'halytys');
ALTER TABLE ilmoitustoimenpide ADD COLUMN myohastymisvaroitus kuittausvaroitus;
