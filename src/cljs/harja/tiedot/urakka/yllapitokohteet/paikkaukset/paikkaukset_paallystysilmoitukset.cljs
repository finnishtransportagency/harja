(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.paallystys :as paallystys]))

(defrecord HaePotPaikkaukset [])
(defrecord FiltteriValitseVuosi [uusi-vuosi])
(defrecord FiltteriValitseTila [tila valittu?])


(defn- paivita-valinta
  [valitut-tilat tila valittu?]
  (cond 
    ; valitaan kaikki
    (and valittu?
         (= (:nimi tila) :kaikki)) 
    #{:kaikki}
    
    ; poistetaan valinta
    (not valittu?)
    (disj valitut-tilat (:nimi tila))
    
    ; kaikki valittu, valitaan joku muu
    (and valittu?
         (contains? valitut-tilat :kaikki))
    (conj #{} (:nimi tila))
    ; joku muu valittu, poistetaan valinnat
    
    :else 
    (conj valitut-tilat (:nimi tila))))

(extend-protocol tuck/Event

  HaePotPaikkaukset
  (process-event [_ app]
    app)

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (let [_ (js/console.log "paallystysilmoitukset controller :: FiltteriValitseVuosi" uusi-vuosi)
          app (assoc-in app [:urakka-tila :valittu-urakan-vuosi] uusi-vuosi)]
      app))

  FiltteriValitseTila
  (process-event [{:keys [tila valittu?]} app]
    (update app :valitut-tilat paivita-valinta tila valittu?)))
