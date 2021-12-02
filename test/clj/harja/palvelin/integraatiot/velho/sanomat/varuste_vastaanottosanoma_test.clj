(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma-test
  (:require [clj-time.format :as df]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.kyselyt.koodistot :as koodistot])
  (:import (java.nio.file FileSystems)))

(use-fixtures :once tietokantakomponentti-fixture)

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

(def +kaikki-tietolajit+ #{:tl501 :tl503 :tl504 :tl505 :tl506 :tl507 :tl508 :tl509 :tl512
                           :tl513 :tl514 :tl515 :tl516 :tl517 :tl518 :tl520 :tl522 :tl524})

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

(defn assertoi-kohteen-tietolaji-on-kohteen-oidissa [kohteet & tietolaji-poikkeus-map]
  (doseq [kohde kohteet]
    (let [odotettu-tietolaji-oidista (poimi-tietolaji-oidista (:oid kohde))
          tietolaji-poikkeus-map (or tietolaji-poikkeus-map {"tl514" "tl501"}) ; Melukaiteet ovat kaiteita nyt! tl514 -> tl501
          mapatty-tietolaji (get tietolaji-poikkeus-map odotettu-tietolaji-oidista odotettu-tietolaji-oidista)
          odotettu-tietolaji (if (contains? +kaikki-tietolajit+ (keyword mapatty-tietolaji)) mapatty-tietolaji)]
      (let [paatelty-tietolaji (varuste-vastaanottosanoma/varusteen-tietolaji kohde)]
        (is (= odotettu-tietolaji paatelty-tietolaji)
            (format "Testitiedoston: %s rivillä: %s (oid: %s) odotettu tietolaji: %s ≠ päätelty tietolaji: %s"
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

(deftest varusteen-tl-melurakenteet-test                    ; {:tl514}
  (let [kohteet (lataa-kohteet "sijaintipalvelu" "melurakenteet_luiska")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet {})))

(deftest varusteen-tl-aidat-test                            ; {:tl515}
  (let [kohteet (lataa-kohteet "varusterekisteri" "aidat")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-portaat-test                          ; {:tl517}
  (let [kohteet (lataa-kohteet "varusterekisteri" "portaat")]
    (is (= 1 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
    (assertoi-kohteen-tietolaji-on-kohteen-oidissa kohteet)))

(deftest varusteen-tl-kivetyt-alueet-test                   ; {:tl518}
  (let [kohteet1 (lataa-kohteet "sijaintipalvelu" "kivetyt-alueet_erotusalueet")
        kohteet2 (lataa-kohteet "sijaintipalvelu" "kivetyt-alueet_luiska")
        kohteet (concat kohteet1 kohteet2)]
    (is (= 3 (count kohteet)) "Odotin X testikohdetta testiresursseista.")
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
        odotettu {:sijainti "dummy", :loppupvm nil, :tietolaji "tl506", :tr_loppuosa nil, :muokkaaja "migraatio", :tr_numero 22, :kuntoluokka "Hyvä",
                  :alkupvm #inst "2010-06-15T21:00:00.000000000-00:00", :velho_oid "1.2.246.578.4.3.15.506.283640192", :tr_loppuetaisyys nil, :tr_alkuetaisyys 4139,
                  :lisatieto "Tienviitta", :urakka_id 35, :muokattu #inst "2021-03-10T07:57:40.000000000-00:00", :tr_alkuosa 5, :toteuma "lisatty"}
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        sijainti-fn (fn [& _] "dummy")
        konversio-fn (partial koodistot/konversio db)
        {tulos :tulos} (varuste-vastaanottosanoma/velho->harja urakka-id-fn sijainti-fn konversio-fn syote)]
    (is (= odotettu tulos))))

(deftest velho->harja-puuttuvia-arvoja-test
  (log/debug "velho->harja-puuttuvia-arvoja-test
  syöte: \"test/resurssit/velho/varusteet/velho-harja-test-puuttuvia-arvoja.json\"")
  (let [syote (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-test-puuttuvia-arvoja.json") :key-fn keyword)
        odotettu nil
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        sijainti-fn (partial varusteet/sijainti-kohteelle db)
        konversio-fn (partial koodistot/konversio db)]
    (is (thrown-with-msg? java.lang.AssertionError #".*`muokattu` on pakollinen.*"
                          (varuste-vastaanottosanoma/velho->harja urakka-id-fn sijainti-fn konversio-fn syote)))))


(deftest varusteen-lisatieto-palauttaa-null-muille-kuin-liikennemerkeille-test
  (let [kohde nil
        tietolajit (disj +kaikki-tietolajit+ :tl506)
        db (:db jarjestelma)
        konversio-fn (partial koodistot/konversio db)]
    (doseq [tl tietolajit]
      (varuste-vastaanottosanoma/varusteen-lisatieto konversio-fn (name tl) kohde))))

(deftest varusteen-yleinen-kuntoluokka-konvertoituu-oikein-test
  "Yleinen-kuntoluokka ei ole pakollinen, mutta jos on, niin sen pitää konvertoitua."
  (let [kohde (json/read-str (slurp "test/resurssit/velho/varusteet/kuntoluokka-konvertoituu-oikein.json") :key-fn keyword)
        odotettu-kuntoluokka "Hyvä"
        konversio-fn (partial koodistot/konversio (:db jarjestelma))]
    (is (= odotettu-kuntoluokka (varuste-vastaanottosanoma/varusteen-kuntoluokka konversio-fn kohde)))))

(deftest toteumatyyppi-konvertoituu-oikein-test
  (let [kohde (json/read-str (slurp "test/resurssit/velho/varusteet/toteumatyyppi-konvertoituu-oikein.json") :key-fn keyword)
        uusi-kohde (assoc kohde :alkaen (get-in kohde [:version-voimassaolo :alku])) ; Oletus: version-voimassaolo.alku = alkaen ==> kohde on uusi
        muokattu-kohde (assoc-in kohde [:version-voimassaolo :alku] "2000-01-01")
        uusin-kohde (assoc muokattu-kohde :uusin-versio true)
        poistettu-kohde (assoc-in uusin-kohde [:version-voimassaolo :loppu] "2021-11-01") ; Oletus: historian-viimeinen ja version-voimassaolo.loppu!=null ==> poistettu
        kohteet-ja-toteumatyypit [{:kohde uusi-kohde :odotettu-toteumatyyppi "lisatty"}
                                  {:kohde muokattu-kohde :odotettu-toteumatyyppi "paivitetty"}
                                  {:kohde poistettu-kohde :odotettu-toteumatyyppi "poistettu"}]
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        sijainti-fn (partial varusteet/sijainti-kohteelle db)
        konversio-fn (partial koodistot/konversio db)]
    (doseq [kohde-toteuma kohteet-ja-toteumatyypit]
      (let [{konvertoitu-kohde :tulos} (varuste-vastaanottosanoma/velho->harja urakka-id-fn sijainti-fn konversio-fn (:kohde kohde-toteuma))]
        (is (= (:odotettu-toteumatyyppi kohde-toteuma) (:toteuma konvertoitu-kohde)))))))