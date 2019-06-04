(ns harja.tiedot.hallinta.valtakunnalliset-valitavoitteet
  "Hallinnoi valtakunnallisten välitavoitteiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.atom :refer [paivita!]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(def valtakunnalliset-kertaluontoiset-valitavoitteet-kaytossa
  #{:hoito})
(def valtakunnalliset-toistuvat-valitavoitteet-kaytossa
  #{:hoito :tiemerkinta})

(defn valtakunnalliset-valitavoitteet-kaytossa? [urakkatyyppi]
  (boolean (or (valtakunnalliset-kertaluontoiset-valitavoitteet-kaytossa urakkatyyppi)
               (valtakunnalliset-toistuvat-valitavoitteet-kaytossa urakkatyyppi))))

(defn hae-valitavoitteet []
  (k/post! :hae-valtakunnalliset-valitavoitteet {}))

(defn tallenna-valitavoitteet [valitavoitteet]
  (log "[VALVÄLI] Tallennetaan valtakunnalliset välitavoitteet: " (pr-str valitavoitteet))
  (k/post! :tallenna-valtakunnalliset-valitavoitteet {:valitavoitteet valitavoitteet}))

(def valitavoitteet
  (reaction<! [nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when nakymassa?
                (hae-valitavoitteet))))

(def kertaluontoiset-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv #(= (:tyyppi %) :kertaluontoinen) @valitavoitteet))))

(def toistuvat-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv #(= (:tyyppi %) :toistuva) @valitavoitteet))))
