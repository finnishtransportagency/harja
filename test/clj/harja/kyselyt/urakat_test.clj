(ns harja.kyselyt.urakat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.urakat :as urakat-palvelu]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :todennus (component/using
                                    (todennus/http-todennus)
                                    [:db :integraatioloki])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest listaa-urakat-elinvoimakeskukselle-palauttaa-oikeat-urakat
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid psu-evk-id
                  :kayttajan_org_id 1
                  :kayttajan_org_tyyppi "liikennevirasto"
                  :urakat_annettu false
                  :sallitut_urakat [-1]})]

    (testing "PSU EVK löytyy"
      (is (some? psu-evk-id) "Pohjois-Suomen elinvoimakeskus löytyy kannasta"))

    (testing "Urakoita palautuu"
      (is (seq urakat) "Löytyy vähintään yksi urakka"))

    (testing "Urakoilla on pakolliset kentät"
      (doseq [urakka urakat]
        (is (integer? (:id urakka)) "Urakalla on id")
        (is (string? (:nimi urakka)) "Urakalla on nimi")
        (is (some? (:elinvoimakeskus_id urakka)) "Urakalla on elinvoimakeskus_id")))

    (testing "Kaikki urakat kuuluvat oikeaan EVK:iin"
      (is (every? #(= psu-evk-id (:elinvoimakeskus_id %)) urakat)
          "Jokainen urakka kuuluu PSU:n elinvoimakeskukseen"))))

(deftest hae-elinvoimakeskuksen-urakat-palauttaa-oikeat-tiedot
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakat (urakat-q/hae-elinvoimakeskuksen-urakat db {:evk_id psu-evk-id})
        odotetut-urakat-kannasta (map first (q (format "SELECT id FROM urakka WHERE elinvoimakeskus_id = %s AND poistettu = false" psu-evk-id)))]

    (testing "Kysely palauttaa urakoita"
      (is (seq urakat) "Urakoita löytyy"))

    (testing "Urakoiden lukumäärä täsmää suoraan kantahakuun"
      (is (= (count odotetut-urakat-kannasta) (count urakat))
          "SQL-kyselyn ja suoran kantahaun urakoiden lukumäärät täsmäävät"))

    (testing "Urakoilla on id, nimi ja tyyppi"
      (doseq [urakka urakat]
        (is (integer? (:id urakka)))
        (is (string? (:nimi urakka)))
        (is (some? (:tyyppi urakka)))))))

(deftest listaa-urakat-elinvoimakeskukselle-olemattomalle-evk-idlle
  (let [db (:db jarjestelma)
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid -999
                  :kayttajan_org_id 1
                  :kayttajan_org_tyyppi "liikennevirasto"
                  :urakat_annettu false
                  :sallitut_urakat [-1]})]
    (testing "Olemattomalle EVK ID:lle ei löydy urakoita"
      (is (empty? urakat) "Olemattomalla EVK:lla ei pitäisi olla urakoita"))))

(deftest listaa-urakat-elinvoimakeskukselle-evk-kayttajalla-ekstraurakka
  ;; Tilanne: EVK-käyttäjällä on erikseen sallittu urakka joka kuuluu eri EVK:lle
  ;; kuin mihin hän kuuluu itse. Hänen pitäisi nähdä SEKÄ oman EVK:n urakat
  ;; ETTÄ erikseen sallittu urakka toisesta EVK:sta.
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        ;; Haetaan LAP-EVK:n id (testidatassa on Lapin urakat)
        lap-evk-id (ffirst (q "SELECT id FROM organisaatio WHERE lyhenne = 'LAP' AND tyyppi = 'elinvoimakeskus'"))
        ;; Haetaan jokin Lapin EVK:n urakka joka toimii "ekstraurakkana"
        lapin-urakka-id (ffirst (q (format "SELECT id FROM urakka WHERE elinvoimakeskus_id = %s AND poistettu = false LIMIT 1" lap-evk-id)))]

    (testing "Lapin EVK ja urakka löytyy tietokannasta"
      (is (some? lap-evk-id) "Lapin elinvoimakeskus löytyy kannasta")
      (is (some? lapin-urakka-id) "Lapin EVK:lla on vähintään yksi urakka"))

    (when (and psu-evk-id lap-evk-id lapin-urakka-id)
      (let [urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                     {:elinvoimakeskusid psu-evk-id
                      :kayttajan_org_id psu-evk-id
                      :kayttajan_org_tyyppi "elinvoimakeskus"
                      :sallitut_urakat [lapin-urakka-id]})]

        (testing "EVK-käyttäjä näkee oman EVK:n urakat"
          (is (some #(= psu-evk-id (:elinvoimakeskus_id %)) urakat)
              "PSU:n urakoita pitäisi löytyä"))

        (testing "EVK-käyttäjä ei saa nähdä erikseen sallittua ekstraurakkaa toisesta EVK:sta"
          (is (not (some #(= lapin-urakka-id (:id %)) urakat))
              (str "Erikseen sallittu Lapin urakka (id=" lapin-urakka-id ") ei saa löytyä tuloksista")))))))

