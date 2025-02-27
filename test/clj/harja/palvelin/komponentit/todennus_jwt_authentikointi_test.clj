(ns harja.palvelin.komponentit.todennus-jwt-authentikointi-test
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [cheshire.core :as cheshire]
            [clojure.core.cache :as cache]
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

(use-fixtures :each jarjestelma-fixture)

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
(def mock-roolit "Extranet_Kayttaja,all_ex_users,ext_kayttajat,1242141-OULU2_vastuuhenkilo,Jarjestelmavastaava,4242523-AES4_vastuuhenkilo,MHU-TESTI-LAP-ROV_Laadunvalvoja")

(def odotetut-roolit (todennus/kayttajan-roolit
                       (partial hae-urakan-id-sampo-idlla)
                       (partial hae-urakoitsijan-id-ytunnuksella)
                       oikeudet/roolit
                       mock-roolit))

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
    :alg "ES256",
    ;; Iam datalla ei tarvi tähän key identifieriä, public key haetaan hieman erillä lailla 
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


(defn- _log_ [str debug?]
  (when debug? (println "\n") (log/info str)))


(defn- nollaa-todennuksen-cache 
  "Jokaisessa testissä halutaan nollata todennuksen cache, kun käytetään samoja mock tietoja"
  []
  (reset! jwt-varmistus/iam-data-pk-cache nil)
  (reset! jwt-varmistus/accesstoken-jwk-cache nil)
  (reset! todennus/kayttajatiedot-cache-atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000)))
  (reset! jwt-varmistus/kayttaja-varmistettu-cache (cache/ttl-cache-factory {} :ttl (* 15 60 1000))))


(defn- public-key-to-pem
  "Muunna ec iam-data public avain PEM muotoon
   Tämä simuloi miten se saadaan Cognitolta tuotannossa"
  [public-key]
  (let [sw (StringWriter.)
        pw (JcaPEMWriter. sw)]
    (.writeObject pw public-key)
    (.flush pw)
    (.close pw)
    (.toString sw)))


(defn- rsa-public-key-to-jwks
  "Muunna RSA avain vielä jwks muotoon (json web key set)
   Tämä simuloi miten se saadaan Cognitolta tuotannossa"
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


(defn- tarkista-cognito-todennus-perustiedot [vastaus x-iam-accesstoken x-iam-data]
  ;; Nämä tiedot pitäisi palautua, jos ei palaudu, jotain on todennuksessa vialla
  (is (= (get-in vastaus [:kayttaja :sukunimi]) mock-sukunimi))
  (is (= (get-in vastaus [:kayttaja :etunimi]) mock-etunimi))
  (is (= (get-in vastaus [:kayttaja :kayttajanimi]) mock-uid))
  (is (= (get-in vastaus [:kayttaja :sahkoposti]) mock-email))
  (is (= (get-in vastaus [:kayttaja :puhelin]) mock-puh))

  (is (= (get-in vastaus [:headers "sub"]) mock-subject))
  (is (= (get-in vastaus [:headers "exp"]) mock-expiration))
  (is (= (get-in vastaus [:headers "iss"]) mock-https-issuer))
  (is (= (get-in vastaus [:headers "username"]) mock-username))
  (is (= (get-in vastaus [:headers "oam_remote_user"]) mock-uid))
  (is (= (get-in vastaus [:headers "oam_user_last_name"]) mock-sukunimi))
  (is (= (get-in vastaus [:headers "oam_organization"]) mock-organisaatio))
  (is (= (get-in vastaus [:headers "oam_user_mail"]) mock-email))
  (is (= (get-in vastaus [:headers "oam_user_first_name"]) mock-etunimi))
  (is (= (get-in vastaus [:headers "oam_user_companyid"]) mock-ytunnus))

  ;; JWT tokenit
  (is (= (get-in vastaus [:headers "x-iam-accesstoken"]) x-iam-accesstoken))
  (is (= (get-in vastaus [:headers "x-iam-data"]) x-iam-data))
  (is (= (get-in vastaus [:headers "x-iam-identity"]) mock-subject)))


(defn- palauta-cognito-todennuspyynto-vastaus [kehitysmoodi? public-key todennuspyynto]
  (let [kehitysmoodi? kehitysmoodi?
        handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers
                    kehitysmoodi?
                    {:public-key-url public-key}))
        todenna #(todennus/todenna-pyynto (:todennus jarjestelma) % kehitysmoodi?)]
    (todenna (handler {:headers todennuspyynto}))))


