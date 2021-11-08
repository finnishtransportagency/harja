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
      (str "test/resurssit/velho/varusteet/" palvelu)
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
  (doseq [kohde kohteet]
    (let [tietolaji-oidista (poimi-tietolaji-oidista (:oid kohde))
          tietolaji-poikkeus-map {"tl514" "tl501"}          ; Melukaiteet ovat kaiteita nyt! tl514 -> tl501
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

(deftest varusteen-tl-tienvarsikalusteet-test               ;{:tl503 :tl504 :tl505 :tl507 :tl508 :tl516}
  (let [kohteet (lataa-kohteet "varusterekisteri" "tienvarsikalusteet")]
    (is (= 35 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-kaiteet-test                          ; {:tl501}
  (let [kohteet (lataa-kohteet "varusterekisteri" "kaiteet")]
    (is (= 22 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-liikennemerkit-test                   ; {:tl505}
  (let [kohteet (lataa-kohteet "varusterekisteri" "liikennemerkit")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-rumpuputket-test                      ; {:tl509}
  (let [kohteet (lataa-kohteet "varusterekisteri" "rumpuputket")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-kaivot-test                           ; {:tl512}
  (let [kohteet (lataa-kohteet "varusterekisteri" "kaivot")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-reunapaalut-test                      ; {:tl513}
  (let [kohteet (lataa-kohteet "varusterekisteri" "reunapaalut")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-aidat-test                            ; {:tl515}
  (let [kohteet (lataa-kohteet "varusterekisteri" "aidat")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-portaat-test                          ; {:tl517}
  (let [kohteet (lataa-kohteet "varusterekisteri" "portaat")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-puomit-test                           ; {:tl520}
  (let [kohteet (lataa-kohteet "varusterekisteri" "puomit")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-reunatuet-test                        ; {:tl522}
  (let [kohteet (lataa-kohteet "varusterekisteri" "reunatuet")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-viherkuviot-test                      ; {:tl524}
  (let [kohteet (lataa-kohteet "tiekohderekisteri" "viherkuviot")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest velho->harja-test
  (let [syote (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-test-syote.json") :key-fn keyword)
        odotettu {:sijainti "abc", :loppupvm nil, :tietolaji "tl501", :tr_loppuosa 5, :muokkaaja "migraatio", :tr_numero 22, :kuntoluokka 0,
                  :alkupvm #inst "2013-09-22T21:00:00.000-00:00", :velho_oid "1.2.246.578.4.3.1.501.148568476", :tr_loppuetaisyys 4555, :tr_alkuetaisyys 4355,
                  :lisatieto nil, :urakka_id 123, :muokattu #inst "2021-10-15T06:44:39.000-00:00", :tr_alkuosa 5, :toimenpide "paivitetty"}
        tulos (varuste-vastaanottosanoma/velho->harja (fn [& _] 123) (fn [& _] "abc") syote)]
    (is (= odotettu tulos))))

(deftest velho->harja-puuttuvia-arvoja-test
  (log/debug "velho->harja-puuttuvia-arvoja-test
  syöte: \"test/resurssit/velho/varusteet/velho-harja-test-puuttuvia-arvoja.json\"")
  (let [syote (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-test-puuttuvia-arvoja.json") :key-fn keyword)
        odotettu nil
        tulos (varuste-vastaanottosanoma/velho->harja (fn [& _] 123) (fn [& _] "abc") syote)]
    (is (= odotettu tulos))))