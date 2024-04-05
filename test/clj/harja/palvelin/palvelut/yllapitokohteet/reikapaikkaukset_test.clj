(ns harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset :as reikapaikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :paikkauskohteet (component/using
                             (reikapaikkaukset/->Reikapaikkaukset)
                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn- tee-kutsu [params kutsu]
  (kutsu-palvelua (:http-palvelin jarjestelma) kutsu +kayttaja-jvh+ params))


(deftest hae-reikapaikkaukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus (tee-kutsu {:tr nil
                            :aikavali nil
                            :urakka-id urakka-id} :hae-reikapaikkaukset)]
    
    (is (= (-> vastaus count) 5))
    (is (= (-> vastaus first :aosa) 1))
    (is (= (-> vastaus first :kustannus) 1500.0M))
    (is (= (-> vastaus first :tie) 20))
    (is (= (-> vastaus first :let) 120))
    (is (= (-> vastaus first :losa) 1))
    (is (= (-> vastaus first :aet) 1))
    (is (= (-> vastaus first :tyomenetelma) 9))
    (is (= (-> vastaus first :maara) 66))
    (is (some? (-> vastaus first :sijainti)))
    (is (some? (-> vastaus first :luotu)))
    (is (some? (-> vastaus first :loppuaika)))
    (is (some? (-> vastaus first :alkuaika)))
    (is (some? (-> vastaus first :reikapaikkaus-yksikko)))
    (is (some? (-> vastaus first :tyomenetelma-nimi)))
    (is (some? (-> vastaus first :massatyyppi)))
    (is (some? (-> vastaus first :luoja-id)))))


(deftest hae-tyomenetelmat-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus (tee-kutsu {:urakka-id urakka-id} :hae-tyomenetelmat)]

    (is (= (-> vastaus count) 19))
    (is (= (-> vastaus first :nimi) "AB-paikkaus levittäjällä"))
    (is (= (-> vastaus second :nimi) "PAB-paikkaus levittäjällä"))))


(deftest tallennus-paivitys-ja-poisto-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        ulkoinen-id 6363336
        toteumien-maara 5
        haku-params {:tr nil
                     :aikavali nil
                     :urakka-id urakka-id}
        ;; Hae tiedot ennen uuden toteuman tekoa
        vastaus-ennen (tee-kutsu haku-params :hae-reikapaikkaukset)
        ;; Uuden toteuman parametrit 
        params {:luoja-id 1
                :urakka-id urakka-id
                :tunniste ulkoinen-id
                :tie 20
                :aosa 1
                :aet 1200
                :losa 1
                :luotu nil
                :alkuaika nil
                :loppuaika nil
                :let 1300
                :yksikko "m2"
                :tyomenetelma-id 1
                :maara 123
                :kustannus 1234.5}

        ;; Tallenna uusi reikäpaikkaus
        _ (tee-kutsu params :tallenna-reikapaikkaus)

        ;; Hae uudet tulokset 
        vastaus-lisatty (tee-kutsu haku-params :hae-reikapaikkaukset)

        ;; Ennen lisäystä
        _ (is (= (-> vastaus-ennen count) toteumien-maara))
        ;; Lisäyksen jälkeen
        _ (is (= (-> vastaus-lisatty count) (inc toteumien-maara)))

        ;; Lisätty toteuma
        _ (is (= (-> vastaus-lisatty first :aosa) 1))
        _ (is (= (-> vastaus-lisatty first :kustannus) 1234.5M))
        _ (is (= (-> vastaus-lisatty first :tie) 20))
        _ (is (= (-> vastaus-lisatty first :let) 1300))
        _ (is (= (-> vastaus-lisatty first :losa) 1))
        _ (is (= (-> vastaus-lisatty first :aet) 1200))
        _ (is (= (-> vastaus-lisatty first :tyomenetelma) 1))
        _ (is (= (-> vastaus-lisatty first :tyomenetelma-nimi) "AB-paikkaus levittäjällä"))
        _ (is (= (-> vastaus-lisatty first :reikapaikkaus-yksikko) "m2"))

        ;; Muokkaa yllä olevaa paikkausta 
        params-muokkaa (assoc params :let 1600)
        _ (tee-kutsu params-muokkaa :tallenna-reikapaikkaus)

        ;; Katso että toteumaa muokattiin
        vastaus-muokattu (tee-kutsu haku-params :hae-reikapaikkaukset)
        _ (is (= (-> vastaus-muokattu first :let) 1600))

        ;; Poista toteuma
        params {:kayttaja-id 1
                :urakka-id urakka-id
                :ulkoinen-id ulkoinen-id}
        
        _ (tee-kutsu params :poista-reikapaikkaus)
        vastaus-poistettu (tee-kutsu haku-params :hae-reikapaikkaukset)

        ;; Toteumia pitäisi olla taas 5
        _ (is (= (-> vastaus-poistettu count) toteumien-maara))]))


