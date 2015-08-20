
-- Erillinen alueurakan geometriataulu, koska geometriat saadaan shapefilestä ja ne voivat elää eri elämää Sampon lähettämistä

CREATE TABLE alueurakka (
  alueurakkanro varchar(16) primary key,
  alue geometry
);

  
