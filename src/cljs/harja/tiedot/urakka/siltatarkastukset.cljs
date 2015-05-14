(ns harja.tiedot.urakka.siltatarkastukset
  "Tämä nimiavaruus hallinnoi urakan siltatarkastuksien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-sillat [urakka-id]
  (k/post! :hae-urakan-sillat urakka-id))

(defn hae-sillan-tarkastukset [silta-id]
  (k/post! :hae-sillan-tarkastukset silta-id))

(defn hae-siltatarkastusten-kohteet [siltatarkastus-idt]
  (k/post! :hae-siltatarkastusten-kohteet siltatarkastus-idt))

(defn paivita-siltatarkastuksen-kohteet!
  [urakka-id siltatarkastus-id kohderivit]
  (k/post! :paivita-siltatarkastuksen-kohteet {:urakka-id urakka-id
                                               :siltatarkastus-id siltatarkastus-id
                                               :kohderivit kohderivit}))

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
    12 "Liikuntasaumakaitteiden siisteys ja kunto"
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

