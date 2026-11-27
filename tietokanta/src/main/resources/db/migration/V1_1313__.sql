-- Lisätään uusien vesiväyläurakoiden yksikköhintaisiin töihin mahdollisuus merkitä, onko työn
-- yksikköhinta indeksiin sidottu vai ei, sekä indeksikorjatun yksikköhinnan tallennus.
ALTER TABLE yksikkohintainen_tyo
    ADD COLUMN indeksi_kaytossa BOOLEAN,
    ADD COLUMN indeksikorjattu_yksikkohinta NUMERIC;
