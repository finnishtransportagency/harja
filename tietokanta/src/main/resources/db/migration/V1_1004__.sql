-- Lisätään tr_osan_ajorataan lisäindeksi, joka nopeuttaa hieman ajoradan etsimistä
create index tr_osan_ajorata_tie_ajorata_index
    on tr_osan_ajorata (tie, ajorata);
