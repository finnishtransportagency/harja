CREATE TYPE tiemerkinta_merkinta AS ENUM ('massa', 'maali', 'muu');
CREATE TYPE tiemerkinta_jyrsinta AS ENUM ('ei jyrsintää', 'keski', 'reuna', 'keski- ja reuna');

ALTER TABLE yllapitokohteen_aikataulu
 ADD COLUMN merkinta tiemerkinta_merkinta DEFAULT NULL;
ALTER TABLE yllapitokohteen_aikataulu
    ADD COLUMN jyrsinta tiemerkinta_jyrsinta DEFAULT NULL;