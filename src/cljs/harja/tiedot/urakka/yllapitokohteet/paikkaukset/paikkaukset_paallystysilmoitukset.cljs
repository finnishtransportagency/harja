(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.paallystys :as paallystys]))

(defrecord HaePotPaikkaukset [])
(defrecord FiltteriValitseVuosi [uusi-vuosi])

(extend-protocol tuck/Event

  HaePotPaikkaukset
  (process-event [_ app]
    app)

  FiltteriValitseVuosi
  (process-event [{uusi-vuosi :uusi-vuosi} app]
    (let [_ (js/console.log "paallystysilmoitukset controller :: FiltteriValitseVuosi" uusi-vuosi)
          app (assoc-in app [:urakka-tila :valittu-urakan-vuosi] uusi-vuosi)]
      app))
  )
