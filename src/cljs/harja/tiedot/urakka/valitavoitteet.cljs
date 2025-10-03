(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [tuck.core :as tuck])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(defonce valitavoitteet-app-tila
  (atom {:valittu-hoitokausi nil
         :nakymassa? false}))

(defrecord NakymaAvattu [])
(defrecord NakymaSuljettu [])
(defrecord HoitokausiVaihdettu [hoitokausi])

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
  "Urakan omat ja valtakunnalliset välitavoitteet.
   Riippuu valitusta hoitokaudesta, jotta data päivittyy hoitokauden vaihtuessa."
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?
               _ @u/valittu-hoitokausi]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id nakymassa?)
                (hae-urakan-valitavoitteet urakka-id))))

(def urakan-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv (comp not :valtakunnallinen-id) @valitavoitteet))))

(def valtakunnalliset-valitavoitteet
  (reaction (when @valitavoitteet
              (filterv :valtakunnallinen-id @valitavoitteet))))

(defn hae-urakan-yllapitokohteet
  "Hakee urakan ylläpitokohteet näytettäväksi välitavoitteiden näkymässä"
  [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet-lomakkeelle
           {:urakka-id urakka-id
            :sopimus-id sopimus-id}))

(def urakan-yllapitokohteet-lomakkeelle
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               _ (:tyyppi @nav/valittu-urakka)
               [sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @nakymassa?
               yllapitokohdeurakka? @u/yllapitokohdeurakka?]
    {:nil-kun-haku-kaynnissa? true}
    (when (and yllapitokohdeurakka? nakymassa? urakka-id sopimus-id)
      (hae-urakan-yllapitokohteet urakka-id sopimus-id))))


(extend-protocol tuck/Event
  NakymaAvattu
  (process-event [_ app]
    ;; Alusta hoitokausi globaalista jos ei ole asetettu
    (let [globaali-hk @u/valittu-hoitokausi
          app-hk (:valittu-hoitokausi app)
          hoitokausi (or app-hk globaali-hk)
          hoitokaudet @u/valitun-urakan-hoitokaudet]
      (reset! nakymassa? true)
      (-> app
        (assoc :nakymassa? true)
        (assoc :valittu-hoitokausi hoitokausi)
        (assoc :urakan-hoitokaudet hoitokaudet))))
  
  NakymaSuljettu
  (process-event [_ app]
    (reset! nakymassa? false)
    (assoc app :nakymassa? false))
  
  HoitokausiVaihdettu
  (process-event [{:keys [hoitokausi]} app]
    ;; Päivitä globaali hoitokausi
    (u/valitse-hoitokausi! hoitokausi)
    (assoc app :valittu-hoitokausi hoitokausi)))


