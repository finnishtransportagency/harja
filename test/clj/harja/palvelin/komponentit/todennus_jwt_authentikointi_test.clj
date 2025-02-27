(ns harja.palvelin.komponentit.todennus-jwt-authentikointi-test
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [clojure.string :as str]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [clojure.data.json :as json]
            [harja.palvelin.komponentit.todennus-varmistus :as jwt-varmistus]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta])

  (:import (org.apache.commons.codec.binary Base64)
           (org.bouncycastle.util.io.pem PemWriter)
           (org.bouncycastle.openssl.jcajce JcaPEMWriter)
           (java.io StringWriter)
           java.security.KeyPairGenerator))

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :todennus (component/using
                      (todennus/http-todennus)
                      [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn- generoi-expiration
  [minuutit]
  (-> (time/now) (time/plus (time/minutes minuutit)) (coerce/to-long) (/ 1000) long))

(def mock-expiration (generoi-expiration 1080))
(def mock-issued-at (-> (time/now) (coerce/to-long) (/ 1000) long))

(def mock-key-identifier "tcSe4a9TGsOM7Gsym0E6UL3")
(def mock-subject "d5-1cff-4ebb-a857-07e11")
(def mock-https-issuer "mock-issuer-https-link")
(def mock-jwt-id "afe99d-b453-4c3e-975d-b66cb")
(def mock-client-id "tc20d3i4ghv94ks0semt4")
(def mock-signer "arn:aws:elasticloadbalancing:region-id:010101:loadbalancer/app/Vayla2-TEST-ALB/123456")
(def mock-username "vayla12ctestoam_testi.testi@sahkop.fi")

(def mock-email "testi.testi@at.fi")
(def mock-puh "040123")
(def mock-ytunnus "133-7")
(def mock-etunimi "Jani")
(def mock-uid "LX123")
(def mock-organisaatio "Meitsin organisaatio")
(def mock-sukunimi "Pasatronic")
(def mock-roolit "Extranet_Kayttaja,all_ex_users,ext_kayttajat,Jarjestelmavastaava")

(def test-accesstoken-jwt
  ;; JWT accesstoken mock dataa, jossa header, payload, ja signature 
  ;; Signature generoidaan jälkeen, en tiedä vielä miten 
  [{:kid mock-key-identifier, :alg "RS256"} ;; header 

   {:sub mock-subject,
    :iss mock-https-issuer,
    :exp mock-expiration,
    :username mock-username,
    :scope "openid",
    :cognito:groups "[region-id_Vayla12cTestOAM]",
    :token_use "access", :auth_time mock-expiration,
    :jti mock-jwt-id,
    :client_id mock-client-id,
    :version "2",
    :iat mock-issued-at} ;; payload 
   "signature=="])

(def test-iam-data-jwt
  ;; JWT iam-data (käyttäjätiedot & roolit) mock dataa, jossa header, payload, ja signature 
  [{:typ "JWT",
    :kid mock-key-identifier, ;; TODO, voi varmaan poistaa, tämä on RSA accesstokenin identifier, ei tarvi iam datassa 
    :alg "ES256",
    :iss mock-https-issuer,
    :client mock-client-id,
    :signer mock-signer,
    :exp mock-expiration} ;; header 

   {:email mock-email,
    :custom:puhelin mock-puh,
    :custom:ytunnus mock-ytunnus,
    :sub mock-subject,
    :iss mock-https-issuer,
    :exp mock-expiration,
    :username mock-username,
    :custom:rooli mock-roolit,
    :custom:etunimi mock-etunimi,
    :email_verified "false",
    :custom:uid mock-uid,
    :custom:organisaatio mock-organisaatio,
    :custom:sukunimi mock-sukunimi} ;; payload 
   "signature=="])


(defn- _log_ [str]
  (println "\n")
  (log/info str))


(defn- public-key-to-pem
  "Muunna ec iam-data public avain PEM muotoon
   Simuloi miten se saadaan Cognitolta tuotannossa"
  [public-key]
  (let [sw (StringWriter.)
        pw (JcaPEMWriter. sw)]
    (.writeObject pw public-key)
    (.flush pw)
    (.close pw)
    (.toString sw)))


(defn- rsa-public-key-to-jwks
  "Muunna RSA avain vielä jwks muotoon (json web key set)
   Simuloi miten se saadaan Cognitolta tuotannossa"
  [rsa-public-key key-id]
  (let [modulus (.getModulus rsa-public-key)
        exponent (.getPublicExponent rsa-public-key)]
    {:alg "RS256"
     :e (-> exponent
          (.toByteArray)
          (Base64/encodeBase64URLSafeString))
     :kid key-id
     :kty "RSA"
     :n (-> modulus
          (.toByteArray)
          (Base64/encodeBase64URLSafeString))
     :use "sig"}))


(defn- generate-ec-key-pair
  "Generoi private sekä public avaimet EC 256 bit
   Tarvitaan mock JWT payloadin (iam-data) signaturen tekoon, jos onnistuu"
  []
  (let [key-gen (KeyPairGenerator/getInstance "EC")]
    (.initialize key-gen 256)
    (.generateKeyPair key-gen)))


(defn- generate-rsa-key-pair
  "Generoi private sekä public avaimet RSA 2048 bit
   Tarvitaan mock JWT payloadin (accesstoken) signaturen tekoon, jos onnistuu"
  []
  (let [key-gen (KeyPairGenerator/getInstance "RSA")]
    (.initialize key-gen 2048)
    (.generateKeyPair key-gen)))


(defn- generoi-jwt-signaturella
  "Palauttaa JWT:n validilla signaturella käyttämällä mock tokeneja, sekä private avainta"
  [private-key payload header alg]
  (jwt/sign payload private-key {:alg alg :header header}))


(deftest testaa-mock-cognito-jwt-todennuspyyntoa
  (let [ec-key-pair (generate-ec-key-pair)
        ec-private-key (.getPrivate ec-key-pair)
        ec-public-key (.getPublic ec-key-pair)
        _ (_log_ (str "EC key priv: " ec-private-key))
        _ (_log_ (str "EC key pub: " ec-public-key))

        ;; Tässä on simuloituna iam-data public avain, joka saadaan Cognitolta PEM muodossa
        ;; Tämä kyllä muunnetaan takaisin todennuksessa java muotoon, mutta testi kertoo jos todennus hajoaa 
        ec-public-key-PEM (public-key-to-pem ec-public-key)
        ec-public-key-PEM (jwt-varmistus/parsi-PEM-public-key ec-public-key-PEM)
        _ (_log_ (str "EC key JAVA: " ec-public-key-PEM))

        ;; Accesstokenin RSA avaimet 
        rsa-key-pair (generate-rsa-key-pair)
        rsa-private-key (.getPrivate rsa-key-pair)
        rsa-public-key (.getPublic rsa-key-pair)

        ;; Nämäkin pitää kiikutella oikeaan jwks muotoon, jollai Cognito niitä tarjoaa  
        rsa-jwks (rsa-public-key-to-jwks rsa-public-key mock-key-identifier)
        _ (_log_ (str "RSA rsa-jwks: " rsa-jwks))

        accesstoken-payload (second test-accesstoken-jwt)
        accesstoken-header (first test-accesstoken-jwt)
        iam-data-payload (second test-iam-data-jwt)
        iam-data-header (first test-iam-data-jwt)

        ;; Allekirjoita payloadit, palauttaa kokonaisen JWT:n - header.payload.sig
        x-iam-accesstoken (generoi-jwt-signaturella rsa-private-key accesstoken-payload accesstoken-header :rs256)
        x-iam-data (generoi-jwt-signaturella ec-private-key iam-data-payload iam-data-header :es256)
        _ (_log_ (str "mock-expiration: " mock-expiration " issued at: " mock-issued-at))

        ;; Tässä on viimein oikeanlainen todennuspyyntö
        todennuspyynto {"x-iam-accesstoken" x-iam-accesstoken
                        "x-iam-data" x-iam-data
                        "x-iam-identity" mock-subject}
        handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers
                    true
                    {:public-key-url [rsa-jwks ec-public-key-PEM]}))
        ;; Passataan kehitysmoodi true, jotta todennus tajuaa tämän olevan testi, eikä tee GET kutsuja 
        ;; Todennus parsii kehitysmoodissa public avaimet yllä olevasta vectorissa 
        todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true)]

    (testing "Käyttäjä pääsee Harjaan Cognito JWT authentikoinnin läpi"
      (let [req (todenna (handler {:headers todennuspyynto}))
            odotetut-roolit (try
                              (todennus/yleisroolit
                                (->> (str/split mock-roolit #",")
                                  (keep (partial todennus/ryhman-rooli-ja-linkki oikeudet/roolit))))
                              (catch Exception e
                                (log/error (str "Odotettuja rooleja ei saatu, tarkista mock-roolit: " (.getMessage e)))
                                nil))

            ;; Nämä tiedot pitäisi palautua, jos ei palaudu, jotain on vialla, eikä käyttäjä päässyt Harjaan 
            _ (is (= (get-in req [:kayttaja :sukunimi]) mock-sukunimi))
            _ (is (= (get-in req [:kayttaja :etunimi]) mock-etunimi))
            _ (is (= (get-in req [:kayttaja :kayttajanimi]) mock-uid))
            _ (is (= (get-in req [:kayttaja :sahkoposti]) mock-email))
            _ (is (= (get-in req [:kayttaja :puhelin]) mock-puh))
            _ (is (= (get-in req [:kayttaja :roolit]) odotetut-roolit))

            _ (is (= (get-in req [:headers "sub"]) mock-subject))
            _ (is (= (get-in req [:headers "exp"]) mock-expiration))
            _ (is (= (get-in req [:headers "iss"]) mock-https-issuer))
            _ (is (= (get-in req [:headers "username"]) mock-username))
            _ (is (= (get-in req [:headers "oam_groups"]) mock-roolit))
            _ (is (= (get-in req [:headers "oam_remote_user"]) mock-uid))
            _ (is (= (get-in req [:headers "oam_user_last_name"]) mock-sukunimi))
            _ (is (= (get-in req [:headers "oam_organization"]) mock-organisaatio))
            _ (is (= (get-in req [:headers "oam_user_mail"]) mock-email))
            _ (is (= (get-in req [:headers "oam_user_first_name"]) mock-etunimi))
            _ (is (= (get-in req [:headers "oam_user_companyid"]) mock-ytunnus))

            ;; JWT tokenit
            _ (is (= (get-in req [:headers "x-iam-accesstoken"]) x-iam-accesstoken))
            _ (is (= (get-in req [:headers "x-iam-data"]) x-iam-data))
            _ (is (= (get-in req [:headers "x-iam-identity"]) mock-subject))]))))
