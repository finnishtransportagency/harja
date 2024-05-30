(ns harja.tiedot.hallinta.urakkahenkilot
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  )
(def tila (atom {:urakkahenkilot []
                 :jarjestys {:sarake :urakka
                             :suunta :alas}}))

(defrecord HaeUrakkahenkilot [urakkatyyppi paattyneet?])
(defrecord JarjestaTaulukko [sarake])

(extend-protocol tuck/Event
  HaeUrakkahenkilot
  (process-event [{:keys [urakkatyyppi paattyneet?]} app]
    (assoc app :urakkahenkilot
      [{:nimi "Gon Freecs"
        :puhelin "3333333333"
        :sahkoposti "gon.freecs@hunterassociation.com"
        :rooli :vastuuhenkilo
        :urakka "Oulun MHU 2019-2024"}
       {:nimi "Killua Zoldyc"
        :puhelin "4444444444"
        :sahkoposti "killua.zoldyck@hunterassociation.com"
        :rooli :vastuuhenkilo-varahenkilo
        :urakka "Oulun MHU 2019-2024"}]))

  JarjestaTaulukko
  (process-event [{:keys [sarake]} {:keys [jarjestys] :as app}]
    (let [sarake-vaihtui? (not= sarake (:sarake jarjestys))
          uusi-jarjestys (if sarake-vaihtui?
                           :alas
                           (if (= (:suunta jarjestys) :alas)
                             :ylos
                             :alas))]
      (-> app
        (assoc :jarjestys {:sarake sarake
                           :suunta uusi-jarjestys})
        (update :urakkahenkilot #(sort-by sarake (if (= uusi-jarjestys :alas)
                                                   <
                                                   >) %))))))



