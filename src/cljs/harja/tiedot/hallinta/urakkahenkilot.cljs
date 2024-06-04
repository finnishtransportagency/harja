(ns harja.tiedot.hallinta.urakkahenkilot
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:urakkahenkilot []
                 :jarjestys {:sarake :urakka
                             :suunta :alas}
                 :urakkatyyppi {:nimi "Päällystys" :arvo :paallystys}
                 :paattyneet? false}))

(defrecord HaeUrakkahenkilot [])
(defrecord HaeUrakkahenkilotOnnistui [vastaus])
(defrecord HaeUrakkahenkilotEpaonnistui [vastaus])
(defrecord JarjestaTaulukko [sarake])
(defrecord PaattyneetValittu [uusi-arvo])
(defrecord ValitseUrakkatyyppi [uusi-arvo])

(defn hae-urakkahenkilot [{:keys [urakkatyyppi paattyneet?] :as app}]
  (tuck-apurit/post! app :hae-urakkahenkilot
    {:urakkatyyppi (:arvo urakkatyyppi)
     :paattyneet? paattyneet?}
    {:onnistui ->HaeUrakkahenkilotOnnistui
     :epaonnistui ->HaeUrakkahenkilotEpaonnistui}))

(extend-protocol tuck/Event
  HaeUrakkahenkilot
  (process-event [_ app]
    (hae-urakkahenkilot app)
    app)

  HaeUrakkahenkilotOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :urakkahenkilot vastaus))

  HaeUrakkahenkilotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (js/console.error "Virhe urakkahenkilöiden haussa!" vastaus)
    (viesti/nayta-toast! "Virhe urakkahenkilöiden haussa!" :varoitus)
    app)

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
                                                   >) %)))))
  PaattyneetValittu
  (process-event [{:keys [uusi-arvo]} app]
    (let [app (assoc app :paattyneet? uusi-arvo)]
      (hae-urakkahenkilot app)
      app))

  ValitseUrakkatyyppi
  (process-event [{:keys [uusi-arvo]} app]
    (let [app (assoc app :urakkatyyppi uusi-arvo)]
      (hae-urakkahenkilot app)
      app)))



