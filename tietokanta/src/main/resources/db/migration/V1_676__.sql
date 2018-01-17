-- Lisää aikataulun toimenpiteitä
CREATE TYPE yllapitokohteen_aikataulu_toimenpide AS ENUM ('ojankaivuu', 'rp_tyot', 'rumpujen_vaihto', 'muu');

ALTER TYPE yllapitokohteen_aikataulu_toimenpide ADD VALUE 'sekoitusjyrsinta';
ALTER TYPE yllapitokohteen_aikataulu_toimenpide ADD VALUE 'murskeenlisays';