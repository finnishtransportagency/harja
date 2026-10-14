(ns harja.ui.grid-test
  (:require [cljs.core.async :refer [<! chan put!]]
            [cljs.test :refer-macros [async deftest is]]
            [harja.ui.grid :as grid]
            [harja.ui.grid.yleiset :as grid-yleiset]
            [reagent.core :as r]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def vuosi-virhe
  "Kieli ei voi olla julkaistu ennen 1. ohjelmoitavaa tietokonetta (Colossus, v 1943)")

(def skeema
  [{:nimi :nimi :otsikko "Nimi" :tyyppi :string}
   {:nimi :kieli :otsikko "Kieli" :tyyppi :string}
   {:nimi :vuosi :otsikko "Julkaisuvuosi" :tyyppi :numero
    :validoi [#(when (and % (< % 1943))
                 vuosi-virhe)]}])

(def grid-data
  [{:id 1 :nimi "Rich Hickey" :kieli "Clojure" :vuosi 2009}
   {:id 2 :nimi "Martin Odersky" :kieli "Scala" :vuosi 2004}
   {:id 3 :nimi "Joe Armstrong" :kieli "Erlang" :vuosi 1986}])

(def muokkaus-gridin-data
  {1 {:id 1 :nimi "Rich Hickey" :kieli "Clojure" :vuosi 2009}
   2 {:id 2 :nimi "Martin Odersky" :kieli "Scala" :vuosi 2004}
   3 {:id 3 :nimi "Joe Armstrong" :kieli "Erlang" :vuosi 1986}})

