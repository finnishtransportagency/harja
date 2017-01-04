(ns harja-laadunseuranta.tiedot.reitintallennus-test
  (:require [cljs.test :as t :refer-macros [deftest is testing run-tests async]]
            [reagent.core :as reagent :refer [atom dispose!]]
            [harja-laadunseuranta.tiedot.indexeddb :as idb]
            [cljs.core.async :as async :refer [<! >! put! chan close!]]
            [harja-laadunseuranta.tiedot.reitintallennus :as r]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction-to-store with-cursor with-all-items]]
                   [harja-laadunseuranta.macros :refer [after-delay]]
                   [reagent.ratom :refer [reaction]]))

(def +testikannan-nimi+ "testikanta")

(defn sulje-tietokanta [db]
  (idb/close-indexed-db db)
  (idb/delete-indexed-db +testikannan-nimi+))

(deftest reittiviivan-kertyminen-test
  (testing "Identtinen reittipiste ei tallennu"
    (is (= [[1 1] [2 2]] (r/lisaa-piirrettava-reittipiste [[1 1] [2 2]] [2 2]))))
  (testing "Uusi reittipiste tallentuu loppuun"
    (is (= [[1 1] [2 2] [3 3]] (r/lisaa-piirrettava-reittipiste [[1 1] [2 2]] [3 3]))))
  (testing "nil reittipiste ei tallennu"
    (is (= [[1 1] [2 2]] (r/lisaa-piirrettava-reittipiste [[1 1] [2 2]] nil)))))

(defn- tee-sijainti [[old-lon old-lat] [lon lat]]
  {:nykyinen {:lon lon
              :lat lat}
   :edellinen {:lon old-lon
               :lat old-lat}})

