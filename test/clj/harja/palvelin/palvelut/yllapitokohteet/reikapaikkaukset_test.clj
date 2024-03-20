(ns harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.set :as set]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset :as reikapaikkaukset]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [clojure.java.io :as io]
            [harja.kyselyt.reikapaikkaukset :as q]
            [harja.kyselyt.konversio :as konv]))


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
    (is (= (-> vastaus first :kustannus) 215000.0M))
    (is (= (-> vastaus first :tie) 20))
    (is (= (-> vastaus first :let) 1020))
    (is (= (-> vastaus first :losa) 1))
    (is (= (-> vastaus first :aet) 860))
    (is (= (-> vastaus first :tyomenetelma) 8))
    (is (= (-> vastaus first :maara) 81))
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
                :ulkoinen-id ulkoinen-id
                :tie 20
                :aosa 1
                :aet 1200
                :losa 1
                :luotu nil
                :alkuaika nil
                :loppuaika nil
                :let 1300
                :yksikko "m2"
                :menetelma 1
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
