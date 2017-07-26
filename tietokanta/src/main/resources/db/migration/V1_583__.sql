-- Lisää sillan päivämäärät

ALTER TABLE silta
  ADD alkupvm DATE,
  ADD muutospvm DATE,
  ADD loppupvm DATE;
