-- Lisätään materiaalikoodi-tauluun yksilöivä tunniste
ALTER TABLE materiaalikoodi
ADD COLUMN yksiloiva_tunniste UUID UNIQUE;
