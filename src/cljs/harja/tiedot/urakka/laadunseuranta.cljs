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

(defn hae-urakan-yllapitokohteet-lomakkeelle
  "Hakee urakan ylläpitokohteet näytettäväksi laatupoikkeamalomakkeella."
  [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet-lomakkeelle
           {:urakka-id urakka-id
            :sopimus-id sopimus-id}))

(def urakan-yllapitokohteet-lomakkeelle
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               urakka-tyyppi (:tyyppi @nav/valittu-urakka)
               [sopimus-id _] @u/valittu-sopimusnumero
               laadunseurannassa? @laadunseurannassa?
               yllapitokohdeurakka? @u/yllapitokohdeurakka?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and yllapitokohdeurakka?
                         laadunseurannassa? urakka-id sopimus-id)
                (hae-urakan-yllapitokohteet-lomakkeelle urakka-id sopimus-id))))
