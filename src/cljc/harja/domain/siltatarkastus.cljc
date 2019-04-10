(ns harja.domain.siltatarkastus)

(defn siltatarkastuskohteen-nimi
  "Siltatarkastuksessa käytettyjen kohteiden nimet mäpättynä järjestysnumeroon"
  [kohdenro]
  (case kohdenro
    ;; Alusrakenne
    1 "Maatukien siisteys ja kunto"
    2 "Välitukien siisteys ja kunto"
    3 "Laakeritasojen siisteys ja kunto"
    ;; Päällysrakenne
    4 "Kansilaatta"
    5 "Päällysteen kunto"
    6 "Reunapalkin siisteys ja kunto"
    7 "Reunapalkin liikuntasauma"
    8 "Reunapalkin ja päälllysteen välisen sauman siisteys ja kunto"
    9 "Sillanpäiden saumat"
    10 "Sillan ja penkereen raja"
    ;; Varusteet ja laitteet
    11 "Kaiteiden ja suojaverkkojen vauriot"
    12 "Liikuntasaumalaitteiden siisteys ja kunto"
    13 "Laakerit"
    14 "Syöksytorvet"
    15 "Tippuputket"
    16 "Kosketussuojat ja niiden kiinnitykset"
    17 "Valaistuslaitteet"
    18 "Johdot ja kaapelit"
    19 "Liikennemerkit"
    ;; Siltapaikan rakenteet
    20 "Kuivatuslaitteiden siisteys ja kunto"
    21 "Etuluiskien siisteys ja kunto"
    22 "Keilojen siisteys ja kunto"
    23 "Tieluiskien siisteys ja kunto"
    24 "Portaiden siisteys ja kunto"
    "Tuntematon tarkastuskohde"))
