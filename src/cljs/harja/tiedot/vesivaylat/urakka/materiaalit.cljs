(ns harja.tiedot.vesivaylat.urakka.materiaalit
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.vesivaylat.materiaali :as m]
            [cljs.core.async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.tyokalut.tuck :refer [palvelukutsu]]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Määritellään viestityypit
(defrecord PaivitaUrakka [urakka])
(defrecord ListausHaettu [tulokset])

(defrecord AloitaMateriaalinLisays [])
(defrecord PaivitaLisattavaMateriaali [tiedot])
(defrecord LisaaMateriaali [])
(defrecord PeruMateriaalinLisays [])

(defrecord AloitaMateriaalinKirjaus [nimi])
(defrecord PaivitaMateriaalinKirjaus [tiedot])
(defrecord KirjaaMateriaali [])
(defrecord PeruMateriaalinKirjaus [])

(defrecord Virhe [virhe])

;; App atom
(defonce app (r/atom {:urakka-id nil
                      :materiaalilistaus nil

                      ;; Lisättävän materiaalin tiedot mäp
                      :lisaa-materiaali nil

                      ;; Kirjattavan materiaalin käytön tiedot
                      :kirjaa-materiaali nil}))


(extend-protocol t/Event
  PaivitaUrakka
  (process-event [{urakka :urakka} app]
    (let [u (:id urakka)]
      (palvelukutsu (assoc app :urakka-id u)
                    :hae-vesivayla-materiaalilistaus {::m/urakka-id u}
                    {:onnistui ->ListausHaettu
                     :epaonnistui ->Virhe})))

  ListausHaettu
  (process-event [{tulokset :tulokset} app]
    (assoc app
           :materiaalilistaus tulokset
           :lisaa-materiaali nil
           :kirjaa-materiaali nil))

  AloitaMateriaalinLisays
  (process-event [_ app]
    (assoc app :lisaa-materiaali {::m/urakka-id (:urakka-id app)
                                  ::m/pvm (pvm/nyt)}))

  PaivitaLisattavaMateriaali
  (process-event [{tiedot :tiedot} app]
    (update app :lisaa-materiaali merge tiedot))

  LisaaMateriaali
  (process-event [_ {:keys [urakka-id lisaa-materiaali] :as app}]
    (palvelukutsu app :kirjaa-vesivayla-materiaali (lomake/ilman-lomaketietoja lisaa-materiaali)
                  {:onnistui ->ListausHaettu
                   :epaonnistui ->Virhe}))

  PeruMateriaalinLisays
  (process-event [_ app]
    (dissoc app :lisaa-materiaali))

  AloitaMateriaalinKirjaus
  (process-event [{nimi :nimi} app]
    (assoc app :kirjaa-materiaali {::m/urakka-id (:urakka-id app)
                                   ::m/nimi nimi
                                   ::m/pvm (pvm/nyt)}))

  PaivitaMateriaalinKirjaus
  (process-event [{tiedot :tiedot} app]
    (update app :kirjaa-materiaali merge tiedot))

  PeruMateriaalinKirjaus
  (process-event [_ app]
    (dissoc app :kirjaa-materiaali))

  KirjaaMateriaali
  (process-event [_ {kirjaa-materiaali :kirjaa-materiaali :as app}]
    (palvelukutsu app :kirjaa-vesivayla-materiaali (lomake/ilman-lomaketietoja kirjaa-materiaali)
                  {:onnistui ->ListausHaettu
                   :epaonnistui ->Virhe}))

  Virhe
  (process-event [virhe app]
    (log "Virhe: " virhe)
    (viesti/nayta! "Virhe palvelinkutsussa" :warning)
    app))
