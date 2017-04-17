(ns harja.ui.lomake-test
  (:require [harja.ui.lomake :as lomake]
            [reagent.core :as r]
            [clojure.spec :as s]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils]
            [cljs.test :as test]
            [clojure.string :as str])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]
                   [cljs.test :refer [is testing deftest]]
                   [devcards.core :as dc :refer [defcard-rg]]))

(test/use-fixtures :each
  u/komponentti-fixture)

(s/def ::luoja string?)
(s/def ::vuosi (s/int-in 1800 2100))
(s/def ::nimi (s/and string? seq)) ;; seq takaa ettei tyhjä

(def paradigmat #{:functional :object-oriented :procedural :declarative})
(s/def ::paradigma paradigmat)

(s/def ::kieli (s/keys :req [::luoja ::vuosi ::nimi]
                       :opt [::paradigma]))

(def skeema [{:nimi ::nimi :otsikko "Nimi" :tyyppi :string}
             {:nimi ::luoja :otsikko "Luoja" :tyyppi :string}
             {:nimi ::vuosi :otsikko "Vuosi" :tyyppi :positiivinen-numero}
             {:nimi ::paradigma :otsikkoa "Paradigma"
              :tyyppi :valinta :valinnat (vec paradigmat)}])

(defn testilomake [data]
  [:div.testilomake
   [lomake/lomake
    {:muokkaa! #(reset! data %)
     :spec ::kieli
     :footer-fn
     (fn [data]
       [lomake/nayta-puuttuvat-pakolliset-kentat data])}
    skeema
    @data]])

(def testidata (r/atom {::vuosi 1987}))

(defcard-rg lomake-card
  "## Lomake spec validoinnilla

Tämä lomake käyttää speciä kenttien validointiin."
  (fn [data]
    [:div#lomake-card [testilomake data]])
  testidata
  {:inspect-data true})

(defn- puuttuvat-pakolliset []
  (let [kentat-txt (->> :.puuttuvat-pakolliset-kentat
                        u/sel1
                        u/text
                        (re-matches #".*puuttuu: (.*)")
                        second)]
    (into #{}
          (map str/trim)
          (str/split kentat-txt #","))))

(deftest lomake-testit
  (let [data (r/atom {::vuosi 1987})
        aseta! #(u/change (str "label[for='" %1 "'] + input") %2)]
    (komponenttitesti
     [testilomake data]
     --
     "Aluksi puuttuu kaksi pakollista kenttää"
     (is (= #{"Nimi" "Luoja"} (puuttuvat-pakolliset)))
     (is (= (::vuosi @data) 1987))

     "Muutetaan vuosi liian pieneksi"
     (aseta! "vuosi" "1745")
     --
     (is (= (::vuosi @data) 1745))
     (is (= (first (::vuosi (::lomake/virheet @data)))
            "Liian pieni. Sallittu arvo välillä 1800 – 2099"))

     "Muutetaan vuosi liian suureksi"
     (aseta! "vuosi" "2117")
     --
     (is (= (::vuosi @data) 2117))
     (is (= (first (::vuosi (::lomake/virheet @data)))
            "Liian suuri. Sallittu arvo välillä 1800 – 2099"))

     "Poistetaan vuosi"
     (aseta! "vuosi" "")
     --
     (is (= (::vuosi @data) nil))
     (is ((::lomake/puuttuvat-pakolliset-kentat @data) ::vuosi))
     (is (= #{"Nimi" "Luoja" "Vuosi"} (puuttuvat-pakolliset)))

     "Kirjoitetaan luoja ja nimi"
     (aseta! "luoja" "Foo Barsky")
     --
     (aseta! "nimi" "Somelanguage")
     --
     (is (= (::luoja @data) "Foo Barsky"))
     (is (= (::nimi @data) "Somelanguage"))
     (is (= #{"Vuosi"} (puuttuvat-pakolliset)))

     "Asetetaan luoja ja nimi taas tyhjäksi"
     (aseta! "luoja" "")
     --
     (aseta! "nimi" "")
     --
     (is (= #{"Nimi" "Vuosi"} (puuttuvat-pakolliset))
         "Luoja EI ole puuttuvissa, koska sen spec sallii tyhjän arvon"))))
