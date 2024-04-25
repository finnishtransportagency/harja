-- Kuvaus: Lisää toteumalle hash, jonka avulla voidaan päätellä, että onko sama identtinen toteuma (json) lähetetty meille aiemmin
ALTER TABLE toteuma
    ADD COLUMN json_hash TEXT;

CREATE INDEX idx_toteuma_json_hash ON toteuma (json_hash);
CREATE INDEX idx_toteuma_191001_200701_json_hash ON toteuma_191001_200701 (json_hash);
CREATE INDEX idx_toteuma_200701_210101_json_hash ON toteuma_200701_210101 (json_hash);
CREATE INDEX idx_toteuma_210101_210701_json_hash ON toteuma_210101_210701 (json_hash);
CREATE INDEX idx_toteuma_210701_220101_json_hash ON toteuma_210701_220101 (json_hash);
CREATE INDEX idx_toteuma_220101_220701_json_hash ON toteuma_220101_220701 (json_hash);
CREATE INDEX idx_toteuma_220701_230101_json_hash ON toteuma_220701_230101 (json_hash);
CREATE INDEX idx_toteuma_230101_230701_json_hash ON toteuma_230101_230701 (json_hash);
CREATE INDEX idx_toteuma_230701_240101_json_hash ON toteuma_230701_240101 (json_hash);
CREATE INDEX idx_toteuma_240101_240701_json_hash ON toteuma_240101_240701 (json_hash);
CREATE INDEX idx_toteuma_240701_250101_json_hash ON toteuma_240701_250101 (json_hash);
CREATE INDEX idx_toteuma_250101_991231_json_hash ON toteuma_250101_991231 (json_hash);


