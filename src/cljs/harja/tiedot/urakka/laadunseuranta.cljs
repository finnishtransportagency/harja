(ns harja.tiedot.urakka.laadunseuranta
  "Tämä nimiavaruus hallinnoi laadunseurantaa sekä laatupoikkeamia ja tarkastuksia"
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce laadunseurannassa? (atom false))

(defn hae-urakan-yllapitokohteet-laatupoikkeamalomakkeelle
  "Hakee urakan ylläpitokohteet näytettäväksi laatupoikkeamalomakkeella."
  [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet-laatupoikkeamalomakkeelle
           {:urakka-id urakka-id
            :sopimus-id sopimus-id}))

(def urakan-yllapitokohteet
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               urakka-tyyppi (:tyyppi @nav/valittu-urakka)
               [sopimus-id _] @u/valittu-sopimusnumero
               laadunseurannassa? laadunseurannassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and (or (= :paallystys urakka-tyyppi)
                             (= :paikkaus urakka-tyyppi)
                             (= :tiemerkinta urakka-tyyppi))
                         laadunseurannassa? urakka-id sopimus-id)
                (hae-urakan-yllapitokohteet-laatupoikkeamalomakkeelle urakka-id sopimus-id))))