(ns harja.tiedot.vesivaylat.urakka.materiaalit
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.vesivaylat.materiaali :as m]
            [cljs.core.async :refer [<! >!]]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.tyokalut.tuck :refer [post!]]
            [harja.ui.viesti :as viesti]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Määritellään viestityypit
(defrecord PaivitaUrakka [urakka])
(defrecord ListausHaettu [tulokset])

(defrecord AloitaMateriaalinLisays [])
(defrecord PaivitaLisattavaMateriaali [tiedot])
(defrecord LisaaMateriaali [])
(defrecord PeruMateriaalinLisays [])
(defrecord MuutaAlkuperaisetTiedot [tiedot])

(defrecord AloitaMateriaalinKirjaus [nimi tyyppi yksikko])
(defrecord PaivitaMateriaalinKirjaus [tiedot])
(defrecord PoistaMateriaalinKirjaus [tiedot])
(defrecord KirjaaMateriaali [])
(defrecord PeruMateriaalinKirjaus [])
(defrecord NaytaKaikkiKirjauksetVaihto [nimi])

(defrecord Virhe [virhe])

;; App atom
(defonce app (r/atom {:urakka-id nil
                      :materiaalilistaus nil

                      ;; Lisättävän materiaalin tiedot mäp
                      :lisaa-materiaali nil

                      ;; Kirjattavan materiaalin käytön tiedot
                      :kirjaa-materiaali nil

                      :tallennus-kaynnissa? false}))


(extend-protocol t/Event
  PaivitaUrakka
  (process-event [{urakka :urakka} app]
    (let [u (:id urakka)]
      (post! (assoc app
                    :urakka-id u
                    :materiaalilistaus nil)
             :hae-vesivayla-materiaalilistaus {::m/urakka-id u}
             {:onnistui ->ListausHaettu
              :epaonnistui ->Virhe})))

  ListausHaettu
  (process-event [{tulokset :tulokset} app]
    (assoc app
           :materiaalilistaus tulokset
           :lisaa-materiaali nil
           :kirjaa-materiaali nil
           :tallennus-kaynnissa? false))

  AloitaMateriaalinLisays
  (process-event [_ app]
    (assoc app :lisaa-materiaali {::m/urakka-id (:urakka-id app)
                                  ::m/pvm (pvm/nyt)}))

  PaivitaLisattavaMateriaali
  (process-event [{tiedot :tiedot} app]
    (update app :lisaa-materiaali merge tiedot))

  LisaaMateriaali
  (process-event [_ {:keys [urakka-id lisaa-materiaali] :as app}]
    (-> app
        (assoc :tallennus-kaynnissa? true)
        (post! :kirjaa-vesivayla-materiaali (lomake/ilman-lomaketietoja lisaa-materiaali)
               {:onnistui ->ListausHaettu
                :epaonnistui ->Virhe})))

  PeruMateriaalinLisays
  (process-event [_ app]
    (dissoc app :lisaa-materiaali))

  MuutaAlkuperaisetTiedot
  (process-event [{tiedot :tiedot} app]
    (let [uudet-alkuperaiset-tiedot (map #(-> %
                                              (assoc ::m/ensimmainen-kirjaus-id (get-in % [::m/muutokset 0 ::m/id]))
                                              (assoc ::m/idt (map ::m/id (::m/muutokset %)))
                                              (dissoc ::m/muutokset))
                                         (:uudet-alkuperaiset-tiedot tiedot))
          urakka-id (:urakka-id tiedot)
          chan (:chan tiedot)
          onnistui! (tuck/send-async! ->ListausHaettu)
          epaonnistui! (tuck/send-async! ->Virhe)]

      (go
        (let [vastaus (<! (k/post! :muuta-materiaalien-alkuperaiset-tiedot
                                   {::m/urakka-id urakka-id
                                    :uudet-alkuperaiset-tiedot uudet-alkuperaiset-tiedot}))]
          (if (k/virhe? vastaus)
            (epaonnistui! vastaus)
            (onnistui! vastaus))
          (>! chan vastaus)))

      (assoc app :tallennus-kaynnissa? true)))

  AloitaMateriaalinKirjaus
  (process-event [{nimi :nimi tyyppi :tyyppi yksikko :yksikko} app]
    (assoc app :kirjaa-materiaali {::m/urakka-id (:urakka-id app)
                                   ::m/nimi nimi
                                   ::m/pvm (pvm/nyt)
                                   :tyyppi tyyppi
                                   ::m/yksikko yksikko}))

  PaivitaMateriaalinKirjaus
  (process-event [{tiedot :tiedot} app]
    (update app :kirjaa-materiaali merge tiedot))

  PoistaMateriaalinKirjaus
  (process-event [{tiedot :tiedot} app]
    (post! app :poista-materiaalikirjaus {::m/urakka-id (:urakka-id tiedot)
                                          ::m/id (:materiaali-id tiedot)}
           {:onnistui ->ListausHaettu
            :epaonnistui ->Virhe}))

  PeruMateriaalinKirjaus
  (process-event [_ app]
    (dissoc app :kirjaa-materiaali))

  KirjaaMateriaali
  (process-event [_ {kirjaa-materiaali :kirjaa-materiaali :as app}]
    (-> app
        (assoc :tallennus-kaynnissa? true)
        (post! :kirjaa-vesivayla-materiaali
               (as-> kirjaa-materiaali m
                     (lomake/ilman-lomaketietoja m)
                     ;; Jos kirjataan käyttöä, muutetaan määrä negatiiviseksi
                     (if (= :- (:tyyppi m))
                       (update m ::m/maara -)
                       m)
                     (dissoc m :tyyppi))
               {:onnistui ->ListausHaettu
                :epaonnistui ->Virhe})))

  NaytaKaikkiKirjauksetVaihto
  (process-event [{nimi :nimi} app]
    (update app :materiaalilistaus (fn [listaus]
                                     (map #(if (= (::m/nimi %) nimi)
                                             (update % :nayta-kaikki? not) %)
                                          listaus))))

  Virhe
  (process-event [virhe app]
    (log "Virhe: " (pr-str virhe))
    (viesti/nayta! "Virhe palvelinkutsussa" :warning)
    (assoc app :tallennus-kaynnissa? false)))
