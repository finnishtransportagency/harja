(ns harja.tiedot.hallinta.urakkahenkilot
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
)
(def tila (atom {:urakkahenkilot []}))

(defrecord HaeUrakkahenkilot [urakkatyyppi paattyneet?])

(extend-protocol tuck/Event
  HaeUrakkahenkilot
  (process-event [{:keys [urakkatyyppi paattyneet?]} app]
    (assoc app :urakkahenkilot
      [{:etunimi "Gon"
        :sukunimi "Freecs"
        :puhelin "3333333333"
        :sahkoposti "gon.freecs@hunterassociation.com"
        :rooli :vastuuhenkilo
        :urakka {:nimi "Oulun MHU 2019-2024"
                 :id 35}}
       {:etunimi "Killua"
        :sukunimi "Zoldyc"
        :puhelin "4444444444"
        :sahkoposti "killua.zoldyck@hunterassociation.com"
        :rooli :vastuuhenkilo-varahenkilo
        :urakka {:nimi "Oulun MHU 2019-2024"
                 :id 35}}]
      )
    )
  )



