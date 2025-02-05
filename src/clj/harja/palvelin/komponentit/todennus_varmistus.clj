(ns harja.palvelin.komponentit.todennus-varmistus
  "Kirjautumisen JWT tokenien varmistus jotka saadaan Cognitolta"
  (:require [clojure.string :as str]
            [cheshire.core :as cheshire]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.core.cache :as cache]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)))

(defonce jwk-cache (atom nil))
(def +jwk-cache-paivitys-min+ 120)
(def +kayttaja-varmistus-cache-min+ 15)


(defn- tunnistetiedot
  "Palauttaa dekoodatut tunnistetiedot käyttäjästä, sisältää roolit yms (x-iam-data)"
  [jwt]
  (some-> jwt
    (str/split #"\.")
    second
    Base64/decodeBase64
    String.
    cheshire/decode))


(defn- onko-jwk-vanhentunut?
  "Hakee uuden public keyn, jos sitä ei ole haettu vähään aikaan
   Ei ole tiedossa kuinka tiheään, mutta Cognitolla on ominaisuus rotatoida public avaimia"
  [minuutit]
  (> (- (System/currentTimeMillis) (:fetched-at @jwk-cache)) (* minuutit 60 1000)))


(defn hae-jwks-json
  "Tekee GET kutsun joka hakee jwks (json web key set/public avaimet) Cognitolta"
  [issuer paivita?]
  (if
    ;; Jos public avainta ei ole päivitetty 120 minuuttiin
    ;; Tällainen cachetus vielä, koska yksi backend, monta käyttäjää, Cognito ei rotatoi public avaimia kovin tiheään 
    (or
      paivita? ;; Käyttäjä ei päässyt sisälle, yritetään yhden kerran uudelleen päivittämällä public avain
      (nil? @jwk-cache)
      (onko-jwk-vanhentunut? +jwk-cache-paivitys-min+))
    (try
      (let [response @(http/get (str issuer "/.well-known/jwks.json") {:headers {;; Lisää Cognitolle tiedoksi mistä pyyntö tulee
                                                                                 "User-Agent" "HARJA dev: (JWT signature verification)"
                                                                                 "Content-Type" "application/json"}})
            response (json/read-json (:body response))
            _ (reset! jwk-cache {:keys (:keys response) :fetched-at (System/currentTimeMillis)})]
        (:keys response))
      (catch Exception e
        (log/error (str "Failed to refresh JWKS cache: " (.getMessage e)))))
    ;; Public avaimet on ajan tasalla, palauta tallennettu arvo 
    (-> @jwk-cache :keys)))


(defn hae-public-key
  "Kutsuttu rajapinta palauttaa accesstokenin public avaimen
   Mukana on 2 avainta, joten suodatetaan vaan haluttu avain (key identifier)"
  [kid issuer paivita?]
  (some #(when (= kid (:kid %)) %)
    (hae-jwks-json issuer paivita?)))


(defn- decode-base64-json [s]
  (-> s Base64/decodeBase64 String. (json/read-json)))


(defn dekoodaa-token
  "Dekoodaa enkoodatut headerit, ja palauttaa tiedot json stringinä"
  [token]
  (let [[header payload signature] (str/split token #"\.")]
    {:header  (decode-base64-json header)
     :payload (decode-base64-json payload)
     :signature signature}))


(defn varmista-jwt-signature
  "Varmistaa kirjautumisen oikellisuuden Cogniton x-iam-accesstoken header tokenista
   https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html#amazon-cognito-user-pools-using-tokens-manually-inspect"
  [accesstoken iam-data iam-identity paivita?]
  (let [header (-> accesstoken dekoodaa-token :header)
        issuer (-> accesstoken dekoodaa-token :payload :iss)
        kid (:kid header)
        ;; Hae public avain jolla sama :kid
        jwk (hae-public-key kid issuer paivita?)
        ;; Muunna java muotoon 
        public-key (keys/jwk->public-key jwk)

        _ (println "\n accesstoken payload: " header (-> accesstoken dekoodaa-token :payload))
        _ (println "\n accesstoken header: " header (-> accesstoken dekoodaa-token :header))
        _ (println "\n accesstoken signature: " header (-> accesstoken dekoodaa-token :signature) " \n \n" " ---- \n")
        _ (println "\n iam-data payload: " (-> iam-data dekoodaa-token :payload))
        _ (println "\n iam-data header: " (-> iam-data dekoodaa-token :header))
        _ (println "\n iam-data signature: " (-> iam-data dekoodaa-token :signature) " \n \n" " ---- \n")
        _ (println "\n iam-identity payload: " iam-identity " \n ")

        ;; Verifioi kutsumalla unsign, joka tarkastaa saapuvien tietojen allekirjoituksen
        ;; Palauttaa Cognito map responsen, jos kirjautuminen menee läpi ja token on voimassa 
        ;; Heittää virheen, jos tiedoissa on jotain väärin 
        response (jwt/unsign accesstoken public-key {:alg :rs256})
        _ (println "\n res: " response " \n")]
    response))


(defn- kasittele-virhe
  "Käsitellään kirjautumisvirhe, ohjaa käyttäjän 'Ei käyttöoikeutta Harjaan.'- näkymään
   
   'Message seems manipulated': 
     - Public avain voi olla väärin vaikka yritettiin hakea uusi (Ota Harjan kirjautumisen välittäjälle yhteyttä)
     - On mahdollista, että payloadia sorkittu, tutki mitä logeissa lukee 
   
   'Token is expired'
     - Token mennyt vanhaksi, kestää yleensä noin 10 min, tätä ei pitäisi näkyä (välittäjälle yhteyttä)
     - Voi olla järjestelmävirhe, mutta tämä testataan huolella
   
   'Audience does not match'
     - Audience, eli järjestelmän tunnus mille token annettu, ei jostain syystä täsmää (välittäjälle yhteyttä)
   
   'The subject does not match'
     - Subjekti, kenelle token myönnetty, ei jostain syystä täsmää (välittäjälle yhteyttä)
   
   'Issuer does mot match' 
     - Tokenin välittäjä ei jostain syystä täsmää (taas, välittäjälle yhteyttä)"
  [e iam-data]
  ;; Fatal laukaisee slack-hälytyksen, nämä halutaan aina tutkia 
  (log/fatal (str
               "Kirjautumisen varmistus ei onnistunut (JWT signature): " (.getMessage e)))
  (log/error (str
               "Saatu JWT Header (iam-data): " (-> iam-data dekoodaa-token :header)
               "  -  "
               ;; Tässä on käyttäjän yritetyt roolit yms, jos logeilta löytyy roolissa jotain outoa, tee välittömästi toimenpiteitä 
               "Saatu payload (iam-data): " (-> iam-data dekoodaa-token :payload)))
  ;; Kirjautuminen ei mennyt läpi, poista kaikki oikeudet käyttäjältä
  ;; Tämä uudelleenohjaa "Ei käyttöoikeutta" näkymään 
  (-> (tunnistetiedot iam-data)
    ;; Extranet_Kayttaja ei anna oikeutta Harjaan
    ;; Jokin rooli täytyy antaa, muuten tulee todennusvirhe 
    (assoc "custom:rooli" "Extranet_Kayttaja")))


(def kayttaja-varmistettu-cache (atom (cache/ttl-cache-factory {} :ttl (* +kayttaja-varmistus-cache-min+ 60 1000))))

(defn vahvista-jwt-signaturet
  "Tekee JWT tokenien signaturen vahvistuksen
   Palauttaa dekoodatut tunnistetiedot, mikäli signaturet OK
   JWT (Json Web Token) sisältää pisteellä erotetut osiot (enkoodattuna): HEADER.PAYLOAD.SIGNATURE
   
   Molemmilla tokeneilla (accesstoken, iam-data) on nämä otsikkotiedot:
     Header: Metadata 
     Payload: Claims (väitteet, mitkä pitää todentaa oikeiksi)
     Signature: Kryptografinen allekirjoitus Coognitolta, perustuu alkuperäiseen hash-arvoon 

   1. Accesstokenin vahvistukseen haetaan ensin oikea public avain:
        cognito-idp.../.well-known/jwks.json   -  Tämä linkki on headereissa sisällä :iss (issuer) avaimessa
        Täsmätään :kid (key identifier) payloadin :kid arvoon, jolloin meillä on oikea public avain
        Public avainta käytetään signaturen decryptaamiseen ja vahvistukseen 
   
   2. Lasketaan odotettu signature hash (jwt/unsign), ja verrataan alkuperäiseen jwt:n signatureen
      Täällä myös katsotaan tokenin expiration yms, virhe heitetään jos mitään on väärin 
   
   3. Jos kaikki OK, palautetaan dekoodattu tunnusdata, ja jatketaan kirjautumista 
   4. Tuloksena käyttäjän roolitiedot on vahvistettu oikeiksi, eikä mitään ole sorkittu matkalla"
  ([accesstoken iam-data iam-identity kehitysmoodi?]
   (vahvista-jwt-signaturet accesstoken iam-data iam-identity false kehitysmoodi?))
  ([accesstoken iam-data iam-identity yrita-uudelleen? kehitysmoodi?]
   (let [cache-key accesstoken]
     ;; Todennusta kutsutaan ilman malttia, joten cachella kutsutaan vaan tarvittaessa per käyttäjä
     (get (swap! kayttaja-varmistettu-cache #(cache/through
                                               (fn [_]
                                                 (try
                                                   ;; Cachessa ei ole tietoja, varmistetaan signaturet 
                                                   (let [
                                                         _ (println "\n yrita uudelleen: " yrita-uudelleen? "\n ")
                                                         _ (when-not kehitysmoodi?
                                                             (varmista-jwt-signature accesstoken iam-data iam-identity yrita-uudelleen?))
                                                         inject (-> (tunnistetiedot iam-data)
                                                                  (assoc "custom:rooli" "Jarjestelmavastaava"))]
                                                     (tunnistetiedot iam-data)
                                                     (println "\n bb: " (tunnistetiedot iam-data))
                                                     ;; Tykitä jvh koska ei ole kvh oikeuksia
                                                     inject)
                                                   (catch Exception e
                                                     (if
                                                       (and
                                                         (not yrita-uudelleen?)
                                                         (= (.getMessage e) "Message seems corrupt or manipulated"))
                                                       ;; Tarkista, onko public key rotatoitunut, yritä uudelleen
                                                       (vahvista-jwt-signaturet accesstoken iam-data iam-identity true kehitysmoodi?)
                                                       ;; Public key on ajan tasalla, ja vieläkin tulee virhe, joten käsitellään se
                                                       (kasittele-virhe e iam-data)))))
                                               ;; Tiedot on jo cachessa, palauta ne 
                                               %
                                               cache-key))
       cache-key))))
