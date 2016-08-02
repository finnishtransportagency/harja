(ns harja.ui.grid-test
  (:require [harja.ui.grid :as g]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils :as u]
            [reagent.core :as r]
            [clojure.string :as str]
            [cljs-react-test.simulate :as sim])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :each u/komponentti-fixture)

(def vuosi-virhe
  "Kieli ei voi olla julkaistu ennen 1. ohjelmoitavaa tietokonetta (Colossus, v 1943)")

(def skeema
  [{:nimi :nimi :otsikko "Nimi" :tyyppi :string}
   {:nimi :kieli :otsikko "Kieli" :tyyppi :string}
   {:nimi :vuosi :otsikko "Julkaisuvuosi" :tyyppi :numero
    :validoi [#(when (and % (< % 1943))
                 vuosi-virhe)]}])

(def data
  [{:id 1 :nimi "Rich Hickey" :kieli "Clojure" :vuosi 2009}
   {:id 2 :nimi "Martin Odersky" :kieli "Scala" :vuosi 2004}
   {:id 3 :nimi "Joe Armstrong" :kieli "Erlang" :vuosi 1986}])

(deftest perusgrid-datalla
  (komponenttitesti
   [g/grid {:id "g1"}
    skeema
    data]

   "Tekstit ovat riveillä oikein"
   (is (= "Rich Hickey" (u/text (u/grid-solu "g1" 0 0))))
   (is (= "Erlang" (u/text (u/grid-solu "g1" 2 1))))

   "Toimintonappeja ei ole"
   (is (nil? (u/sel1 :button)))))

(deftest muokattava-perus-grid
  (let [data (r/atom data)
        tallenna (fn [uusi-arvo]
                   (reset! data uusi-arvo)
                   (go true))
        solu (partial u/grid-solu "g2")]
    (komponenttitesti
     [g/grid {:id "g2"
              :tallenna-vain-muokatut false
              :tallenna tallenna}
      skeema
      @data]

     "Alkutilanne on lukumoodi"
     (is (= "2004" (u/text (solu 1 2))))

     "Muokkausnapin painaminen tekee muokattavaksi"
     (u/click :.grid-muokkaa)
     --
     (is (= "2004" (.-value (solu 1 2))))

     "Rivin lisäys toimii"
     (u/click :.grid-lisaa)
     --
     (is (= "" (.-value (solu 3 0))))
     (is (= "" (.-value (solu 3 1))))
     (is (= "" (.-value (solu 3 2))))

     "Lisää rivi, jossa ei-validi pvm"
     (u/change (solu 3 0 "input") "Max Syöttöpaine")
     (u/change (solu 3 1 "input") "Vanha hieno kieli")
     (u/change (solu 3 2 "input") "1890")
     --
     (is (= vuosi-virhe (str/trim (u/text (solu 3 2 "div.virheet")))))
     (is (u/disabled? :.grid-tallenna))

     "Vuoden korjaaminen sallii tallentamisen"
     (u/change (solu 3 2 "input") "2016")
     --
     (println (.-innerHTML (solu 3 2)))
     (is (nil? (u/sel1 (solu 3 2 "div.virheet"))))
     (println (u/sel1 :.grid-tallenna))
     (is (not (u/disabled? :.grid-tallenna)))

     "Tallennus muuttaa atomin arvon"
     (u/click :.grid-tallenna)
     --
     (is (= (nth @data 3)
            {:id -1 :nimi "Max Syöttöpaine" :kieli "Vanha hieno kieli" :vuosi 2016})))))

;; FIXME: muokkaus-grid kaipaa testiä myös
