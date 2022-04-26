(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma-test
  (:require [clj-time.format :as df]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.kyselyt.koodistot :as koodistot]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as yleiset])
  (:import (java.nio.file FileSystems)
           (org.joda.time DateTime)))

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
  (let [kohteet (lataa-kohteet "sijaintipalvelu" "melurakenteet_luiskat")]
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
        kohteet2 (lataa-kohteet "sijaintipalvelu" "kivetyt-alueet_luiskat")
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

(deftest varustetoteuma-velho->harja-test
  (let [syote (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-test-syote.json") :key-fn keyword)
        alkupvm (varuste-vastaanottosanoma/aika->sql (varuste-vastaanottosanoma/velho-pvm->pvm "2019-10-01"))
        muokattu (varuste-vastaanottosanoma/aika->sql (varuste-vastaanottosanoma/velho-aika->aika "2021-03-10T07:57:40Z"))
        odotettu {:sijainti "dummy", :loppupvm nil, :tietolaji "tl506", :tr_loppuosa nil, :muokkaaja "migraatio", :tr_numero 22, :kuntoluokka "Hyvä",
                  :alkupvm alkupvm, :ulkoinen_oid "1.2.246.578.4.3.15.506.283640192", :tr_loppuetaisyys nil, :tr_alkuetaisyys 4139,
                  :lisatieto "Tienviitta", :urakka_id 35, :muokattu muokattu, :tr_alkuosa 5, :toteuma "lisatty"}
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        urakka-pvmt-idlla-fn (partial varusteet/urakka-pvmt-idlla db)
        sijainti-fn (fn [& _] "dummy")
        konversio-fn (partial koodistot/konversio db)
        {tulos :tulos} (varuste-vastaanottosanoma/varustetoteuma-velho->harja urakka-id-fn sijainti-fn konversio-fn urakka-pvmt-idlla-fn syote)]
    (is (= odotettu tulos))))

(deftest velho->harja-puuttuvia-arvoja-test
  (testing "velho->harja-puuttuvia-arvoja-test - oid puuttuu"
    (let [syote (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-test-syote.json") :key-fn keyword)
          puuttuu-oid (dissoc syote :oid)
          odotettu {:tulos nil :virheviesti "puuttuu pakollisia kenttiä: [:ulkoinen_oid]"}
          db (:db jarjestelma)
          urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
          urakka-pvmt-idlla-fn (partial varusteet/urakka-pvmt-idlla db)
          sijainti-fn (partial varusteet/sijainti-kohteelle db)
          konversio-fn (partial koodistot/konversio db)]
      (is (= odotettu (varuste-vastaanottosanoma/varustetoteuma-velho->harja urakka-id-fn sijainti-fn konversio-fn urakka-pvmt-idlla-fn puuttuu-oid))))))

(deftest velho->harja-sijaintipalvelun-vastaus-ei-sisalla-historiaa-test
  (let [kohde (json/read-str (slurp "test/resurssit/velho/varusteet/velho-harja-ei-sisalla-historiaa.json") :key-fn keyword)
        odotettu {:tulos {:alkupvm #inst "2016-08-09T00:00:00.000-00:00"
                          :kuntoluokka "Tyydyttävä"
                          :lisatieto nil
                          :loppupvm nil
                          :muokkaaja "migraatio"
                          :tietolaji "tl514"
                          :toteuma "lisatty"
                          :tr_alkuetaisyys 4549
                          :tr_alkuosa 5
                          :tr_loppuetaisyys 4629
                          :tr_loppuosa 5
                          :tr_numero 22
                          :urakka_id 4
                          :ulkoinen_oid "1.2.246.578.4.1.10.514.330249097"}
                  :virheviesti nil}
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        urakka-pvmt-idlla-fn (partial varusteet/urakka-pvmt-idlla db)
        sijainti-fn (partial varusteet/sijainti-kohteelle db)
        konversio-fn (partial koodistot/konversio db)
        konvertoitu-kohde (varuste-vastaanottosanoma/varustetoteuma-velho->harja urakka-id-fn sijainti-fn konversio-fn urakka-pvmt-idlla-fn kohde)]
    (is (= odotettu (update-in konvertoitu-kohde [:tulos] dissoc :sijainti :muokattu)))))

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

(deftest varusteen-toimenpiteet-konvertoituu-oikein-test
  "Toimenpiteet joukko on konvertoituu niin, että pidämme vain tutut toimenpiteet."
  (let [kohde (json/read-str (slurp "test/resurssit/velho/varusteet/toimenpiteet-konvertoituu-oikein.json") :key-fn keyword)
        odotetut-toimenpiteet "korjaus"
        konversio-fn (partial koodistot/konversio (:db jarjestelma))]
    (is (= odotetut-toimenpiteet (varuste-vastaanottosanoma/varusteen-toteuma konversio-fn kohde)))))

(deftest toteumatyyppi-konvertoituu-oikein-test
  (let [kohde (json/read-str (slurp "test/resurssit/velho/varusteet/toteumatyyppi-konvertoituu-oikein.json") :key-fn keyword)
        uusi-kohde (assoc kohde :alkaen (get-in kohde [:version-voimassaolo :alku])) ; Oletus: version-voimassaolo.alku = alkaen ==> kohde on uusi
        muokattu-kohde (assoc-in kohde [:version-voimassaolo :alku] "2010-07-01")
        uusin-kohde (assoc muokattu-kohde :uusin-versio true)
        poistettu-kohde (assoc-in uusin-kohde [:version-voimassaolo :loppu] "2021-11-01") ; Oletus: historian-viimeinen ja version-voimassaolo.loppu!=null ==> poistettu
        kohteet-ja-toteumatyypit [{:kohde uusi-kohde :odotettu-toteumatyyppi "lisatty"}
                                  {:kohde muokattu-kohde :odotettu-toteumatyyppi "paivitetty"}
                                  {:kohde poistettu-kohde :odotettu-toteumatyyppi "poistettu"}]
        db (:db jarjestelma)
        urakka-id-fn (partial varusteet/urakka-id-kohteelle db)
        urakka-pvmt-idlla-fn (partial varusteet/urakka-pvmt-idlla db)
        sijainti-fn (partial varusteet/sijainti-kohteelle db)
        konversio-fn (partial koodistot/konversio db)]
    (doseq [kohde-toteuma kohteet-ja-toteumatyypit]
      (let [{konvertoitu-kohde :tulos virheviesti :virheviesti}
            (varuste-vastaanottosanoma/varustetoteuma-velho->harja urakka-id-fn sijainti-fn konversio-fn urakka-pvmt-idlla-fn (:kohde kohde-toteuma))]
        (is (nil? virheviesti))
        (is (= (:odotettu-toteumatyyppi kohde-toteuma) (:toteuma konvertoitu-kohde)))))))

(deftest aika->velho-aika-nil-test
  (is (nil? (varuste-vastaanottosanoma/aika->velho-aika nil))))

(deftest aika->velho-aika-illegal-arg-test
  (is (thrown-with-msg? java.lang.IllegalArgumentException #".*aika pitää olla.*"
                        (varuste-vastaanottosanoma/aika->velho-aika "adasas"))))

(def joku-aika "2021-12-02T12:32:00Z")

(deftest aika->velho-aika-test
  (let [odotettu-aika joku-aika
        saatu-aika (varuste-vastaanottosanoma/aika->velho-aika (df/parse (:date-time-no-ms df/formatters) "2021-12-02T12:32:00Z"))]
    (is (instance? String saatu-aika))
    (is (= odotettu-aika saatu-aika))))

(deftest velho-aika->aika-test
  (let [odotettu-aika (df/parse (:date-time-no-ms df/formatters) joku-aika)
        saatu-aika (varuste-vastaanottosanoma/velho-aika->aika joku-aika)]
    (is (instance? DateTime saatu-aika))
    (is (= odotettu-aika saatu-aika))))

(def joku-pvm "2021-12-02")

(deftest velho-pvm->pvm-test
  (let [odotettu-pvm (df/parse (:date df/formatters) joku-pvm)
        saatu-pvm (varuste-vastaanottosanoma/velho-pvm->pvm joku-pvm)]
    (is (instance? DateTime saatu-pvm))
    (is (= odotettu-pvm saatu-pvm))))

(deftest tarkista-varustetoteuma-test
  ; 1. pakolliset kentät
  ; 2. muutoksen-lahde-oid pitää olla Hallintorekisterin maanteiden-hoitourakka, jonka urakkakoodi vastaa VHAR-6045 mukaisesti Harjassa olevaan hoito
  ;    tai teiden-hoito tyyppisen voimassaolevan urakan tunnisteeseen (URAKKA.urakkanro)
  ; 3. version-voimassaolon alkupvm ja loppupvm pitää leikata 1. kohdan urakan keston kanssa. (Jos näin ei ole, ei kohde näy käyttöliittymässä.)
  ; 4. Varusteen tietolajin pitää sisältyä VHAR-5109 kommentissa mainittuihin tietolajeihin
  ; 5. Varusteversion toimenpiteen pitää olla jokin seuraavista: lisäys, päivitys, poisto, tarkastus, korjaus ja puhdistus
  ; 6. Varusten ollessa tl506 (liikennemerkki) tulee sillä olla asetusnumero tai lakinumero, joka kertoo liikennemerkin tyypin
  ;    (meillä lisätieto-tekstiä)
  ; 7. Varusteversion versioitu.tekninen-tapatuma tulee olla tyhjä

  (let [db (:db jarjestelma)
        odotettu-oulu-MHU-urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        sijainti-kohteelle-fn (partial varusteet/sijainti-kohteelle db)
        urakka-pvmt-idlla-fn (partial varusteet/urakka-pvmt-idlla db)
        alku-ja-loppupvm (fn [kohde] (assoc kohde :alkupvm (pvm/->pvm "1.10.2019") :loppupvm nil))
        oid-muokattu-urakka-ja-sijainti (fn [kohde] (-> kohde
                                                        (assoc :oid "1.2.3.4.5" :muokattu (:alkupvm kohde) :urakka_id odotettu-oulu-MHU-urakka-id)
                                                        (assoc :sijainti (sijainti-kohteelle-fn {:sijainti {:osa 5 :tie 22 :etaisyys 4355}}))))
        toimenpide (fn [kohde] (assoc kohde :toteuma "lisays"))
        muutoksen-lahde-tuntematon-oid (fn [kohde] (assoc kohde :muutoksen-lahde-oid "4.3.2.1"))
        sijainti-ei-MHU-testidatassa (fn [kohde] (assoc kohde :sijainti (sijainti-kohteelle-fn {:sijainti {:osa 10 :tie 20 :etaisyys 100}})))
        perus-setti (comp oid-muokattu-urakka-ja-sijainti toimenpide alku-ja-loppupvm)
        kutsu (fn [kohde] (varuste-vastaanottosanoma/tarkista-varustetoteuma kohde urakka-pvmt-idlla-fn))
        juuri-ennen-oulun-MHU-alkua (fn [kohde] (assoc kohde :alkupvm (pvm/->pvm "29.9.2019") :loppupvm (pvm/->pvm "30.9.2019")))]
    ; 1 -> varoita
    (is (= {:toiminto :varoita :viesti "Puuttuu pakollisia kenttiä: [:sijainti]"}
           (kutsu (-> {} perus-setti
                      (assoc :sijainti nil)))))
    ; 3 -> varoita
    (is (= {:toiminto :varoita :viesti
            (str "version-voimassaolon alkupvm ja loppupvm pitää leikata urakan keston kanssa "
                 "alkupvm: Sun Sep 29 00:00:00 EEST 2019 loppupvm: Mon Sep 30 00:00:00 EEST 2019")}
           (kutsu (-> {} perus-setti
                      juuri-ennen-oulun-MHU-alkua))))
    ; 5 -> varoita
    (is (= {:toiminto :varoita :viesti "Toimenpide ei ole lisäys, päivitys, poisto, tarkastus, korjaus tai puhdistus"}
           (kutsu (-> {} alku-ja-loppupvm
                      oid-muokattu-urakka-ja-sijainti))))
    ; 2 -> skippaa
    (is (= {:toiminto :skippaa :viesti "Urakka ei löydy Harjasta."}
           (kutsu (-> {} perus-setti
                      sijainti-ei-MHU-testidatassa
                      muutoksen-lahde-tuntematon-oid
                      (assoc :urakka_id nil)))))))