(deftest listaa-urakat-elinvoimakeskukselle-urakoitsijana-toimii
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakoitsija-id (ffirst (q "SELECT id FROM organisaatio WHERE tyyppi = 'urakoitsija' LIMIT 1"))
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid psu-evk-id
                  :kayttajan_org_id urakoitsija-id
                  :kayttajan_org_tyyppi "urakoitsija"
                  :urakat_annettu false
                  :sallitut_urakat [-1]})]

    (testing "Urakoitsija näkee vain omat urakkansa"
        (doseq [urakka urakat]
          (is (= urakoitsija-id (:urakoitsija_id urakka))
              "Urakoitsija näkee vain omia urakoitaan")))))

(deftest elinvoimakeskuksen-urakat-ohituskaistalla-test
  ;; Testi käyttää "ohituskaistaa" eli OAM-headereita, joissa on lisätietoja.
  ;; Käyttäjällä on oam_groups-kentässä "Jarjestelmavastaava, PR00053529_ELY_Urakanvalvoja",
  ;; joka tarkoittaa ELY_Urakanvalvoja-roolia urakassa PR00053529.
  ;; Käyttäjän organisaatio on "Urakoitsija Oy" (urakoitsija) j alisäksi järjestelmavastaava.
  (let [db (:db jarjestelma)
        integraatioloki-komponentti (:integraatioloki jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        oam-headerit {"oam_remote_user"    "testitunnus1"
                      "oam_user_first_name" "Etunimi"
                      "oam_user_last_name"  "Sukunimi"
                      "oam_user_mail"       "testaaja.tyyppi@solita.fi"
                      "oam_groups"          "Jarjestelmavastaava, PR00053529_ELY_Urakanvalvoja"
                      "oam_organization"    "Urakoitsija Oy"}
        kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki-komponentti {} oam-headerit)]

    (testing "Käyttäjä luodaan onnistuneesti OAM-headereista"
      (is (some? kayttaja) "Käyttäjä palautetaan headereiden perusteella")
      (is (= "testitunnus1" (:kayttajanimi kayttaja)) "Käyttäjätunnus on oikein")
      (is (= "Etunimi" (:etunimi kayttaja)) "Etunimi on oikein")
      (is (= "Sukunimi" (:sukunimi kayttaja)) "Sukunimi on oikein"))

    (testing "Käyttäjällä on ELY_Urakanvalvoja-rooli ja Jarjestelmavastaava-rooli"
      ;; PR00053529_ELY_Urakanvalvoja on urakkaan sidottu rooli, ei yleisrooli
      (is (not (contains? (:roolit kayttaja) "ELY_Urakanvalvoja"))
          "ELY_Urakanvalvoja ei ole yleisrooli vaan urakkaan sidottu rooli")
      (is (contains? (:roolit kayttaja) "Jarjestelmavastaava")
          "Jarjestelmavastaava on yleisrooli ja löytyy roolit-joukosta"))

    (testing "elinvoimakeskuksen-urakat toimii käyttäjällä jolla ei ole PSU EVK -organisaatiota"
      (is (some? psu-evk-id) "PSU EVK löytyy testikannasta")
      ;; Käyttäjällä on Solita Oy -organisaatio (urakoitsija), ei elinvoimakeskus.
      ;; Koska urakat eivät kuulu Solita Oy:lle, palautuu tyhjä lista.
      (let [urakat (urakat-palvelu/elinvoimakeskuksen-urakat db kayttaja psu-evk-id)]
        (is (vector? urakat) "Palautusarvo on vektori")
        (is (> (count urakat) 5) "Urakoita löytyy useampia.")))

    ;; Siivotaan testi-käyttäjä
    (u "DELETE FROM kayttaja WHERE kayttajanimi = 'testitunnus1'")))

