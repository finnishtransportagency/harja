ALTER TABLE vakiohavainto ADD COLUMN avain VARCHAR(64);

UPDATE vakiohavainto v SET
  avain = c.avain
FROM (VALUES
   (1, 'liukasta'),
   (2, 'tasauspuute'),
   (3, 'lumista'),
   (4, 'liikennemerkki-luminen'),
   (5, 'pysakilla-epatasainen-polanne'),
   (6, 'aurausvalli'),
   (7, 'sulamisvesihaittoja'),
   (8, 'polanteessa-jyrkat-urat'),
   (9, 'hiekoittamatta'),
   (10, 'pysakki-auraamatta'),
   (11, 'pysakki-hiekoittamatta'),
   (12, 'pl-epatasainen-polanne'),
   (13, 'pl-alue-auraamatta'),
   (14, 'pl-alue-hiekoittamatta'),
   (15, 'sohjoa'),
   (16, 'irtolunta'),
   (17, 'lumikielekkeita')) AS c(id,avain)
WHERE c.id = v.id;
