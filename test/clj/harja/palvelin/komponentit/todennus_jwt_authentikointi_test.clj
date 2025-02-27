(ns harja.palvelin.komponentit.todennus-jwt-authentikointi-test
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [cheshire.core :as cheshire]
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
    :exp "1740471727", 
    :username mock-username, 
    :scope "openid",
    :cognito:groups "[region-id_Vayla12cTestOAM]", 
    :token_use "access", :auth_time "1740460913",
    :jti mock-jwt-id, 
    :client_id mock-client-id, 
    :version "2", 
    :iat "1740468127"} ;; payload 
   "signature=="])

(def test-iam-data-jwt
  ;; JWT iam-data (käyttäjätiedot & roolit) mock dataa, jossa header, payload, ja signature 
  [{:typ "JWT", 
    :kid mock-key-identifier, ;; TODO, voi varmaan poistaa, tämä on RSA accesstokenin identifier, ei tarvi iam datassa 
    :alg "ES256", 
    :iss mock-https-issuer, 
    :client mock-client-id, 
    :signer mock-signer, 
    :exp "1740468776"} ;; header 
   
   {:email mock-email, 
    :custom:puhelin mock-puh, 
    :custom:ytunnus mock-ytunnus, 
    :sub mock-subject, 
    :iss mock-https-issuer, 
    :exp "1740468776", 
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


(defn- encode-64json
  "Enkoodaa JWT oikeaan web64 muotoon"
  [s]
  (-> s
    cheshire/encode
    (.getBytes "UTF-8")
    Base64/encodeBase64
    (String. "UTF-8")))


(defn- create-jwt-signature
  "Generoi signaturen käyttämällä mock datasta tehtyä payloadia, sekä avainta"
  [private-key data alg]
  (jwt/sign data private-key {:alg alg}))


(defn- generoi-enkoodattu-todennuspyynto
  "Simuloi oikean tyyppistä todennuspyyntöä Harjaan, 
   jossa 2 JWT tokenia oikealla signaturella, jotka passataan todennukseen"
  [x-iam-accesstoken accesstoken-signature
   x-iam-data iam-data-signature
   x-iam-identity]
  (let [encode #(encode-64json %)]
    {"x-iam-accesstoken" (str
                           (encode (first x-iam-accesstoken)) "." ;; header 
                           (encode (second x-iam-accesstoken)) "." ;; payload 
                           accesstoken-signature)

     "x-iam-data" (str
                    (encode (first x-iam-data)) "." ;; header 
                    (encode (second x-iam-data)) "." ;; payload 
                    iam-data-signature)

     "x-iam-identity" x-iam-identity}))


(deftest mock-testi-1234
  (let [ec-key-pair (generate-ec-key-pair)
        ec-private-key (.getPrivate ec-key-pair)
        ec-public-key (.getPublic ec-key-pair)

        _ (_log_ (str "EC priv: " ec-private-key))
        _ (_log_ (str "EC pub: " ec-public-key))
        _ (_log_ (str "EC formatted: " (public-key-to-pem ec-public-key)))

        ;; Tässä on simuloituna iam-data public avain, joka saadaan Cognitolta PEM muodossa
        ;; Tämä kyllä muunnetaan takaisin todennuksessa java muotoon, mutta testi kertoo jos todennus hajoaa 
        ec-public-key-PEM (public-key-to-pem ec-public-key)
        ec-public-key-PEM (jwt-varmistus/parsi-PEM-public-key ec-public-key-PEM)

        _ (_log_ (str "EC JAVA: " ec-public-key-PEM))

        ;; Accesstokenin RSA avaimet 
        rsa-key-pair (generate-rsa-key-pair)
        rsa-private-key (.getPrivate rsa-key-pair)
        rsa-public-key (.getPublic rsa-key-pair)

        _ (_log_ (str "RSA priv: " rsa-private-key))
        _ (_log_ (str "RSA pub: " rsa-public-key))

        ;; Nämäkin pitää kiikutella oikeaan jwks muotoon, jollai Cognito niitä tarjoaa  
        rsa-jwks (rsa-public-key-to-jwks rsa-public-key mock-key-identifier)
        _ (_log_ (str "RSA rsa-jwks: " rsa-jwks))
        
        ;; Allekirjoita payloadit, ehkä vaatii vielä rassausta...
        accesstoken-signature (create-jwt-signature rsa-private-key (second test-accesstoken-jwt) :rs256)
        iam-data-signature (create-jwt-signature ec-private-key (second test-iam-data-jwt) :es256)

        todennuspyynto (generoi-enkoodattu-todennuspyynto
                         test-accesstoken-jwt accesstoken-signature
                         test-iam-data-jwt iam-data-signature
                         mock-subject)

        handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers
                    true
                    {:public-key-url [rsa-jwks ec-public-key-PEM]}))

        todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % true)]

    (testing "test"
      (let [req (todenna (handler {:headers todennuspyynto}))

            ;_ (println "\n sig: " accesstoken-signature " \n jwt: " todennuspyynto " \n ")
            ;req nil
            ;_ (println "\n req: " req)
            ]

       ;(is (= (get-in req [:kayttaja :kayttajanimi]) "ddd"))
        ))))
