(ns harja.palvelin.integraatiot.api.talvihoitoreitti-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.api.talvihoitoreitit-api :as api-talvihoitoreitit]
            [harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-palvelu :as talvihoitoreitit-palvelu]
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

(defn http-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :talvihoitoreitit (component/using
                              (talvihoitoreitit-palvelu/->Talvihoitoreitit)
                              [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture http-fixture)

(defn poista-talvihoitoreitit-urakalta [urakka_id]
  (u (str "DELETE FROM talvihoitoreitti WHERE urakka_id = " urakka_id)))

(deftest tallenna-talvihoitoreitti-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (str 123456)
        lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-ok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" "testinimi"))
        vastaus-lisays (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)
        dekoodattu-body (cheshire/decode (:body vastaus-lisays) true)]
    
    ;; Status on oikein
    (is (= 200 (:status vastaus-lisays)))
    ;; Palautetaan kutsujalle ulkoinen id
    (is (= ulkoinen-id (:id dekoodattu-body)))

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
        lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-ok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" (str nimi)))
        vastaus-lisays (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)]
    (is (= 200 (:status vastaus-lisays)))
    (let [talvihoitoreitti-kannassa (q-map (format "SELECT nimi, luoja, luotu FROM talvihoitoreitti WHERE urakka_id = %s " urakka-id))]
      (is (= 1 (count talvihoitoreitti-kannassa))))

    ;; Päivitetään talvihoitoreitti
    (let [uusinimi "uusinimi"
          lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-ok.json"
                        slurp
                        (.replace "__ULKOINENID__" (str ulkoinen-id))
                        (.replace "__NIMI__" (str uusinimi)))
          vastaus-paivitys (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                             lahetysdata)
          ;; Tarkistetaan kannasta, onko nimi muuttunut
          talvihoitoreitti-kannassa (q-map (format "SELECT nimi FROM talvihoitoreitti
          WHERE urakka_id = %s AND ulkoinen_id = '%s' " urakka-id ulkoinen-id))]
      (is (= uusinimi (:nimi (first talvihoitoreitti-kannassa)))))))


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
        lahetysdata1 (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-ok.json"
                       slurp
                       (.replace "__ULKOINENID__" (str ulkoinen-id1))
                       (.replace "__NIMI__" (str nimi1)))
        lahetysdata2 (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti2-ok.json"
                       slurp
                       (.replace "__ULKOINENID__" (str ulkoinen-id2))
                       (.replace "__NIMI__" (str nimi2)))
        vastaus-lisays1 (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                          lahetysdata1)
        vastaus-lisays2 (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                          lahetysdata2)
        ;; HAetaan kannasta ja varmistetaan, että lisäys on onnistunut
        talvihoitoreitti-kannassa (q-map (format "SELECT id, nimi FROM talvihoitoreitti
          WHERE urakka_id = %s" urakka-id))
        _ (is (= 2 (count talvihoitoreitti-kannassa)))

        ;; Poistetaan toinen talvihoitoreitti
        delete-json (cheshire/encode {:talvihoitoreittien-tunnisteet [(str ulkoinen-id1) (str ulkoinen-id2)]})
        vastaus-poisto (tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti" ] kayttaja-yit portti
                         delete-json)
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
        lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-nok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" "testinimi"))
        vastaus (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)
        dekoodattu-body (cheshire/decode (:body vastaus) true)
        virhe (first (:virheet dekoodattu-body))]

    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (get-in virhe [:virhe :koodi])))
    (is (.contains (get-in virhe [:virhe :viesti]) "hoitoluokka"))
    (is (.contains (get-in virhe [:virhe :viesti]) ":error :invalid-enum-value"))
    (is (.contains (get-in virhe [:virhe :viesti]) "tie: Väärä tyyppi"))))

