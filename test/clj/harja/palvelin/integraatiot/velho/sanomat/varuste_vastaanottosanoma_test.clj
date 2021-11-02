(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer :all]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [taoensso.timbre :as log])
  (:import (java.nio.file FileSystems)))

(defn listaa-matchaavat-tiedostot [juuri glob]
  (let [tietolaji-matcher (.getPathMatcher
                            (FileSystems/getDefault)
                            (str "glob:" glob))]
    (->> juuri
         clojure.java.io/file
         .listFiles
         (filter #(.isFile %))
         (filter #(.matches tietolaji-matcher (.getFileName (.toPath %))))
         (mapv #(.getPath %))
         )))

(defn json->kohde [json-lahde lahdetiedosto]
  (let [lahderivi (inc (first json-lahde))                  ; inc, koska 0-based -> järjestysluvuksi
        json (second json-lahde)]
    (log/debug "Ladataan JSON tiedostosta: " lahdetiedosto " riviltä:" lahderivi)
    (->
      json
      (json/read-str :key-fn keyword)
      (assoc :lahdetiedosto (str lahdetiedosto) :lahderivi (str lahderivi)))))

(defn lue-ndjson->kohteet [tiedosto]
  (let [rivit (clojure.string/split-lines (slurp tiedosto))]
    (filter #(contains? % :oid) (map #(json->kohde % tiedosto) (map-indexed #(vector %1 %2) rivit)))))

(defn muunna-tiedostolista-kohteiksi [tiedostot]
  (flatten (mapv lue-ndjson->kohteet tiedostot)))

(defn lataa-kohteet [palvelu kohdeluokka]
  (->
    (listaa-matchaavat-tiedostot
      (str "test/resurssit/velho/" palvelu)
      (str "*" kohdeluokka ".jsonl"))
    muunna-tiedostolista-kohteiksi))

(defn poimi-tietolaji-oidista [oid]
  "Poimitaan oid-merkkijonosta tietolaji.
  Esimerkiksi: oid \"1.2.246.578.4.3.11.507.51457624\" tietolaji 507"
  (as-> oid a
        (clojure.string/split a #"\.")
        (nth a 7)
        (str "tl" a)))

(defn assertoi-kohteen-tietolaji-on-kohteen-oidissa [kohteet]
  (log/debug (format "Testiaineistossa %s kohdetta." (count kohteet)))
  (doseq [kohde kohteet]
    (let [tietolaji-oidista (poimi-tietolaji-oidista (:oid kohde))
          tietolaji-poikkeus-map {"tl514" "tl501"}            ; Melukaiteet ovat kaiteita nyt! tl514 -> tl501
          odotettu-tietolaji (get tietolaji-poikkeus-map tietolaji-oidista tietolaji-oidista)]
      (let [paatelty-tietolaji (varuste-vastaanottosanoma/varusteen-tietolaji kohde)]
        (is (= odotettu-tietolaji paatelty-tietolaji)
            (format "Testitiedoston: %s rivillä: %s (oid: %s) odotettu tietolaji: %s ei vastaa pääteltyä tietolajia: %s"
                    (:lahdetiedosto kohde)
                    (:lahderivi kohde)
                    (:oid kohde)
                    odotettu-tietolaji
                    paatelty-tietolaji
                    ))))))

(deftest varusteen-tl-tienvarsikalusteet-test       ;{:tl503 :tl504 :tl505 :tl507 :tl508 :tl516}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "tienvarsikalusteet")))

(deftest varusteen-tl-kaiteet-test                  ; {:tl501}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "kaiteet")))

(deftest varusteen-tl-liikennemerkit-test           ; {:tl505}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "liikennemerkit")))

(deftest varusteen-tl-rumpuputket-test              ; {:tl509}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "rumpuputket")))

(deftest varusteen-tl-kaivot-test                   ; {:tl512}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "kaivot")))

(deftest varusteen-tl-reunapaalut-test              ; {:tl513}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "reunapaalut")))

(deftest varusteen-tl-aidat-test                    ; {:tl515}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "aidat")))

(deftest varusteen-tl-portaat-test                  ; {:tl517}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "portaat")))

(deftest varusteen-tl-puomit-test                   ; {:tl520}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "puomit")))

(deftest varusteen-tl-reunatuet-test                ; {:tl522}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "varusterekisteri" "reunatuet")))

(deftest varusteen-tl-viherkuviot-test              ; {:tl524}
  (assertoi-kohteen-tietolaji-on-kohteen-oidissa (lataa-kohteet "tiekohderekisteri" "viherkuviot"))
  )