(defn- initialisoi-cognito-jwt-todennuspyynto
  "Generoi private & public avaimet, ja kirjoittaa tokeneille validin signaturen
   Palauttaa validin todennuspyynnön Cognito muodossa"
  ([] (initialisoi-cognito-jwt-todennuspyynto false))
  ([debug?]
   (nollaa-todennuksen-cache)
   (let [ec-key-pair (generate-ec-key-pair)
         ec-private-key (.getPrivate ec-key-pair)
         ec-public-key (.getPublic ec-key-pair)
         _ (_log_ (str "EC key priv: " ec-private-key) debug?)
         _ (_log_ (str "EC key pub: " ec-public-key) debug?)

         ;; Tässä on simuloituna iam-data public avain, joka saadaan Cognitolta PEM muodossa
         ;; Tämä kyllä muunnetaan takaisin todennuksessa java muotoon, mutta testi kertoo jos todennus hajoaa 
         ec-public-key-PEM (public-key-to-pem ec-public-key)
         ec-public-key-PEM (jwt-varmistus/parsi-PEM-public-key ec-public-key-PEM)
         _ (_log_ (str "EC key JAVA: " ec-public-key-PEM) debug?)

         ;; Accesstokenin RSA avaimet 
         rsa-key-pair (generate-rsa-key-pair)
         rsa-private-key (.getPrivate rsa-key-pair)
         rsa-public-key (.getPublic rsa-key-pair)

         ;; Nämäkin pitää kiikutella oikeaan jwks muotoon, jollai Cognito niitä tarjoaa  
         rsa-jwks (rsa-public-key-to-jwks rsa-public-key mock-key-identifier)
         _ (_log_ (str "RSA rsa-jwks: " rsa-jwks) debug?)

         accesstoken-payload (second test-accesstoken-jwt)
         accesstoken-header (first test-accesstoken-jwt)
         iam-data-payload (second test-iam-data-jwt)
         iam-data-header (first test-iam-data-jwt)

         ;; Allekirjoita payloadit, palauttaa kokonaisen JWT:n - header.payload.sig
         x-iam-accesstoken (generoi-jwt-signaturella rsa-private-key accesstoken-payload accesstoken-header :rs256)
         x-iam-data (generoi-jwt-signaturella ec-private-key iam-data-payload iam-data-header :es256)
         _ (_log_ (str "mock-expiration: " mock-expiration " issued at: " mock-issued-at) debug?)]

     ;; Palauta oikeanlainen todennuspyyntö, sisältää 2 JWT tokenia (identityä ei käytetä kirjoitushetkellä)
     {:todennuspyynto {"x-iam-accesstoken" x-iam-accesstoken
                       "x-iam-data" x-iam-data
                       "x-iam-identity" mock-subject}

      :accesstoken-public-key rsa-jwks
      :iam-data-public-key ec-public-key-PEM})))