(deftest tallenna-talvihoitoreitti-epaonnistuu-vaara-tieosoite
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id 123456
        lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-vaara-tieosoite.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" "testinimi"))
        vastaus (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                  lahetysdata)
        dekoodattu-body (cheshire/decode (:body vastaus) true)
        virhe (first (:virheet dekoodattu-body))]

    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (get-in virhe [:virhe :koodi])))
    (is (.contains (str (get-in virhe [:virhe :viesti])) "Tiellä 1 ei ole tieosaa 40"))))


;; Käyttöliittymästä tehtävät haut eivät välttämättä kuulu tänne, mutta niitä on mahdoton tehdä ilman dataa
;; Joten käytetään API-kutsuja datan luomiseksi ja varmistetaan, että käyttöliittymän käyttävät endpointit
;; hakevat tiedot oikein
;; Luodaan testi, joka lisää APIn kautta talvihoitoreitin ja se haetaan käyttöliittymälle
(deftest tallenna-talvihoitoreitti-ja-hae-kayttoliittymalle
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Siivotaan kanta varulta
        _ (poista-talvihoitoreitit-urakalta urakka-id)
        ;; Anna oikeudet käyttäjälle
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (str 123456)
        reittinimi "testinimi"
        lahetysdata (-> "test/resurssit/api/talvihoitoreitit/talvihoitoreitti-ok.json"
                      slurp
                      (.replace "__ULKOINENID__" (str ulkoinen-id))
                      (.replace "__NIMI__" reittinimi))
        vastaus-lisays (tyokalut/put-kutsu ["/api/urakat/" urakka-id "/talvihoitoreitti"] kayttaja-yit portti
                         lahetysdata)
        ;; Tarkistetaan, että lisäys on onnistunut
        talvihoitoreitti-kannassa (first (q-map (format "SELECT nimi FROM talvihoitoreitti
          WHERE urakka_id = %s" urakka-id)))
        _ (is (= reittinimi (:nimi talvihoitoreitti-kannassa)))

        ;; Haetaan talvihoitoreitti käyttöliittymälle
        vastaus-haku (kutsu-palvelua (:http-palvelin jarjestelma) :hae-urakan-talvihoitoreitit +kayttaja-jvh+ {:urakka-id urakka-id})
        reittien-laskettu-pituus (reduce + (map :laskettu_pituus (mapcat :reitit vastaus-haku)))
        hoitoluokkien-laskettu-pituus (reduce + (map :pituus (mapcat :hoitoluokat vastaus-haku)))
        reittien-annettu-pituus (reduce + (map :pituus (mapcat :reitit vastaus-haku)))]

    (is (= 30.5 reittien-annettu-pituus))
    (is (not= reittien-laskettu-pituus reittien-annettu-pituus))
    (is (= reittien-laskettu-pituus hoitoluokkien-laskettu-pituus))
    (is (= reittinimi (:nimi (first vastaus-haku))))
    (is (nil? (:muokkaaja (first vastaus-haku))))
    (is (nil? (:muokattu (first vastaus-haku))))
    (is (= urakka-id (:urakka_id (first vastaus-haku))))
    (is (= ulkoinen-id (:ulkoinen_id (first vastaus-haku))))
    (is (= 2 (count (:kalustot (first vastaus-haku)))))

    ;; Kuorma-autot
    (is (= 7 (get-in (first vastaus-haku) [:kalustot 0 :kalustomaara])))
    ;; Traktorit
    (is (= 3 (get-in (first vastaus-haku) [:kalustot 1 :kalustomaara])))
    ;; Hoitoluokat
    (is (= 2 (count (:hoitoluokat (first vastaus-haku)))))
    (is (= "IsE" (get-in (first vastaus-haku) [:hoitoluokat 0 :hoitoluokka])))
    (is (= 17.672 (get-in (first vastaus-haku) [:hoitoluokat 0 :pituus]))) ;; Tämä on eri kuin annettu pituus. Pituus lasketaan geometriasta
    (is (= "Ib" (get-in (first vastaus-haku) [:hoitoluokat 1 :hoitoluokka])))
    (is (= 43.416 (get-in (first vastaus-haku) [:hoitoluokat 1 :pituus])))))


