(ns harja.ui.grid-test
  (:require [harja.ui.grid :as g]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils.shared-testutils :as u]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.ui.grid.yleiset :as grid-yleiset]
            [harja.loki :refer [log tarkkaile! error]]
            [cljs-react-test.simulate :as sim]
            [harja.ui.grid :as grid])
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
      (is (= "2004" (.-value (solu 1 2 "input"))))
      "Alkutilanteessa tallenna-nappi on disabled"
      (is (u/disabled? :.grid-tallenna) "ei voi tallentaa ilman muokkauksia")

      "Rivin lisäys toimii"
      (u/click :.grid-lisaa)
      --
      (is (u/enabled? :.grid-tallenna) "Muokkauksen jälkeen voi tallentaa")
      (is (= "" (.-value (solu 3 0))))
      (is (= "" (.-value (solu 3 1))))
      (is (= "" (.-value (solu 3 2 "input"))))

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
      ;(println (.-innerHTML (solu 3 2 "input")))
      (is (nil? (u/sel1 (solu 3 2 "div.virheet"))))
      ;(println (u/sel1 :.grid-tallenna))
      (is (not (u/disabled? :.grid-tallenna)))

      "Tallennus muuttaa atomin arvon"
      (u/click :.grid-tallenna)
      --
      (is (= (nth @data 3)
             {:id -1 :nimi "Max Syöttöpaine" :kieli "Vanha hieno kieli" :vuosi 2016})))))

(defn- grid-container []
  [:div
   (for [id (range 0 3)]
     ^{:key id}
     [g/grid {:id (str "g" id)
              :tallenna (constantly nil)}
      skeema
      data])])