(deftest rivi-piilotetun-otsikon-alla
  (let [testirivit [(grid/otsikko "A" {:id :A}) 1 2 3 4
                    (grid/otsikko "B" {:id :B}) 5 6
                    (grid/otsikko "C" {:id :C}) 7 8]]
    (is (false? (grid/rivi-piilotetun-otsikon-alla? 1 testirivit #{:B})))
    (is (false? (grid/rivi-piilotetun-otsikon-alla? 2 testirivit #{:B})))
    (is (false? (grid/rivi-piilotetun-otsikon-alla? 9 testirivit #{:B})))
    (is (true? (grid/rivi-piilotetun-otsikon-alla? 6 testirivit #{:B})))
    (is (true? (grid/rivi-piilotetun-otsikon-alla? 7 testirivit #{:B})))
    (is (false? (grid/rivi-piilotetun-otsikon-alla? 0 testirivit #{:B})))
    (is (false? (grid/rivi-piilotetun-otsikon-alla? 5 testirivit #{:B})))))

(deftest tayta-alas-toistaen
  (let [lahtorivit [{:arvo 1 :teksti "ABC1"}
                    {:arvo 2 :teksti "ABC2"}
                    {:arvo 3 :teksti "ABC3"}
                    {:arvo nil :teksti "ABC4"}
                    {:arvo nil :teksti "ABC5"}
                    {:arvo nil :teksti "ABC6"}
                    {:arvo nil :teksti "ABC7"}
                    {:arvo nil :teksti "ABC8"}]
        lopputulos-lahtoindeksilla-1 [{:arvo 1 :teksti "ABC1"}
                                      {:arvo 2 :teksti "ABC2"}
                                      {:arvo 1 :teksti "ABC3"}
                                      {:arvo 2 :teksti "ABC4"}
                                      {:arvo 1 :teksti "ABC5"}
                                      {:arvo 2 :teksti "ABC6"}
                                      {:arvo 1 :teksti "ABC7"}
                                      {:arvo 2 :teksti "ABC8"}]
        lopputulos-lahtoindeksilla-2 [{:arvo 1 :teksti "ABC1"}
                                      {:arvo 2 :teksti "ABC2"}
                                      {:arvo 3 :teksti "ABC3"}
                                      {:arvo 1 :teksti "ABC4"}
                                      {:arvo 2 :teksti "ABC5"}
                                      {:arvo 3 :teksti "ABC6"}
                                      {:arvo 1 :teksti "ABC7"}
                                      {:arvo 2 :teksti "ABC8"}]]
    (is (= lopputulos-lahtoindeksilla-1
           (grid-yleiset/tayta-tiedot-alas-toistuvasti
             lahtorivit
             1
             (fn [lahtorivi tama-rivi]
               (assoc tama-rivi :arvo (:arvo lahtorivi))))))
    (is (= lopputulos-lahtoindeksilla-2
           (grid-yleiset/tayta-tiedot-alas-toistuvasti
             lahtorivit
             2
             (fn [lahtorivi tama-rivi]
               (assoc tama-rivi :arvo (:arvo lahtorivi))))))))

(deftest perusgrid-datalla
  (render/render! [grid/grid {:id "g1"} skeema grid-data])
  (is (some? (screen/get-by-text "Rich Hickey")))
  (is (some? (screen/get-by-text "Erlang")))
  (is (nil? (screen/query-by-role "button"))))

(deftest rivin-muokattavuus
  (render/render! [grid/grid {:id "g3"
                              :voi-muokata-rivia? #(<= 2000 (:vuosi %))
                              :tallenna (fn [_] (go true))}
                   skeema
                   grid-data])
  (fire-event/click (screen/get-by-role "button" {:name "Muokkaa"}))
  (is (= 6 (count (screen/get-all-by-role "textbox"))))
  (is (= "Rich Hickey" (.-value (screen/get-by-display-value "Rich Hickey"))))
  (is (= "Scala" (.-value (screen/get-by-display-value "Scala"))))
  (is (some? (screen/get-by-text "Joe Armstrong")))
  (is (some? (screen/get-by-text "Erlang")))
  (is (some? (screen/get-by-text "1986"))))

(deftest muokkaus-grid-datalla
  (let [data (r/atom muokkaus-gridin-data)]
    (render/render! [grid/muokkaus-grid {:id "mg1"
                                         :otsikko "Teiden hoitourakoiden sydäntalven testimuokkausgridi"
                                         :voi-muokata? true
                                         :voi-poistaa? (constantly false)
                                         :piilota-toiminnot? true
                                         :voi-lisata? false
                                         :tyhja "Ei kieliä"
                                         :jarjesta :id
                                         :virheet (r/atom nil)
                                         :tunniste :id}
                     skeema
                     data])
    (is (= "Rich Hickey" (.-value (screen/get-by-display-value "Rich Hickey"))))
    (is (= "Scala" (.-value (screen/get-by-display-value "Scala"))))
    (is (= "Erlang" (.-value (screen/get-by-display-value "Erlang"))))

    (fire-event/change (screen/get-by-display-value "Erlang")
      {:target {:value "Haskell"}})
    (is (= "Haskell" (.-value (screen/get-by-display-value "Haskell"))))
    (is (some? (screen/get-by-role "button" {:name "Kumoa"})))))

(deftest muokattava-perus-grid
  (async done
    (go
      (let [data (r/atom grid-data)
            tallennettu (chan)
            tallenna (fn [uusi-arvo]
                       (reset! data uusi-arvo)
                       (put! tallennettu true)
                       (go true))]
        (render/render! [grid/grid {:id "g2"
                                    :tallenna-vain-muokatut false
                                    :tallenna tallenna}
                         skeema
                         @data])
        (is (some? (screen/get-by-text "2004")))

        (fire-event/click (screen/get-by-role "button" {:name "Muokkaa"}))
        (is (= "2004" (.-value (screen/get-by-display-value "2004"))))
        (let [tallenna-nappi (screen/get-by-role "button" {:name "Tallenna"})]
          (is (true? (.-disabled tallenna-nappi)))

          (fire-event/click (screen/get-by-role "button" {:name "Lisää rivi"}))
          (let [syotteet (screen/get-all-by-role "textbox")
                nimi (nth syotteet 9)
                kieli (nth syotteet 10)
                vuosi (nth syotteet 11)]
            (is (= 12 (count syotteet)))
            (is (= "" (.-value nimi)))
            (is (= "" (.-value kieli)))
            (is (= "" (.-value vuosi)))

            (fire-event/change nimi {:target {:value "Max Syöttöpaine"}})
            (fire-event/change kieli {:target {:value "Vanha hieno kieli"}})
            (fire-event/change vuosi {:target {:value "1890"}})
            (is (some? (screen/query-by-text vuosi-virhe)))
            (is (true? (.-disabled tallenna-nappi)))

            (fire-event/change vuosi {:target {:value "2016"}})
            (is (nil? (screen/query-by-text vuosi-virhe)))
            (is (false? (.-disabled tallenna-nappi)))

            (fire-event/click tallenna-nappi)
            (<! tallennettu)
            (is (= (nth @data 3)
                   {:id -1 :nimi "Max Syöttöpaine" :kieli "Vanha hieno kieli" :vuosi 2016}))))
        (done)))))
