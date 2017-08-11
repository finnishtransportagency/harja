-- Taulu häiriöilmoituksille
CREATE TABLE hairioilmoitus (
  id        SERIAL PRIMARY KEY,
  viesti    VARCHAR(1024) NOT NULL,
  pvm       DATE          NOT NULL,
  poistettu BOOLEAN       NOT NULL DEFAULT FALSE
)