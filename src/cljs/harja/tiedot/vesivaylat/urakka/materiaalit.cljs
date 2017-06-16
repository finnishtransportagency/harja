(ns harja.tiedot.vesivaylat.urakka.materiaalit
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.vesivaylat.materiaali :as m]
            [cljs.core.async :refer [<!]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Määritellään viestityypit
(defrecord PaivitaUrakka [urakka])
(defrecord ListausHaettu [tulokset])
(defrecord HaeMateriaalinKaytto [nimi])
(defrecord MateriaalinKayttoHaettu [nimi rivit])

(defrecord AloitaMateriaalinLisays [])
(defrecord PaivitaLisattavaMateriaali [tiedot])
(defrecord LisaaMateriaali [])
(defrecord PeruMateriaalinLisays [])

;; App atom
(defonce app (r/atom {:urakka-id nil
                      :materiaalilistaus nil

                      ;; materiaalin nimi -> käyttörivit
                      :materiaalin-kaytto {}

                      ;; Lisättävän materiaalin tiedot mäp
                      :lisaa-materiaali nil}))


(defn- hae [{u :urakka-id :as app}]
  (let [tulos! (t/send-async! ->ListausHaettu)]
    (go
      (tulos! (<! (k/post! :hae-vesivayla-materiaalilistaus {::m/urakka-id u}))))
    app))

(extend-protocol t/Event
  PaivitaUrakka
  (process-event [{urakka :urakka} app]
    (hae (assoc app :urakka-id (:id urakka))))

  ListausHaettu
  (process-event [{tulokset :tulokset} app]
    (assoc app
           :materiaalilistaus tulokset))

  HaeMateriaalinKaytto
  (process-event [{nimi :nimi} {u :urakka-id :as app}]
    (let [tulos! (t/send-async! (partial ->MateriaalinKayttoHaettu nimi))]
      (go
        (tulos! (<! (k/post! :hae-vesivayla-materiaalin-kaytto
                             {::m/urakka-id u ::m/nimi nimi}))))
      app))

  MateriaalinKayttoHaettu
  (process-event [{:keys [nimi rivit]} app]
    (assoc-in app [:materiaalin-kaytto nimi] rivit))

  AloitaMateriaalinLisays
  (process-event [_ app]
    (assoc app :lisaa-materiaali {::m/urakka-id (:urakka-id app)
                                  ::m/pvm (pvm/nyt)}))

  PaivitaLisattavaMateriaali
  (process-event [{tiedot :tiedot} app]
    (update app :lisaa-materiaali merge tiedot))

  LisaaMateriaali
  (process-event [_ {:keys [urakka-id lisaa-materiaali] :as app}]
    (let [tulos! (t/send-async! ->ListausHaettu)]
      (go (tulos! (<! (k/post! :kirjaa-vesivayla-materiaali
                               lisaa-materiaali))))
      app))

  PeruMateriaalinLisays
  (process-event [_ app]
    (dissoc app :lisaa-materiaali)))
