CREATE TABLE paallysteen_korjausluokka
(
    tie           INTEGER,
    aosa          INTEGER,
    aet           INTEGER,
    losa          INTEGER,
    let           INTEGER,
    korjausluokka TEXT,
    paivitetty    TIMESTAMP
);

CREATE INDEX paallysteen_korjausluokka_tie ON paallysteen_korjausluokka (tie);
