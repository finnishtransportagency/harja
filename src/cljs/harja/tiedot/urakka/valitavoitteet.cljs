(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(defn hae-urakan-valitavoitteet [urakka-id]
  (k/post! :hae-urakan-valitavoitteet urakka-id))

(defn merkitse-valmiiksi! [urakka-id valitavoite-id valmis-pvm kommentti]
  (k/post! :merkitse-valitavoite-valmiiksi
           {:urakka-id urakka-id
            :valitavoite-id valitavoite-id
            :valmis-pvm valmis-pvm
            :kommentti kommentti}))

(defn tallenna! [urakka-id valitavoitteet]
  (k/post! :tallenna-urakan-valitavoitteet
           {:urakka-id urakka-id
            :valitavoitteet valitavoitteet}))

(def valitavoitteet
  "Urakan omat ja valtakunnalliset välitavoitteet"
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              (hae-urakan-valitavoitteet urakka-id)))

(def urakan-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv #(and
                         (:urakka-id %)
                         (nil? (:valtakunnallinen-valitavoite-id %)))
                       @valitavoitteet))))

(def valtakunnalliset-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv :valtakunnallinen-valitavoite-id @valitavoitteet))))