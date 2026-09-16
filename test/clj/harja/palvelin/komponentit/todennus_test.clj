(ns harja.palvelin.komponentit.todennus-test
  (:require [cheshire.core :as cheshire]
            [clojure.core.cache :as cache]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [clojure.string :as str]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
            [slingshot.slingshot :refer [throw+]]
            [clojure.data.json :as json]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.kayttajat :as kayttaja-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta])
  (:import (org.apache.commons.codec.binary Base64)))

(defn jarjestelma-fixture [testit]
  (alter-var-root
   #'jarjestelma
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

(use-fixtures :once jarjestelma-fixture)

(def testiroolit {"root" {:nimi "root"
                          :kuvaus "Pääkäyttäjä"}
                  "valvoja" {:nimi "valvoja"
                             :kuvaus "Urakan valvoja"
                             :linkki "urakka"}
                  "katsoja" {:nimi "katsoja"
                             :kuvaus "Katsoja"}
                  "urakoitsija" {:nimi "urakoitsija"
                                 :kuvaus "Urakoitsijan käyttäjä"
                                 :linkki "urakoitsija"}
                  "paivystaja" {:nimi "paivystaja"
                                :kuvaus "Urakan päivystäjä"
                                :linkki "urakka"}
                  "Kayttaja" {:nimi "Kayttaja"
                              :kuvaus "Urakoitsijan käyttäjä"
                              :linkki "urakoitsija"}})

(def urakat {"u123" 666})
(def urakoitsijat {"Y123456-7" 42})
(def urakat-monta {"PR00013343" 13343 "PR00014303" 14303})

(def oikeudet (partial todennus/kayttajan-roolit urakat urakoitsijat testiroolit))

(deftest lue-oikeudet-oam-groupsista

  (is (= {:roolit #{"root"} :urakkaroolit {} :organisaatioroolit {}}
         (oikeudet "root")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"valvoja"}} :organisaatioroolit {}}
         (oikeudet "u123_valvoja")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"paivystaja"}}
          :organisaatioroolit {42 #{"urakoitsija"}}}
         (oikeudet "Y123456-7_urakoitsija,u123_paivystaja"))))