(deftest cognito-jwt-todennuspyynto-toimii
  (let [initialisoidut-tiedot (initialisoi-cognito-jwt-todennuspyynto)
        todennuspyynto (-> initialisoidut-tiedot :todennuspyynto)
        iam-data-public-key (-> initialisoidut-tiedot :iam-data-public-key)
        accesstoken-public-key (-> initialisoidut-tiedot :accesstoken-public-key)
        ;; JWT tokenit
        x-iam-data (get todennuspyynto "x-iam-data")
        x-iam-accesstoken (get todennuspyynto "x-iam-accesstoken")
        
        kehitysmoodi? true
        public-key [accesstoken-public-key iam-data-public-key]
        ;; Tekee pyynnön Harjan todennukseen 
        vastaus (palauta-cognito-todennuspyynto-vastaus kehitysmoodi? public-key todennuspyynto)

        odotetut-harja-roolit (:roolit odotetut-roolit)
        odotetut-urakka-roolit (:urakkaroolit odotetut-roolit)
        mock-roolit-set (set (str/split mock-roolit #","))
        sisaltaa-roolin? (fn [role]
                           (some #(str/includes? % role) mock-roolit-set))
        vastaus-roolit (get-in vastaus [:kayttaja :roolit])
        vastaus-urakkaroolit (get-in vastaus [:kayttaja :urakkaroolit])]

    ;; Vastaahan roolit odotettuja rooleja 
    (is (= vastaus-roolit odotetut-harja-roolit))
    (is (= vastaus-urakkaroolit odotetut-urakka-roolit))

    ;; Katsotaan että cognito parsinta palauttaa kaikki roolit
    ;; Tarkista että vastauksen harja-rooli löytyy passatuista rooleista 
    (is (every? mock-roolit-set vastaus-roolit) "Vastaus sisältää lähetetyt roolit")

    ;; Tarkista että vastauksen urakkaroolit löytyy passatuista rooleista 
    ;;   {4 #{"vastuuhenkilo"} .. }    <- vastuuhenkilo löytyy mock-rooli sisältä 
    ;;
    ;; urakka id voisi myös tarkistaa, mutta lienee tarpeeksi hyvä tämä laiska check
    (is (every? sisaltaa-roolin? (set (mapcat second vastaus-urakkaroolit))) "Vastaus sisältää lähetetyt urakkaroolit")

    ;; Roolit vastaa annettuja rooleja 
    (is (= (get-in vastaus [:headers "oam_groups"]) mock-roolit))

    ;; Perustiedot täsmää
    (tarkista-cognito-todennus-perustiedot vastaus x-iam-accesstoken x-iam-data)))


;; TODO 
;; Tämä päälle vasta sitten kun väännetään käyttäjien esto päälle 
;; Aluksi mergetään ilman, joten tämä testi ei  tule menemään läpi vielä  
#_(deftest cognito-esta-harjaan-paasy-test
  (let [initialisoidut-tiedot (initialisoi-cognito-jwt-todennuspyynto)
        todennuspyynto (-> initialisoidut-tiedot :todennuspyynto)
        x-iam-data (get todennuspyynto "x-iam-data")
        x-iam-accesstoken (get todennuspyynto "x-iam-accesstoken")

        ;; kehitysmoodi? false Laukaisee GET kutsun, 
        ;; mutta koska mock-https-issuer, public-key-url eivät sisällä validia linkkiä, kutsu ei mene mihinkään
        ;;    Pitäisi johtaa virheeseen, ja estää pääsy
        kehitysmoodi? false
        public-key "string"
        vastaus (palauta-cognito-todennuspyynto-vastaus kehitysmoodi? public-key todennuspyynto)
        odotetut-harja-roolit (:roolit odotetut-roolit)
        odotetut-urakka-roolit (:urakkaroolit odotetut-roolit)]

    ;; failed tarkoittaa että käyttäjä ohjattiin "Ei käyttöoikeutta Harjaan"
    (is (= (get-in vastaus [:headers "oam_groups"]) "failed"))
    (is (= (get-in vastaus [:kayttaja :roolit]) #{"failed"}))

    ;; Roolien ei pitäisi täsmätä
    (is (not= (get-in vastaus [:kayttaja :roolit]) odotetut-harja-roolit))
    (is (not= (get-in vastaus [:kayttaja :urakkaroolit]) odotetut-urakka-roolit))

    ;; Muiden perustietojen  pitäisi silti olla OK 
    (tarkista-cognito-todennus-perustiedot vastaus x-iam-accesstoken x-iam-data)))


(deftest ei-public-avainta-asetettu-paasta-kayttaja-harjaan-test
  ;; Jos public avainta ei ole asetettu, käyttäjän pitäisi päästä Harjaan
  ;; (koska authentikaatiota ei voi tällöin suorittaa)
  (let [initialisoidut-tiedot (initialisoi-cognito-jwt-todennuspyynto)
        todennuspyynto (-> initialisoidut-tiedot :todennuspyynto)
        x-iam-data (get todennuspyynto "x-iam-data")
        x-iam-accesstoken (get todennuspyynto "x-iam-accesstoken")
        kehitysmoodi? false
        public-key nil
        vastaus (palauta-cognito-todennuspyynto-vastaus kehitysmoodi? public-key todennuspyynto)
        odotetut-harja-roolit (:roolit odotetut-roolit)
        odotetut-urakka-roolit (:urakkaroolit odotetut-roolit)]

    ;; Ei pitäisi olla failed tilassa 
    (is (not= (get-in vastaus [:headers "oam_groups"]) "failed"))
    (is (not= (get-in vastaus [:kayttaja :roolit]) #{"failed"}))

    ;; Roolit pitäisi olla OK, ja käyttäjän pitäisi päästä Harjaan 
    (is (= (get-in vastaus [:kayttaja :roolit]) odotetut-harja-roolit))
    (is (= (get-in vastaus [:kayttaja :urakkaroolit]) odotetut-urakka-roolit))
    (tarkista-cognito-todennus-perustiedot vastaus x-iam-accesstoken x-iam-data)))
