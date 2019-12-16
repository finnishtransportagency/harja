(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
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

(defn tallenna-valitavoitteet! [urakka-id valitavoitteet]
  (k/post! :tallenna-urakan-valitavoitteet
           {:urakka-id urakka-id
            :valitavoitteet valitavoitteet}))

(def valitavoitteet
  "Urakan omat ja valtakunnalliset välitavoitteet"
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id nakymassa?)
                (hae-urakan-valitavoitteet urakka-id))))

(def urakan-valitavoitteet
  (reaction (when @valitavoitteet
              (println " €€€€€€  Urakan omat" @valitavoitteet)
              (filterv (comp not :valtakunnallinen-id) @valitavoitteet))))

(def valtakunnalliset-valitavoitteet
  (reaction (when @valitavoitteet
              (println " €€€€€€  Valtakunnalliset " @valitavoitteet)
              (filterv :valtakunnallinen-id @valitavoitteet))))

(defn hae-urakan-yllapitokohteet
  "Hakee urakan ylläpitokohteet näytettäväksi välitavoitteiden näkymässä"
  [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet-lomakkeelle
           {:urakka-id urakka-id
            :sopimus-id sopimus-id}))

(def urakan-yllapitokohteet-lomakkeelle
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               urakka-tyyppi (:tyyppi @nav/valittu-urakka)
               [sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @nakymassa?
               yllapitokohdeurakka? @u/yllapitokohdeurakka?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and yllapitokohdeurakka? nakymassa? urakka-id sopimus-id)
                (hae-urakan-yllapitokohteet urakka-id sopimus-id))))
