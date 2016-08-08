(ns harja.tiedot.urakka.laadunseuranta.siltatarkastukset
  "Tämä nimiavaruus hallinnoi urakan siltatarkastuksien tietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.atom :refer-macros [reaction<!]]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction-writable]]))


(defn hae-sillan-tarkastukset [urakka-id silta-id]
  (k/post! :hae-sillan-tarkastukset {:urakka-id urakka-id
                                     :silta-id silta-id}))

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
(defonce valittu-silta (atom nil))

(defonce valitun-sillan-tarkastukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               vs @valittu-silta]
              {:nil-kun-haku-kaynnissa? true}
              (when vs
                (hae-sillan-tarkastukset urakka-id (:id vs)))))

(defonce valittu-tarkastus (reaction-writable (first @valitun-sillan-tarkastukset)))

(defn uusi-tarkastus [silta ur]
  (let [kayttaja @istunto/kayttaja
        etunimi (if kayttaja (:etunimi kayttaja) "")
        sukunimi (if kayttaja (:sukunimi kayttaja) "")
        nimi (str/trim (str etunimi " " sukunimi))]
    {:kohteet       {}, :silta-id silta, :urakka-id ur, :id nil,
     :tarkastusaika (pvm/nyt) :tarkastaja nimi}))
