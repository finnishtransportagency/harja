(ns harja.palvelin.integraatiot.api.talvihoitoreitti-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.talvihoitoreitit-api :as api-talvihoitoreitit]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [cheshire.core :as cheshire]))

(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-talvihoitoreitit (component/using
                            (api-talvihoitoreitit/->TalvihoitoreittiAPI)
                            [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn poista-talvihoitoreitit-urakalta [urakka_id]
  (u (str "DELETE FROM talvihoitoreitti WHERE urakka_id = " urakka_id)))

(deftest tallenna-talvihoitoreitti-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id 123456
        lahetysdata (-> "test/resurssit/api/talvihoitoreitti-ok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" "testinimi"))
        vastaus-lisays (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)
        _ (println "vastaus-lisays" vastaus-lisays)]
    (is (= 200 (:status vastaus-lisays)))
    (let [talvihoitoreitti-kannassa (q-map (format "SELECT nimi, luoja, luotu FROM talvihoitoreitti WHERE urakka_id = %s " urakka-id))]
      (is (= 1 (count talvihoitoreitti-kannassa))))))

(deftest paivita-talvihoitoreitti-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id 123456
        nimi "Nelostien pikkumutka"
        lahetysdata (-> "test/resurssit/api/talvihoitoreitti-ok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" (str nimi)))
        vastaus-lisays (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)]
    (is (= 200 (:status vastaus-lisays)))
    (let [talvihoitoreitti-kannassa (q-map (format "SELECT nimi, luoja, luotu FROM talvihoitoreitti WHERE urakka_id = %s " urakka-id))]
      (is (= 1 (count talvihoitoreitti-kannassa))))

    ;; Päivitetään talvihoitoreitti
    (let [nimi "uusinimi"
          lahetysdata (-> "test/resurssit/api/talvihoitoreitti-ok.json"
                        slurp
                        (.replace "__ULKOINENID__" (str ulkoinen-id))
                        (.replace "__NIMI__" (str nimi)))
          vastaus-paivitys (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                             lahetysdata)
          ;; Tarkistetaan kannasta, onko nimi muuttunut
          talvihoitoreitti-kannassa (q-map (format "SELECT nimi FROM talvihoitoreitti
          WHERE urakka_id = %s AND ulkoinen_id = '%s' " urakka-id ulkoinen-id))]
      (is (= nimi (:nimi (first talvihoitoreitti-kannassa)))))))


(deftest poista-talvihoitoreitti-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ;; Muodostetaan 2 erilaista talvihoitoreittiä
        ulkoinen-id1 123456
        ulkoinen-id2 654321
        nimi1 "Nelostien pikkumutka"
        nimi2 "Kotimaan lehväpolku"
        lahetysdata1 (-> "test/resurssit/api/talvihoitoreitti-ok.json"
                       slurp
                       (.replace "__ULKOINENID__" (str ulkoinen-id1))
                       (.replace "__NIMI__" (str nimi1)))
        lahetysdata2 (-> "test/resurssit/api/talvihoitoreitti-ok.json"
                       slurp
                       (.replace "__ULKOINENID__" (str ulkoinen-id2))
                       (.replace "__NIMI__" (str nimi2)))
        vastaus-lisays1 (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                          lahetysdata1)
        vastaus-lisays2 (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                          lahetysdata2)
        ;; HAetaan kannasta ja varmistetaan, että lisäys on onnistunut
        talvihoitoreitti-kannassa (q-map (format "SELECT nimi FROM talvihoitoreitti
          WHERE urakka_id = %s" urakka-id))
        _ (is (= 2 (count talvihoitoreitti-kannassa)))


        ;; Poistetaan toinen talvihoitoreitti
        delete-json (cheshire/encode {:talvihoitoreittien-tunnisteet [(str ulkoinen-id1) (str ulkoinen-id2)]})
        _ (println "delete-json" delete-json)
        vastaus-poisto (tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti" ] kayttaja-yit portti
                         delete-json)
        _ (println "vastaus-poisto" vastaus-poisto)
        ;; Tarkistetaan, että toinen talvihoitoreitti on poistettu
        talvihoitoreitti-kannassa (q-map (format "SELECT nimi FROM talvihoitoreitti
          WHERE urakka_id = %s" urakka-id))
        _ (is (= 0 (count talvihoitoreitti-kannassa)))]))

(deftest tallenna-talvihoitoreitti-epaonnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id 123456
        lahetysdata (-> "test/resurssit/api/talvihoitoreitti-nok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" "testinimi"))
        vastaus (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        virhe (first (:virheet encoodattu-body))]

    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (get-in virhe [:virhe :koodi])))
    (is (.contains (get-in virhe [:virhe :viesti]) "hoitoluokka"))
    (is (.contains (get-in virhe [:virhe :viesti]) ":error :invalid-enum-value"))
    (is (.contains (get-in virhe [:virhe :viesti]) "tie: Väärä tyyppi"))))





