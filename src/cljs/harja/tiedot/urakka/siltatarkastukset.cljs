(ns harja.tiedot.urakka.siltatarkastukset
  "Tämä nimiavaruus hallinnoi urakan siltatarkastuksien tietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio :as nav]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defn hae-sillan-tarkastukset [silta-id]
  (k/post! :hae-sillan-tarkastukset silta-id))

(defn hae-siltatarkastusten-kohteet [siltatarkastus-idt]
  (k/post! :hae-siltatarkastusten-kohteet siltatarkastus-idt))

(defn tallenna-siltatarkastus!
  [siltatarkastus]
  (log "tiedot, tallenna-siltatarkastus" (pr-str siltatarkastus))
  (k/post! :tallenna-siltatarkastus siltatarkastus))

(defn poista-siltatarkastus! [silta tarkastus]
  "Merkitsee annetun sillantarkastuksen poistetuksi"
  (log "tiedot poista-st!" silta tarkastus)
  (k/post! :poista-siltatarkastus {:urakka-id (:id @nav/valittu-urakka)
                                   :silta-id silta
                                   :siltatarkastus-id tarkastus}))

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

(defonce valittu-silta (atom nil))

(defonce valitun-sillan-tarkastukset (reaction<! (when-let [vs @valittu-silta]
                                                   (hae-sillan-tarkastukset (:id @valittu-silta)))))

(defonce valittu-tarkastus (reaction (first @valitun-sillan-tarkastukset)))

(defn uusi-tarkastus [silta ur]
  {:kohteet {}, :silta-id silta, :urakka-id ur, :id nil, :tarkastusaika nil, :tarkastaja nil})

(tarkkaile! "valittu-silta" valittu-silta)
(tarkkaile! "valitun-sillan-tarkastukset" valitun-sillan-tarkastukset)