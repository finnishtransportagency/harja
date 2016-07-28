-- Lisää ylläpidon urakoita koskevat toimenpidekoodit
INSERT INTO toimenpidekoodi (nimi, koodi, emo, taso, luotu)
VALUES ('Päällystyksen yksikköhintaiset työt', 'PAAL_YKSHINT',
(SELECT id FROM toimenpidekoodi WHERE koodi = '20100'), 3, NOW());

INSERT INTO toimenpidekoodi (nimi, koodi, emo, taso, luotu)
VALUES ('Paikkauksen yksikköhintaiset työt', 'PAIK_YKSHINT',
(SELECT id FROM toimenpidekoodi WHERE koodi = '20100'), 3, NOW());

INSERT INTO toimenpidekoodi (nimi, koodi, emo, taso, luotu)
VALUES ('Tiemerkinnän yksikköhintaiset työt', 'TIEM_YKSHINT',
(SELECT id FROM toimenpidekoodi WHERE koodi = '20120'), 3, NOW());

INSERT INTO toimenpidekoodi (nimi, koodi, emo, taso, luotu)
VALUES ('Valaistuksen yksikköhintaiset työt', 'VALA_YKSHINT',
(SELECT id FROM toimenpidekoodi WHERE koodi = '20170'), 3, NOW());