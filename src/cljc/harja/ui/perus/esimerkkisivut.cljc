(ns harja.ui.perus.esimerkkisivut
  "Esimerkkisivut sivusimulaattorille ja agenteille.

  Jokainen esimerkki on oma EDN-tiedosto hakemistossa `src/cljc/harja/ui/perus/esimerkkisivut/`:

    {:id :listaussivu            ; yksilöllinen avain
     :nimi \"Listaussivu\"         ; näkyy simulaattorin valinnassa
     :kuvaus \"...\"               ; mitä esimerkki näyttää
     :puu [{:komponentti ...}]}  ; simulaattorin deklaratiivinen komponenttipuu

  Tiedostot luetaan käännösaikaisella makrolla, joten ne päätyvät sekä JVM:ään (testit) että
  selaimeen (simulaattorin valinta). Tiedoston muutos ei laukaise Figwheelin uudelleenlatausta:
  kosketa tätä tiedostoa tai käynnistä build uudelleen. JVM-testi validoi jokaisen puun
  simulaattorin säännöillä ja varmistaa renderöijien esimerkkikattavuuden
  (`lein test harja.ui.perus.esikatselu-test harja.ui.perus.esimerkkisivut-test`).

  `asettelut.edn` näyttää `sailio`-, `osio`-, `vaaka`- ja `ruudukko`-komponenttien yhteiskäytön."
  #?(:clj (:require [clojure.edn :as edn]
                    [clojure.java.io :as io]
                    [clojure.string :as str])
     :cljs (:require-macros [harja.ui.perus.esimerkkisivut :refer [lataa-esimerkkisivut]])))

#?(:clj
   (def ^:private hakemisto "src/cljc/harja/ui/perus/esimerkkisivut"))

#?(:clj
   (defn- lue-esimerkkisivut []
     (->> (.listFiles (io/file hakemisto))
          (filter #(str/ends-with? (.getName %) ".edn"))
          (sort-by #(.getName %))
          (mapv #(edn/read-string (slurp %))))))

#?(:clj
   (defmacro lataa-esimerkkisivut
     "Lukee esimerkkisivut käännösaikaan datana. Data lainataan, jotta sitä ei evaluoida koodina."
     []
     (list 'quote (lue-esimerkkisivut))))

(def esimerkkisivut
  "Esimerkkisivut tiedostonimen mukaisessa järjestyksessä."
  (lataa-esimerkkisivut))

(defn hae
  "Esimerkkisivu id:llä tai nil."
  [id]
  (first (filter #(= id (:id %)) esimerkkisivut)))