(deftest vain-yksi-grid-voi-olla-muokkauksessa
  ;; Vain yhtä gridiä saa kerrallaan muokata, koska
  ;; - Gridin tallennus nollaa muiden gridien muokkauksen, ei kivaa UX:ää
  ;; - Jos gridi on gridin sisällä vetolaatikossa, se tod.näk liittyy isäntäänsä.
  ;;   On hasardia muokata molempia samaan aikaan.

  (komponenttitesti
    [grid-container]

    (let [muokkausnapit (u/sel [:.grid-muokkaa])
          peru-napit (u/sel [:.grid-peru])
          muokkausnappi-1 (-> (nth muokkausnapit 0) .-parentNode)
          muokkausnappi-2 (-> (nth muokkausnapit 1) .-parentNode)
          muokkausnappi-3 (-> (nth muokkausnapit 2) .-parentNode)]

      (is (= (count muokkausnapit) 3))
      (is (= (count peru-napit) 0) "Peru-nappeja ei voi olla ennen muokkaustilaa")

      ;; Kaikki napit on enabloitu
      (is (not (u/disabled? muokkausnappi-1)))
      (is (not (u/disabled? muokkausnappi-2)))
      (is (not (u/disabled? muokkausnappi-3)))

      ;; Asetetaan grid 1 muokkaustilaan
      (u/click muokkausnappi-1)
      --
      ;; Muiden gridien muokkausnappien pitäisi olla disabloitu
      ;; FIXME Miksi nämä kaksi ei toimi!?
      #_(is (u/disabled? muokkausnappi-2))
      #_(is (u/disabled? muokkausnappi-3)))))

(deftest rivin-muokattavuus
  "Testaa että rivin muokkausehdot toimivat oikein: tekee rivikohtaisen ehdon, jonka mukaan vain vuoden 2000 jälkeen
   kehitettyjä kieliä saa muokata. "
  (let [data (r/atom data)
        tallenna (fn [uusi-arvo]
                   (reset! data uusi-arvo)
                   (go true))]
    (komponenttitesti
      [g/grid {:id "g3"
               :voi-muokata-rivia? #(<= 2000 (:vuosi %))
               :tallenna tallenna}
       skeema
       @data]

      "Aloitetaan gridin muokkaus"
      (u/click :.grid-muokkaa)

      --

      "Ensimmäisellä rivillä sarakkeet ovat muokattavia"
      (is (u/input? (u/grid-solu "g3" 0 0)))
      (is (u/input? (u/grid-solu "g3" 0 1)))
      (is (u/input? (u/grid-solu "g3" 0 2 "input")))

      "Toisella rivillä sarakkeet ovat muokattavia"
      (is (u/input? (u/grid-solu "g3" 1 0)))
      (is (u/input? (u/grid-solu "g3" 1 1)))
      (is (u/input? (u/grid-solu "g3" 1 2 "input")))

      "Kolmannella rivillä sarakkeisiin ei voi syöttää arvoja"
      (is (not (u/input? (u/grid-solu "g3" 2 0 ""))))
      (is (not (u/input? (u/grid-solu "g3" 2 1 ""))))
      (is (not (u/input? (u/grid-solu "g3" 2 2 ""))))

      "Kolmannella rivillä sarakkeissa on oikeat arvot"
      (is (= (u/text (u/grid-solu "g3" 2 0 "")) "Joe Armstrong"))
      (is (= (u/text (u/grid-solu "g3" 2 1 "")) "Erlang"))
      (is (= (u/text (u/grid-solu "g3" 2 2 "")) "1986")))))

(def muokkaus-gridin-data
  {1 {:id 1 :nimi "Rich Hickey" :kieli "Clojure" :vuosi 2009}
   2 {:id 2 :nimi "Martin Odersky" :kieli "Scala" :vuosi 2004}
   3 {:id 3 :nimi "Joe Armstrong" :kieli "Erlang" :vuosi 1986}})

(deftest muokkaus-grid-datalla
  (let [data (r/atom muokkaus-gridin-data)]
    (komponenttitesti
      [g/muokkaus-grid {:id "mg1"
                        :otsikko "Teiden hoitourakoiden sydäntalven testimuokkausgridi"
                        :voi-muokata? true
                        :voi-poistaa? (constantly false)
                        :piilota-toiminnot? true
                        :voi-lisata? false
                        :tyhja "Ei kieliä"
                        :jarjesta :id
                        :virheet (atom nil)
                        :tunniste :id}
       skeema
       data]

      "Tekstit ovat riveillä oikein"
      (is (= "Rich Hickey" (.-value (u/grid-solu "mg1" 0 0))))
      (is (= "Scala" (.-value (u/grid-solu "mg1" 1 1))))
      (is (= "Erlang" (.-value (u/grid-solu "mg1" 2 1))))

      "Muokataan ja tallennetaan"

      (u/change (u/grid-solu "mg1" 2 1 "input") "Haskell")
      --
      (is (= "Haskell" (.getAttribute (u/grid-solu "mg1" 2 1) "value")))

      "Toimintonappeja ei ole"
      (is (= "Kumoa" (u/text (u/sel1 :button)))))))

(deftest rivi-piilotetun-otsikon-alla
  (let [testirivit [(grid/otsikko "A" {:id :A}) 1 2 3 4
                    (grid/otsikko "B" {:id :B}) 5 6
                    (grid/otsikko "C" {:id :C}) 7 8]]
    (is (false? (g/rivi-piilotetun-otsikon-alla? 1 testirivit #{:B})))
    (is (false? (g/rivi-piilotetun-otsikon-alla? 2 testirivit #{:B})))
    (is (false? (g/rivi-piilotetun-otsikon-alla? 9 testirivit #{:B})))

    (is (true? (g/rivi-piilotetun-otsikon-alla? 6 testirivit #{:B})))
    (is (true? (g/rivi-piilotetun-otsikon-alla? 7 testirivit #{:B})))

    (is (false? (g/rivi-piilotetun-otsikon-alla? 0 testirivit #{:B}))
        "Otsikkorivi ei koskaan ole piilotetun otsikon alla")
    (is (false? (g/rivi-piilotetun-otsikon-alla? 5 testirivit #{:B}))
        "Otsikkorivi ei koskaan ole piilotetun otsikon alla")))

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

    (is (= (grid-yleiset/tayta-tiedot-alas-toistuvasti
             lahtorivit
             1
             (fn [lahtorivi tama-rivi]
               (assoc tama-rivi :arvo (:arvo lahtorivi))))
           lopputulos-lahtoindeksilla-1))
    (is (= (grid-yleiset/tayta-tiedot-alas-toistuvasti
             lahtorivit
             2
             (fn [lahtorivi tama-rivi]
               (assoc tama-rivi :arvo (:arvo lahtorivi))))
           lopputulos-lahtoindeksilla-2))))