(deftest liito-rooli-ei-sekoitu-harja-rooliin
  (is (= {:roolit #{} :urakkaroolit {666 #{"paivystaja"}}
          :organisaatioroolit {42 #{"urakoitsija"}}}
         (oikeudet "Y123456-7_urakoitsija,u123_paivystaja,Extranet_Liito_Kayttaja,Aina_öisin_valvoja"))))

(deftest tilaajan-kayttaja
  (is (= {:roolit             #{"Tilaajan_Kayttaja"}
          :organisaatioroolit {}
          :urakkaroolit       {}}
         (todennus/kayttajan-roolit urakat urakoitsijat oikeudet/roolit "Tilaajan_Kayttaja"))))

(deftest ely-peruskayttaja
  (is (= {:roolit             #{"ELY_Peruskayttaja"}
          :organisaatioroolit {}
          :urakkaroolit       {}}
         (todennus/kayttajan-roolit urakat urakoitsijat oikeudet/roolit "ELY_Peruskayttaja"))))

(deftest ely-urakanvalvoja
  (let [oam-groups "55746,39626,39627,27231,28875,30138,49544,29957,30006,44687,56406,PR00014281_ELY_Urakanvalvoja,PR00013343_ELY_Urakanvalvoja,28311,55550,PR00014258_ELY_Urakanvalvoja,26871,PR00014303_ELY_Urakanvalvoja,51204,49468,49469,28852,30050,30116,28851,PR00014273_ELY_Urakanvalvoja,32065,44594,31866,51805,51804,PR00014248_ELY_Urakanvalvoja,Extranet_Liito_Kayttaja,29778,44556,PR00013356_ELY_Urakanvalvoja,Extranet_Aura_Kayttaja,47074,47075,thuv,51544,51685,51684,55530,54206,56626,PR00014296_ELY_Urakanvalvoja,53865,29726,53864,r,56426,PR00014289_ELY_Urakanvalvoja,PR00014265_ELY_Urakanvalvoja"
        vastaus (todennus/kayttajan-roolit urakat-monta urakoitsijat oikeudet/roolit oam-groups)
        odotetut-roolit {:roolit             #{}
                         :organisaatioroolit {}
                         :urakkaroolit       {14303 #{"ELY_Urakanvalvoja"}
                                              13343 #{"ELY_Urakanvalvoja"}}}]
    (is (= vastaus odotetut-roolit))))

(def default-miam-vastaus "{\"Table1\": [{\"CompanyID\":\"2163026-3\",\"Company\":\"Destia Oy\",\"UserName\":\"LXXX\",\"Name\":\"Firma Oy\",\"Role\": \"1242141-KITT3_vastuuhenkilo\",\"StartDate\": \"9.4.2024 13:01:03\", \"EndDate\": \"31.3.2029 0:00:00\", \"Agreementname\": \"_Organisaatio peruste Destia Oy\",\"Appname\": \"HARJA\", \"email\": \"s.fi\" }
                         ,{\"CompanyID\":\"2163026-3\",\"Company\":\"Destia Oy\",\"UserName\":\"LXYY\",\"Name\":\"Destia Oy\",\"Role\": \"2163026-3_Paakayttaja\", \"StartDate\": \"2016-10-14 09:57:23\", \"EndDate\": \"2027-12-30 17:00:00\", \"Agreementname\": \"E18 (Vt7) Koskenkylä-Kotka, kunnossapito, P\", \"Appname\": \"HARJA\", \"email\": \"...fi\" }]}")

(def semi-virheellinen-miam-vastaus "{\"Table1\": [{\"CompanyID\":\"2163026-3\",\"Company\":\"Destia Oy\",\"UserName\":\"LXXX\",\"Name\":\"Firma Oy\",\"Role\": \"\",\"StartDate\": \"9.4.2024 13:01:03\", \"EndDate\": \"31.3.2029 0:00:00\", \"Agreementname\": \"_Organisaatio peruste Destia Oy\",\"Appname\": \"HARJA\", \"email\": \"s.fi\" }
                         ,{\"CompanyID\":\"2163026-3\",\"Company\":\"Destia Oy\",\"UserName\":\"LXYY\",\"Name\":\"Destia Oy\", \"StartDate\": \"2016-10-14 09:57:23\", \"EndDate\": \"2027-12-30 17:00:00\", \"Agreementname\": \"E18 (Vt7) Koskenkylä-Kotka, kunnossapito, P\", \"Appname\": \"HARJA\", \"email\": \"...fi\" }]}")

(deftest siivoa-roolit-rajapintavastauksesta-test
  (let [data (json/read-str default-miam-vastaus :key-fn keyword)
        table1 (get data :Table1)
        siivottu-json-data (if (and table1 (sequential? table1))
                             (let [siivottu-table1 (mapv #(dissoc % :Name :email) table1)
                                   siivottu-data (assoc data :Table1 siivottu-table1)]
                               (json/write-str siivottu-data))
                             (json/write-str data))]
    ;; Sisältää emailin
    (is (re-find #"email" default-miam-vastaus))
    ;; Ei sisällä emailia
    (is (not (re-find #"email" siivottu-json-data)))))

(deftest kayttajaroolit-rajapinnasta-test
  (is (= {:organisaatioroolit {26 #{"Paakayttaja"}}
          :roolit #{}
          :urakkaroolit {39 #{"vastuuhenkilo"}}}
        (todennus/kayttajaroolit-rajapintavastauksesta (:db jarjestelma)
          default-miam-vastaus
          nil))))

(deftest kayttajaroolit-rajapintavastauksesta-ei-kaadu-virheellisilla-vastauksilla
  (testing "MIAM-vastauksen parsinta ei kaadu nil/tyhjä/virheellinen JSON"
    (let [db (:db jarjestelma)]
      (is (nil? (todennus/kayttajaroolit-rajapintavastauksesta db nil nil)))
      (is (nil? (todennus/kayttajaroolit-rajapintavastauksesta db "" nil)))
      (is (nil? (todennus/kayttajaroolit-rajapintavastauksesta db "not-json" nil)))
      (is (nil? (todennus/kayttajaroolit-rajapintavastauksesta db "{}" nil)))
      (is (nil? (todennus/kayttajaroolit-rajapintavastauksesta db "{\"Table1\":null}" nil)))
      (is (= {:roolit #{}, :urakkaroolit {}, :organisaatioroolit {}}
            (todennus/kayttajaroolit-rajapintavastauksesta db "{\"Table1\":[{\"Role\":null}]}" nil))))))

(deftest kayttajaroolit-rajapintavastauksesta-ja-asetuksista
  (is (= {:organisaatioroolit {26 #{"Paakayttaja"}}
          :roolit #{"Jarjestelmavastaava", "Tilaajan_Asiantuntija"}
          :urakkaroolit {39 #{"vastuuhenkilo"}}}
        (todennus/kayttajaroolit-rajapintavastauksesta (:db jarjestelma)
          default-miam-vastaus
          "Jarjestelmavastaava,Tilaajan_Asiantuntija"))))

(deftest kayttajaroolit-virheellisesta-rajapintavastauksesta
  (is (= {:organisaatioroolit {}
          :roolit #{"Jarjestelmavastaava"
                    "Tilaajan_Asiantuntija"}
          :urakkaroolit {}}
        (todennus/kayttajaroolit-rajapintavastauksesta (:db jarjestelma)
          semi-virheellinen-miam-vastaus
          "Jarjestelmavastaava,Tilaajan_Asiantuntija"))))

(deftest miam-virhetilanteet
  (testing "MIAM palauttaa 401"
    (with-redefs [integraatiotapahtuma/laheta
                  (fn [_ _ _] {:status 401 :body "Unauthorized"})]
      (is (nil? (todennus/hae-kayttajaroolit-rajapinnasta (:db jarjestelma)
                  (:integraatioloki jarjestelma)
                  {:timeout 100
                   :max-yritykset 2
                   :sleep-ms 100}
                  "feikki-kayttaja")))))

  (testing "MIAM palauttaa viallisen JSON:n"
    (with-redefs [integraatiotapahtuma/laheta
                  (fn [_ _ _] {:status 200 :body "{invalid json"})]
      (is (nil? (todennus/hae-kayttajaroolit-rajapinnasta (:db jarjestelma)
                  (:integraatioloki jarjestelma)
                  {:timeout 100
                   :max-yritykset 2
                   :sleep-ms 100}
                  "feikki-kayttaja"))))))

(deftest miam-uudelleenyritys-with-redefs-test
  (let [yrityskerrat (atom 0)
        mock-integraatio (fn [db integraatioloki nimi toiminto ulkoinen-id context-fn]
                           (swap! yrityskerrat inc)
                           (if (< @yrityskerrat 5)
                             nil
                             (context-fn {})))]

    (with-redefs [integraatiotapahtuma/suorita-integraatio mock-integraatio
                  integraatiotapahtuma/laheta (fn [konteksti http http-asetukset] {:body default-miam-vastaus :headers "headers" :status 200})]
      (let [vastaus (todennus/hae-kayttajaroolit-rajapinnasta
                      (:db jarjestelma)
                      (:integraatioloki jarjestelma)
                      {:timeout 500
                       :max-yritykset 5
                       :sleep-ms 100}
                      "feikki-kayttaja")]
        (is (= 5 @yrityskerrat) "Viisi yritystä tehtiin")
        (is (some? vastaus) "Vastaus saatiin lopulta")))))

(def testi-cognito-headerit-entraid
  [{"typ" "JWT"
    "kid" "7d2ed764-76dd-44c3-b4cf-8cde89fe6e5f"
    "alg" "ES256"
    "iss" "https://cognito-idp.eu-west-1.amazonaws.com/foobar"
    "client" "3ctc20d3i4ghv34ks0semt4e16"
    "signer" "arn:aws:elasticloadbalancing:eu-west-1:083539282917:loadbalancer/app/foobar/8dad8bb767eb8568"
    "exp" 1687175356}
   {"custom:rooli" (json/write-str ["Jarjestelmavastaava" "MHU-TESTI-LAP-ROV_vastuuhenkilo" "MHU-TESTI-LAP-IVA_vastuuhenkilo"])
    "custom:sukunimi" "tonttu"
    "email" "toni@tonttu.com"
    "custom:uid" "Jarjestelmavastaava"
    "custom:organisaatio" "Liikennevirasto"
    "custom:etunimi" "toni"}
   ;; Tämä on vain signature. Ei relevantti näiden testien kannalta tällä hetkellä.
   "TDZJ0uQA-H2GEfw38cVc-OS8gAsRVlW_EyPojJOtLKbqMalXUcq59BFB-ZJY1UXmxhdNDX04IEAQs70qa5p2Gw=="])

(def testi-cognito-headerit-oam 
  [{"typ" "JWT"
    "kid" "7d2ed764-76dd-44c3-b4cf-8cde89fe6e5f"
    "alg" "ES256"
    "iss" "https://cognito-idp.eu-west-1.amazonaws.com/foobar"
    "client" "3ctc20d3i4ghv34ks0semt4e16"
    "signer" "arn:aws:elasticloadbalancing:eu-west-1:083539282917:loadbalancer/app/foobar/8dad8bb767eb8568"
    "exp" 1687175356}
   {"custom:rooli" "2234567-8_Paakayttaja"
    "custom:sukunimi" "Destialainen"
    "custom:ytunnus" "2163026-3"
    "email" "daniel@example.com"
    "exp" 1687175356
    "custom:uid" "daniel"
    "custom:puhelin" "1234567890"
    "custom:organisaatio" "Destia Oy"
    "custom:etunimi" "Daniel"}
   ;; Tämä on vain signature. Ei relevantti näiden testien kannalta tällä hetkellä.
   "TDZJ0uQA-H2GEfw38cVc-OS8gAsRVlW_EyPojJOtLKbqMalXUcq59BFB-ZJY1UXmxhdNDX04IEAQs70qa5p2Gw=="])

(defn testi-enkoodaa-payload-jwt [iam-payload]
  (let [x-iam-data iam-payload
        jwt (map #(->
                    %
                    cheshire/encode
                    (.getBytes "UTF-8")
                    Base64/encodeBase64
                    (String. "UTF-8")) [(first x-iam-data) (second x-iam-data)])
        jwt (str (str/join "." jwt) "." (nth x-iam-data 2))]
    {;; Vaaditaan että molemmat tokenit on aina Cognito kutsussa mukana
     "x-iam-data" jwt 
     "x-iam-accesstoken" jwt}))

(deftest cognito-headereiden-purku-oam-ja-entraid
  (let [handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers true))
        todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true) 

        destia-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'")))
        virasto-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'")))]
    
    (testing "Cognito headeri EntraID muodossa: x-iam-data on purettu oikein ja tarvittava OAM-data on saatu"
      (let [req (handler {:headers (testi-enkoodaa-payload-jwt testi-cognito-headerit-entraid)})
            req (todenna req)]
        (is (= (get-in req [:kayttaja :organisaatio :id]) virasto-id))
        (is (= (get-in req [:kayttaja :sahkoposti]) "toni@tonttu.com"))
        (is (= (get-in req [:kayttaja :kayttajanimi]) "Jarjestelmavastaava"))
        (is (= (get-in req [:kayttaja :etunimi]) "toni"))
        (is (= (get-in req [:kayttaja :sukunimi]) "tonttu"))
        (is (= (get-in req [:kayttaja :roolit]) #{"Jarjestelmavastaava"}))
        (is (= (get-in req [:kayttaja :urakkaroolit]) {31 #{"vastuuhenkilo"}, 34 #{"vastuuhenkilo"}}))))
    
    (testing "Cognito headeri OAM muodossa: x-iam-data on purettu oikein ja tarvittava OAM-data on saatu"
      (let [req (handler {:headers (testi-enkoodaa-payload-jwt testi-cognito-headerit-oam)})
            req (todenna req)]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))
        (is (= (get-in req [:kayttaja :sahkoposti]) "daniel@example.com"))
        (is (= (get-in req [:kayttaja :kayttajanimi]) "daniel"))
        (is (= (get-in req [:kayttaja :puhelin]) "1234567890"))
        (is (= (get-in req [:kayttaja :etunimi]) "Daniel"))
        (is (= (get-in req [:kayttaja :sukunimi]) "Destialainen"))
        (is (= (get-in req [:kayttaja :organisaatioroolit]) {33 #{"Paakayttaja"}}))))

    (testing "Käytetään OAM-headereita normaalisti, mikäli ne on määritelty"
      (let [req (todenna {:headers {"oam_remote_user" "daniel"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_user_mail" "daniel@example.com"
                                    "oam_user_mobile" "1234567890"
                                    "oam_organization" "Destia Oy"
                                    "oam_groups" "2234567-8_Paakayttaja"}})]

        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))
        (is (= (get-in req [:kayttaja :sahkoposti]) "daniel@example.com"))
        (is (= (get-in req [:kayttaja :kayttajanimi]) "daniel"))
        (is (= (get-in req [:kayttaja :puhelin]) "1234567890"))
        (is (= (get-in req [:kayttaja :etunimi]) "Daniel"))
        (is (= (get-in req [:kayttaja :sukunimi]) "Destialainen"))
        (is (= (get-in req [:kayttaja :organisaatioroolit]) {33 #{"Paakayttaja"}}))))))

(deftest cognito-headereiden-purku-harja-api-usernamella
  (let [handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers))
        todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true)]

    (testing "Cognito headeri: harja-api-username -headerin arvo löytyy custom:uid-headerin arvon sijaan"
      (let [req (handler {:headers (merge (testi-enkoodaa-payload-jwt testi-cognito-headerit-oam) {"harja-api-username" "LOTTA"}) })
            req (todenna req)]

        (is (= (get-in req [:kayttaja :kayttajanimi]) "LOTTA"))
        (is (= (get-in req [:kayttaja :etunimi]) "Daniel"))
        (is (= (get-in req [:kayttaja :sukunimi]) "Destialainen"))))

    (testing "Koska headereissa saatiin harja-api-username, oam_remote_userista löytyy sen, eikä custom:uid-headerin arvo"
      (let [req (todenna {:headers {"oam_remote_user" "LOTTA"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_groups" "2234567-8_Paakayttaja"}})]

        (is (= (get-in req [:kayttaja :kayttajanimi]) "LOTTA"))
        (is (= (get-in req [:kayttaja :etunimi]) "Daniel"))
        (is (= (get-in req [:kayttaja :sukunimi]) "Destialainen"))))))

(deftest ota-organisaatio-roolin-y-tunnuksesta
  (let [todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true)
        destia-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'")))
        lampunvaihtajat-id (first (first (q "SELECT id FROM organisaatio WHERE ytunnus = '2234567-8'")))]
    (testing "Organisaatio löytyy, jos OAM_ORGANIZATION on annettu oikein"
      (let [req (todenna {:headers {"oam_remote_user" "daniel"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_user_mail" "daniel@example.com"
                                    "oam_user_mobile" "1234567890"
                                    "oam_organization" "Destia Oy"
                                    "oam_groups" ""}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))

    (testing "Jos muuta organisaatiotietoa ei löyty, yritä ottaa se roolin Y-tunnuksesta"
      (let [req (todennus/todenna-pyynto (:todennus jarjestelma)
                                         {:headers {"oam_remote_user" "alpo"
                                                    "oam_user_first_name" "Alpo"
                                                    "oam_user_last_name" "Asfalttimies"
                                                    "oam_user_mail" "alpo@example.com"
                                                    "oam_user_mobile" "1234567890"
                                                    "oam_organization" "Eitällaistaolekaan Oy"
                                                    "oam_groups" "2234567-8_Paakayttaja"}} true)]
        (is (= (get-in req [:kayttaja :organisaatio :id]) lampunvaihtajat-id))))))

(deftest ota-organisaatio-companyid-headerista
  (let [todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true)
        destia-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'")))]
    (testing "Organisaatio löytyy, jos OAM_USER_COMPANYID on annettu oikein vaikka nimi olisi väärä"
      (let [req (todenna {:headers {"oam_remote_user" "daniel"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_user_mail" "daniel@example.com"
                                    "oam_user_mobile" "1234567890"
                                    "oam_organization" "Dezdia Oy"
                                    "oam_user_companyid" "2163026-3"
                                    "oam_groups" ""}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))

    (testing "Organisaatio löytyy edelleen nimen perusteella, jos OAM_USER_COMPANYID:ssä on roskaa"
      (let [req (todennus/todenna-pyynto (:todennus jarjestelma)
                                         {:headers {"oam_remote_user" "alpo"
                                                    "oam_user_first_name" "Alpo"
                                                    "oam_user_last_name" "Asfalttimies"
                                                    "oam_user_mail" "alpo@example.com"
                                                    "oam_user_mobile" "1234567890"
                                                    "oam_organization" "Destia oy"
                                                    "oam_user_companyid" "NOT_FOUND"
                                                    "oam_groups" ""}} true)]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))))

(deftest varmista-kayttajatiedot-test
  (let [db (:db jarjestelma)
        integraatioloki (:integraatioloki jarjestelma)
        miam-asetukset {} ;; Laita asetukset tyhjänä
        testi-kayttajanimi "testi-kayttaja-123"
        testi-headerit {"oam_remote_user" testi-kayttajanimi
                        "oam_user_first_name" "Testi"
                        "oam_user_last_name" "Käyttäjä"
                        "oam_user_mail" "testi@example.com"
                        "oam_user_mobile" "0401234567"
                        "oam_organization" "Destia Oy"
                        "oam_groups" "2234567-8_Paakayttaja"}]

    (testing "Uusi käyttäjä luodaan tietokantaan"
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")

      (let [kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki miam-asetukset testi-headerit)
            kayttaja-kannassa (first (kayttaja-kyselyt/hae-kayttaja-kayttajanimella db {:kayttajanimi testi-kayttajanimi}))]

        (is (some? kayttaja) "Käyttäjä palautetaan")
        (is (some? kayttaja-kannassa) "Käyttäjä löytyy kannasta")
        (is (= testi-kayttajanimi (:kayttajanimi kayttaja)))
        (is (= "Testi" (:etunimi kayttaja)))
        (is (= "Käyttäjä" (:sukunimi kayttaja)))
        (is (= "testi@example.com" (:sahkoposti kayttaja)))
        (is (= "0401234567" (:puhelin kayttaja)))
        (is (= "Destia Oy" (get-in kayttaja [:organisaatio :nimi])) "Organisaatio on asetettu")
        (is (contains? (get-in kayttaja [:organisaatioroolit 33]) "Paakayttaja") "Pääkäyttäjärooli on asetettu")))

    (testing "Olemassa oleva käyttäjä päivitetään jos tiedot muuttuvat"
      (let [muutetut-headerit (assoc testi-headerit
                                "oam_user_first_name" "Muutettu"
                                "oam_user_mail" "uusi@example.com")
            kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki miam-asetukset muutetut-headerit)
            kayttaja-kannassa (first (kayttaja-kyselyt/hae-kayttaja-kayttajanimella db {:kayttajanimi testi-kayttajanimi}))]

        (is (= "Muutettu" (:etunimi kayttaja)))
        (is (= "uusi@example.com" (:sahkoposti kayttaja)))
        (is (= "Muutettu" (:etunimi kayttaja-kannassa)))
        (is (= "uusi@example.com" (:sahkoposti kayttaja-kannassa)))))

    (testing "Elinvoimakeskus-käyttäjä toimii"
      (let [evk-kayttajanimi "testi-evk"
            evk-headerit {"oam_remote_user" evk-kayttajanimi
                            "oam_user_first_name" "ELY"
                            "oam_user_last_name" "Elinvoimakeskus"
                            "oam_user_mail" "testi@example.com"
                            "oam_user_mobile" "0401234567"
                            "oam_organization" "Lappi"
                            "oam_groups" "ELY_Paakayttaja"}
            kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki miam-asetukset evk-headerit)
            kayttaja-kannassa (first (kayttaja-kyselyt/hae-kayttaja-kayttajanimella db {:kayttajanimi evk-kayttajanimi}))]

        (is (some? kayttaja) "Käyttäjä palautetaan")
        (is (some? kayttaja-kannassa) "Käyttäjä löytyy kannasta")
        (is (= evk-kayttajanimi (:kayttajanimi kayttaja)))
        (is (= "ELY" (:etunimi kayttaja)))
        (is (= "Elinvoimakeskus" (:sukunimi kayttaja)))
        (is (= "testi@example.com" (:sahkoposti kayttaja)))
        (is (= "0401234567" (:puhelin kayttaja)))
        (is (= "Lappi" (get-in kayttaja [:organisaatio :nimi])) "Organisaatio on asetettu")))

    ;; Siivoa testi-käyttäjä lopuksi
    (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")))

(deftest varmista-kayttajatiedot-miam-rajapinnasta-test
  (let [db (:db jarjestelma)
        integraatioloki (:integraatioloki jarjestelma)
        ;; MIAM-asetukset kuten ne tulevat asetukset.edn tiedostosta, mutta testi-osoitteilla
        miam-asetukset {:url "https://testi-miam.example.com/api/v1/users"
                        :apiavain "test-api-key-12345"}
        testi-kayttajanimi "miam-testi-kayttaja"
        testi-headerit {"oam_remote_user" testi-kayttajanimi
                        "oam_user_first_name" "MIAM"
                        "oam_user_last_name" "Testaaja"
                        "oam_user_mail" "miam@example.com"
                        "oam_user_mobile" "0501234567"
                        "oam_organization" "Destia Oy"
                        "oam_groups" "Jarjestelmavastaava, 2234567-8_Paakayttaja"}
        ;; Mockattu MIAM-vastaus
        miam-vastaus (cheshire/encode
                       {"Table1" [{"CompanyID" "2163026-3"
                                   "Company" "Destia Oy"
                                   "UserName" testi-kayttajanimi
                                   "Name" "MIAM Testaaja"
                                   "Role" "2234567-8_Paakayttaja"
                                   "StartDate" "1.1.2024 0:00:00"
                                   "EndDate" "31.12.2029 23:59:59"
                                   "Agreementname" "Testisopimus"
                                   "Appname" "HARJA"
                                   "email" "miam@example.com"}]})]

    (testing "MIAM-käyttäjä luodaan kun MIAM-asetukset on määritelty"
      ;; Mockataan HTTP-kysely
      (with-redefs [harja.palvelin.asetukset/ominaisuus-kaytossa? (fn [_] false)
                    todennus/hae-kayttajaroolit-rajapinnasta (fn [db integraatioloki miam kayttajanimi] miam-vastaus)]
        (let [kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki miam-asetukset testi-headerit)
              kayttaja-kannassa (first (kayttaja-kyselyt/hae-kayttaja-kayttajanimella db {:kayttajanimi testi-kayttajanimi}))]

          (is (some? kayttaja) "Käyttäjä palautetaan")
          (is (some? kayttaja-kannassa) "Käyttäjä löytyy kannasta")
          (is (= testi-kayttajanimi (:kayttajanimi kayttaja)))
          (is (= "MIAM" (:etunimi kayttaja)))
          (is (= "Testaaja" (:sukunimi kayttaja)))
          (is (= "miam@example.com" (:sahkoposti kayttaja)))
          (is (= "Destia Oy" (get-in kayttaja [:organisaatio :nimi])) "Organisaatio on asetettu")
          (is (= "Jarjestelmavastaava" (first (:roolit kayttaja))) "Käyttäjä on järjestelmävastaava")))

      ;; Siivoa testi-käyttäjä lopuksi
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'"))))

(deftest varmista-kayttajatiedot-miam-rajapinnasta-test-2
  (let [db (:db jarjestelma)
        integraatioloki (:integraatioloki jarjestelma)
        ;; MIAM-asetukset kuten ne tulevat asetukset.edn tiedostosta, mutta testi-osoitteilla
        miam-asetukset {:url "https://testi-miam.example.com/api/v1/users"
                        :apiavain "test-api-key-12345"}
        testi-kayttajanimi "miam-testi-kayttaja"
        testi-headerit {"oam_remote_user" testi-kayttajanimi
                        "oam_user_first_name" "MIAM"
                        "oam_user_last_name" "Testaaja"
                        "oam_user_mail" "miam@example.com"
                        "oam_user_mobile" "0501234567"
                        "oam_organization" "Destia Oy"
                        "oam_groups" "Jarjestelmavastaava"}
        ;; Mockattu MIAM-vastaus
        miam-vastaus (cheshire/encode {})]

    (testing "Sähkeheaderit mutta ei miamista mitään testi"
      ;; Mockataan HTTP-kysely
      (with-redefs [harja.palvelin.asetukset/ominaisuus-kaytossa? (fn [_] false)
                    todennus/hae-kayttajaroolit-rajapinnasta (fn [db integraatioloki miam kayttajanimi] miam-vastaus)]
        (let [kayttaja (#'todennus/varmista-kayttajatiedot db integraatioloki miam-asetukset testi-headerit)
              kayttaja-kannassa (first (kayttaja-kyselyt/hae-kayttaja-kayttajanimella db {:kayttajanimi testi-kayttajanimi}))]

          (is (some? kayttaja) "Käyttäjä palautetaan")
          (is (some? kayttaja-kannassa) "Käyttäjä löytyy kannasta")
          (is (= testi-kayttajanimi (:kayttajanimi kayttaja)))
          (is (= "MIAM" (:etunimi kayttaja)))
          (is (= "Testaaja" (:sukunimi kayttaja)))
          (is (= "miam@example.com" (:sahkoposti kayttaja)))
          (is (= "Destia Oy" (get-in kayttaja [:organisaatio :nimi])) "Organisaatio on asetettu")
          (is (= "Jarjestelmavastaava" (first (:roolit kayttaja))) "Käyttäjä on järjestelmävastaava")))

      ;; Siivoa testi-käyttäjä lopuksi
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'"))))

(deftest miam-epaonnistuminen-ei-cacheta-kayttajatietoja
  (let [db (:db jarjestelma)
        integraatioloki (:integraatioloki jarjestelma)
        miam-asetukset {:url "https://testi-miam.example.com/api/v1/users"
                        :apiavain "test-api-key-12345"}
        testi-kayttajanimi "miam-cache-testi-kayttaja"
        testi-headerit {"oam_remote_user" testi-kayttajanimi
                        "oam_user_first_name" "MIAM"
                        "oam_user_last_name" "Cachetestaaja"
                        "oam_user_mail" "miam-cache@example.com"
                        "oam_user_mobile" "0501234567"
                        "oam_organization" "Destia Oy"
                        "oam_groups" "Jarjestelmavastaava"}
        miam-kutsut (atom 0)
        miam-vastaus (cheshire/encode
                       {"Table1" [{"CompanyID" "2163026-3"
                                   "Company" "Destia Oy"
                                   "UserName" testi-kayttajanimi
                                   "Name" "MIAM Cachetestaaja"
                                   "Role" "2234567-8_Paakayttaja"
                                   "StartDate" "1.1.2024 0:00:00"
                                   "EndDate" "31.12.2029 23:59:59"
                                   "Agreementname" "Testisopimus"
                                   "Appname" "HARJA"
                                   "email" "miam-cache@example.com"}]})]

    (testing "Epäonnistunut MIAM-haku ei päädy cacheen vaan seuraava yritys hakee tiedot uudelleen"
      (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl 60000))
      (reset! todennus/odottavat-kutsut-atom {})
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")

      (with-redefs [harja.palvelin.asetukset/ominaisuus-kaytossa? (fn [_] false)
                    todennus/hae-kayttajaroolit-rajapinnasta (fn [_ _ _ _]
                                                              (case (swap! miam-kutsut inc)
                                                                1 nil
                                                                miam-vastaus))]
        (let [eka-kutsu (todennus/koka->kayttajatiedot db integraatioloki miam-asetukset testi-headerit nil false nil)
              toka-kutsu (todennus/koka->kayttajatiedot db integraatioloki miam-asetukset testi-headerit nil false nil)]
          (is (nil? eka-kutsu) "Ensimmäinen epäonnistunut MIAM-haku ei saa palauttaa cachettavaa käyttäjää")
          (is (= 2 @miam-kutsut) "Toinen kutsu tekee uuden MIAM-haun eikä käytä epäonnistunutta cachea")
          (is (some? toka-kutsu) "Seuraava onnistunut MIAM-haku palauttaa käyttäjän")
          (is (= testi-kayttajanimi (:kayttajanimi toka-kutsu)))
          (is (= "Destia Oy" (get-in toka-kutsu [:organisaatio :nimi])) "Organisaatio palautuu onnistuneella toisella haulla")
          (is (= #{"Paakayttaja"} (get-in toka-kutsu [:organisaatioroolit 33])) "Organisaatiorooli tulee onnistuneesta MIAM-vastauksesta")
          (is (contains? (:roolit toka-kutsu) "Jarjestelmavastaava") "Header-rooli säilyy mukana onnistuneessa toisessa haussa"))))

    (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl (* 120 60 1000)))
    (reset! todennus/odottavat-kutsut-atom {})
    (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")))

(deftest miam-uudelleen-yritys-logiikka-testi
  (testing "MIAM-kutsu uudelleenyritys timeout/virhe-tilanteessa"
    ;; Tämä testi varmistaa että hae-kayttajaroolit-rajapinnasta yrittää uudelleen
    ;; max-yritykset kertaa kun MIAM-rajapintakutsu epäonnistuu.
    ;; Timeout-tilannetta simuloidaan palauttamalla virheellinen HTTP-statuskoodi (503)
    ;; joka aiheuttaa saman uudelleenyrityslogiikan kuin todellinen timeout.
    (let [yrityskerrat (atom 0)
          mock-integraatio (fn [_ _ _ _ _ context-fn]
                             (swap! yrityskerrat inc)
                             ;; Kutsutaan context-fn ja palautetaan sen tulos
                             (context-fn {}))
          mock-http-laheta (fn [_ _ _]
                             ;; Simuloi epäonnistunut kutsu palauttamalla virheellinen statuskoodi
                             ;; Timeout aiheuttaisi poikkeuksen, mutta virhestatuskin aiheuttaa uudelleenyrityksen
                             {:body "Service unavailable" :headers {} :status 503})]

      (with-redefs [integraatiotapahtuma/suorita-integraatio mock-integraatio
                    integraatiotapahtuma/laheta mock-http-laheta]
        ;; Asetetaan lyhyt timeout ja vähän yrityksiä
        (let [miam-asetukset {:timeout 100 ; 100ms timeout (ei käytetä tässä testissä)
                              :max-yritykset 3
                              :sleep-ms 100}
              tulos (todennus/hae-kayttajaroolit-rajapinnasta
                      (:db jarjestelma)
                      (:integraatioloki jarjestelma)
                      miam-asetukset
                      "timeout-testi-kayttaja")]

          ;; Virheelliset statuskoodit aiheuttavat nil-vastauksen kaikissa yrityksissä
          (is (nil? tulos)
              "Virhe-tilanne pitäisi johtaa nil-vastaukseen")

          ;; Varmistetaan että yritettiin max-yritykset kertaa
          (is (= 3 @yrityskerrat)
              "Pitäisi yrittää uudelleen max-yritykset verran kun virhe tapahtuu"))))))

(deftest samanaikaiset-kayttajatietohaut-test
  (let [db (:db jarjestelma)
        integraatioloki (:integraatioloki jarjestelma)
        miam-asetukset {}
        testi-kayttajanimi "samanaikainen-testi-kayttaja"
        testi-headerit {"oam_remote_user" testi-kayttajanimi
                        "oam_user_first_name" "Testi"
                        "oam_user_last_name" "Käyttäjä"
                        "oam_user_mail" "testi@example.com"
                        "oam_user_mobile" "0401234567"
                        "oam_organization" "Destia Oy"
                        "oam_groups" "Jarjestelmavastaava"}
        kutsulaskuri (atom 0)]

    (testing "Samanaikaiset kutsut aiheuttavat vain yhden tietokantakutsun"
      ;; Siivoa cache ja pending requests
      (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl (* 60000))) ;; minuutti = 60 sek
      (reset! todennus/odottavat-kutsut-atom {})
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")

      ;; Mockataan varmista-kayttajatiedot laskemaan kutsut
      (with-redefs [todennus/varmista-kayttajatiedot
                    (fn [db integraatioloki miam oam-tiedot]
                      (swap! kutsulaskuri inc)
                      (Thread/sleep 100) ; Simuloi hidas kutsu
                      {:kayttajanimi testi-kayttajanimi
                       :etunimi "Testi"
                       :sukunimi "Käyttäjä"})]

        ;; Käynnistä 4 samanaikaista kutsua
        (let [futuurit (doall
                         (for [_ (range 4)]
                           (do
                             (println "Käynnistetään kutsu...")
                             (future
                                 (todennus/koka->kayttajatiedot
                                   db integraatioloki miam-asetukset
                                   testi-headerit nil false)))))]

          ;; Odota että kaikki valmistuvat
          (doseq [f futuurit]
            (do
              (println "Odotellaan timeouttia...")
              (deref f 1000 :timeout)))

          ;; Tarkista että vain yksi kutsu tehtiin
          (is (= 1 @kutsulaskuri) "Pitäisi olla vain yksi kutsu vaikka tehtiin 4 samanaikaista"))))

    (testing "Cache estää toistuvat kutsut"
      (reset! kutsulaskuri 0)

      ;; Tee uusi kutsu (cachen pitäisi toimia)
      (todennus/koka->kayttajatiedot
        db integraatioloki miam-asetukset
        testi-headerit nil false)

      (is (= 0 @kutsulaskuri) "Ei pitäisi tehdä uutta kutsua koska cache toimii"))

    ;; Siivoa
    (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")))

(deftest kayttajatietohaku-cache-toimii-test
  (testing "Käyttäjätiedot cachetaan ja toinen haku tulee cachesta"
    (let [db (:db jarjestelma)
          integraatioloki (:integraatioloki jarjestelma)
          miam-asetukset {}
          testi-kayttajanimi "cache-testi"
          testi-headerit {"oam_remote_user" testi-kayttajanimi
                          "oam_user_first_name" "Cache"
                          "oam_user_last_name" "Testi"
                          "oam_user_mail" "cache@example.com"
                          "oam_user_mobile" "0401234567"
                          "oam_organization" "Destia Oy"
                          "oam_groups" "Jarjestelmavastaava"}
          kutsulaskuri (atom 0)]

      (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl 60000))
      (reset! todennus/odottavat-kutsut-atom {})
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'")

      (with-redefs [todennus/varmista-kayttajatiedot
                    (fn [db integraatioloki miam oam-tiedot]
                      (swap! kutsulaskuri inc)
                      {:kayttajanimi testi-kayttajanimi})]

        ;; Ensimmäinen haku
        (todennus/koka->kayttajatiedot db integraatioloki miam-asetukset
          testi-headerit nil false nil)
        (is (= 1 @kutsulaskuri) "Ensimmäinen haku kutsuu tietokantaa")

        ;; Toinen haku - pitäisi tulla cachesta
        (todennus/koka->kayttajatiedot db integraatioloki miam-asetukset
          testi-headerit nil false nil)
        (is (= 1 @kutsulaskuri) "Toinen haku tulee cachesta, ei uutta tietokantakutsua"))

      (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl (* 120 60 1000)))
      (reset! todennus/odottavat-kutsut-atom {})
      (u "DELETE FROM kayttaja WHERE kayttajanimi = '" testi-kayttajanimi "'"))))