(deftest reitintallennus-test
  (async test-ok
    (go
      (let [db (atom (<! (idb/create-indexed-db +testikannan-nimi+ r/db-spec)))
            segmentti (atom nil)
            reittipisteet (atom [])
            tallennus-kaynnissa (atom false)
            jatkuvat-havainnot (atom #{})
            tarkastusajo-id (atom 10000)
            sijainti (atom {:nykyinen {:lat 1 :lon 2} :edellinen nil})
            sijainin-tallennus-mahdollinen (atom true)
            tarkastuspisteet (atom [])
            mittaustyyppi (atom nil)
            paattymassa (atom false)
            soratiemittaussyotto (atom nil)
            tallennin (r/kaynnista-reitintallennus
                        {:sijainnin-tallennus-mahdollinen-atom sijainin-tallennus-mahdollinen
                         :sijainti-atom sijainti
                         :db @db
                         :tarkastusajo-paattymassa-atom paattymassa
                         :segmentti-atom segmentti
                         :reittipisteet-atom reittipisteet
                         :tarkastusajo-kaynnissa-atom tallennus-kaynnissa
                         :tarkastusajo-atom tarkastusajo-id
                         :tarkastuspisteet-atom tarkastuspisteet
                         :soratiemittaussyotto-atom soratiemittaussyotto
                         :mittaustyyppi-atom mittaustyyppi
                         :jatkuvat-havainnot-atom jatkuvat-havainnot})]

        (testing "Jos tallennus ei käynnissä, segmentin muutos ei mene reittipisteisiin"
          (reset! segmentti [[1 1] [2 2]])
          (is (= [] @reittipisteet)))

        (testing "Jos tallennus käynnissä, segmentin muutokset kertyvät reittipisteisiin multilinestringinä"
          (reset! tallennus-kaynnissa true) ; segmentti-atomissa jo pätkä, pitäisi aiheuttaa eka tallennus
          (reset! segmentti [[2 2] [3 3]]) ; toinen tallennus
          (is (= [[[1 1] [2 2]]
                  [[2 2] [3 3]]] @reittipisteet)))

        (testing "Havaintojen tilan muuttuminen ei aiheuta reittipisteen lisäämistä"
          (reset! jatkuvat-havainnot #{:liukkaus}) ; Seuraavasta reittipisteestä lähtien liukkaus päällä
          (is (= [[[1 1] [2 2]]
                  [[2 2] [3 3]]] @reittipisteet)))

        ;; simuloi sijainnin muuttuminen ajaessa
        (reset! sijainti (tee-sijainti [1 2] [1 1]))
        (reset! sijainti (tee-sijainti [1 1] [2 2]))
        (reset! sijainti (tee-sijainti [2 2] [3 3]))
        (reset! sijainti (tee-sijainti [3 3] [4 4]))

        ;; päätä tallennus
        (reset! tallennus-kaynnissa false)

        ;; tämä reittipiste ei enää tallennu
        (reset! sijainti (tee-sijainti [4 4] [5 5]))

        (testing "Reittipisteet ovat ilmestyneet IndexedDB:hen"
          (let [ajo (cljs.core/atom nil)]
            (with-transaction-to-store
              @db asetukset/+reittimerkinta-store+ :readwrite store
              (with-all-items
                store reittimerkinnat
                (is (= 6 (count reittimerkinnat)))

                (is (= 10000 (get-in reittimerkinnat [0 "tarkastusajo"])))
                (is (get-in reittimerkinnat [0 "aikaleima"]))
                (is (= {"lat" 1 "lon" 2} (get-in reittimerkinnat [0 "sijainti"])))
                (is (not (get-in reittimerkinnat [0 "havainnot" "liukkaus"])))

                (is (= {"lat" 1 "lon" 2} (get-in reittimerkinnat [1 "sijainti"])))
                (is (not (get-in reittimerkinnat [1 "havainnot" "liukkaus"])))

                (is (= {"lat" 1 "lon" 1} (get-in reittimerkinnat [2 "sijainti"])))
                (is (= (get-in reittimerkinnat [2 "havainnot"]) ["liukkaus"]))

                (is (= {"lat" 2 "lon" 2} (get-in reittimerkinnat [3 "sijainti"])))
                (is (= (get-in reittimerkinnat [3 "havainnot"]) ["liukkaus"]))

                (is (= {"lat" 3 "lon" 3} (get-in reittimerkinnat [4 "sijainti"])))
                (is (= (get-in reittimerkinnat [4 "havainnot"]) ["liukkaus"]))

                (is (= {"lat" 4 "lon" 4} (get-in reittimerkinnat [5 "sijainti"])))
                (is (= (get-in reittimerkinnat [5 "havainnot"]) ["liukkaus"]))

                (with-transaction-to-store
                  @db asetukset/+tarkastusajo-store+ :readwrite store
                  (with-cursor
                    store cursor v
                    (reset! ajo v)
                    (idb/cursor-continue cursor)

                    :finally
                    (do
                      (is (= {"tarkastusajo" 10000
                              "reittipisteet" [[[1 1] [2 2]] [[2 2] [3 3]]]
                              "tarkastuspisteet" []}
                             @ajo))

                      (reset! tarkastusajo-id nil)

                      (let [ajoja (cljs.core/atom false)]
                        (with-transaction-to-store
                          @db asetukset/+tarkastusajo-store+ :readwrite store
                          (with-cursor store cursor v
                                       (reset! ajo true)
                                       (idb/cursor-continue cursor)

                                       :finally
                                       (do
                                         (is (not @ajoja))
                                         (reagent/dispose! tallennin)
                                         (sulje-tietokanta @db)
                                         (test-ok))))))))))))))))

(def +testiviestit+ [{:id 1 :sijainti [0 0]}
                     {:id 2 :sijainti [2 2]}
                     {:id 3 :sijainti [5 5]}])


(deftest lahetys-ok-test
  (async test-ok
    (go
      (let [db (atom (<! (idb/create-indexed-db +testikannan-nimi+ r/db-spec)))
            lahetin (r/kaynnista-reitinlahetys 500 @db (fn [reittimerkinnat]
                                                         (go [1 2 3])))]
        (with-transaction-to-store
          @db asetukset/+reittimerkinta-store+ :readwrite store
          (doseq [viesti +testiviestit+]
            (idb/put-object store viesti))

          :on-complete
          (after-delay 1000
                       (with-transaction-to-store
                         @db asetukset/+reittimerkinta-store+ :readonly store
                         (with-all-items store reittimerkinnat
                                         (is (= 0 (count reittimerkinnat)))
                                         (close! lahetin)
                                         (sulje-tietokanta @db)
                                         (reset! db nil)
                                         (test-ok)))))))))

(deftest lahetys-not-ok-test
  (async test-ok
    (go
      (let [db (atom (<! (idb/create-indexed-db +testikannan-nimi+ r/db-spec)))
            lahetin (r/kaynnista-reitinlahetys 500 @db (fn [reittimerkinnat]
                                                         (go [1])))] ;; simuloi lähetysvirhettä
        (with-transaction-to-store
          @db asetukset/+reittimerkinta-store+ :readwrite store
          (doseq [viesti +testiviestit+]
            (idb/put-object store viesti))

          :on-complete
          (after-delay 1000
                       (with-transaction-to-store
                         @db asetukset/+reittimerkinta-store+ :readonly store
                         (with-all-items store reittimerkinnat
                                         (is (= 2 (count reittimerkinnat)))
                                         (is (= [{"id" 2, "sijainti" [2 2]}
                                                 {"id" 3, "sijainti" [5 5]}]
                                                reittimerkinnat))
                                         (close! lahetin)
                                         (sulje-tietokanta @db)
                                         (reset! db nil)
                                         (test-ok)))))))))