(deftest testaa-reikapaikkaus-excel-validointi
  (let [workbook (xls/load-workbook-from-file "test/resurssit/excel/reikapaikkaus_tuonti_fail.xlsx")
        vastaus (p-excel/parsi-syotetyt-reikapaikkaukset workbook)]
    
    (is (empty? (-> vastaus first :virhe)))
    (is (empty? (-> vastaus second :virhe)))
    (is (empty? (-> vastaus (nth 2) :virhe)))
    (is (empty? (-> vastaus (nth 3) :virhe)))

    (is (= (-> vastaus (nth 4) :virhe) "Rivillä 8 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 5) :virhe) "Rivillä 9 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 6) :virhe) "Rivillä 10 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 7) :virhe) "Rivillä 11 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 8) :virhe) "Rivillä 12 syötetty tunniste on jo olemassa: 1300002"))
    (is (= (-> vastaus (nth 9) :virhe) "Rivillä 13 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 10) :virhe) "Rivillä 14 aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))
    (is (= (-> vastaus (nth 11) :virhe) "Rivillä 15 kustannus ja määrä pitää olla joko kokonaisluku tai desimaaliluku."))
    (is (= (-> vastaus (nth 12) :virhe) "Rivillä 16 kustannus ja määrä pitää olla joko kokonaisluku tai desimaaliluku."))
    (is (= (-> vastaus (nth 13) :virhe)
           (str "Rivillä 17 on tyhjiä kenttiä: " "[\"pvm\" \"aosa\" \"tie\" \"let\" \"losa\" \"aet\" \"maara\" \"tunniste\"]")))))


(deftest testaa-reikapaikkaus-excel-tuonti
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        haku-params {:tr nil
                     :aikavali nil
                     :urakka-id urakka-id}

        workbook (xls/load-workbook-from-file "test/resurssit/excel/reikapaikkaus_tuonti.xlsx")
        vastaus (p-excel/parsi-syotetyt-reikapaikkaukset workbook)

        ;; Wipetä kaikki reikäpaikkaukset
        _ (u (str "DELETE FROM paikkaus WHERE \"paikkaus-tyyppi\" = 'reikapaikkaus'"))
        vastaus-tyhja (tee-kutsu haku-params :hae-reikapaikkaukset)

        ;; Tallenna Excelistä luetut ja valitoidut rivit 
        _ (reikapaikkaukset/tallenna-reikapaikkaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id vastaus)
        ;; Hae uudet paikkaukset 
        vastaus-tuotu (tee-kutsu haku-params :hae-reikapaikkaukset)]

    ;; Virheitä ei pitäisi olla 
    (is (every? #(nil? (:virhe %)) vastaus))
    ;; Taulun pitäisi olla tyhjä reikäpaikkauksilta
    (is (= (-> vastaus-tyhja count) 0))
    ;; Taulussa on paikkauksia tuonnin jälkeen
    (is (= (-> vastaus-tuotu count) 5))
    
    ;; Katso vielä että data on oikein, ainakin ensimmäinen rivi
    (is (= (-> vastaus-tuotu first :aosa) 1))
    (is (= (-> vastaus-tuotu first :kustannus) 1500M))
    (is (= (-> vastaus-tuotu first :tie) 20))
    (is (= (-> vastaus-tuotu first :let) 1020))
    (is (= (-> vastaus-tuotu first :losa) 1))
    (is (= (-> vastaus-tuotu first :aet) 860))
    (is (= (-> vastaus-tuotu first :tyomenetelma) 9))
    (is (= (-> vastaus-tuotu first :tyomenetelma-nimi) "Jyrsintäkorjaukset (HJYR/TJYR)"))
    (is (= (-> vastaus-tuotu first :reikapaikkaus-yksikko) "kpl"))))
