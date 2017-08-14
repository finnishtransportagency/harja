-- Taulu häiriöilmoituksille
CREATE TABLE hairioilmoitus (
  id        SERIAL PRIMARY KEY,
  viesti    VARCHAR(1024) NOT NULL,
  pvm       TIMESTAMP     NOT NULL,
  voimassa  BOOLEAN       NOT NULL DEFAULT FALSE
)