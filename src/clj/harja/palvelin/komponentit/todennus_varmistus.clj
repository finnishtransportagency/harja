(ns harja.palvelin.komponentit.todennus-varmistus
  "Kirjautumisen JWT tokenin varmistus joka tulee Cognitolta"
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.core.cache :as cache]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)))

(defonce jwk-cache (atom nil))


(defn- tunnistetiedot [jwt-body]
  (some->
    ^String jwt-body
    Base64/decodeBase64
    String.
    cheshire/decode))


(defn hae-jwks-json
  "Tekee GET kutsun joka hakee jwks public avaimet cognitolta"
  [issuer kayttajatunnus]
  (if
    ;; Jos public avainta ei ole päivitetty 15 minuuttiin, hae se uudelleen
    ;; Tällainen cachetus vielä, koska yksi backend, monta käyttäjää, Cognito ei rotatoi public avaimia kovin tiheään 
    (or
      (nil? @jwk-cache)
      (> (- (System/currentTimeMillis) (:fetched-at @jwk-cache)) (* 15 60 1000))) ;; 15 min 
    (try
      (let [response @(http/get (str issuer "/.well-known/jwks.json") {:headers {;; TODO, käyttäjätunnus ei tässä ole niinkään relevantti, kosk kutsu tehdään joka 15min
                                                                                 ;; Tämä on cognitolle inffona, mistä kutsu on tullut, voisi laittaa järjestelmän nimen, tms.
                                                                                 "OAM_REMOTE_USER" kayttajatunnus
                                                                                 "Content-Type" "application/json"}})
            response (json/read-json (:body response))
            _ (reset! jwk-cache {:keys response :fetched-at (System/currentTimeMillis)})]
        (:keys response))
      (catch Exception e
        (log/error (str "Failed to refresh JWKS cache: " (.getMessage e)))))
    ;; Public avaimet on ajan tasalla, palauta tallennettu arvo 
    (:keys @jwk-cache)))


(defn hae-public-key
  "Hae ja täsmää public avaimen kid arvo käyttäjän Cognito headeriin"
  [kid issuer kayttajatunnus]
  (some #(when (= kid (:kid %)) %)
    (hae-jwks-json issuer kayttajatunnus)))


(defn- decode-base64-json [s]
  (-> s Base64/decodeBase64 String. (json/read-json)))


(defn dekoodaa-token [token]
  (let [[header payload signature] (str/split token #"\.")]
    {:header  (decode-base64-json header)
     :payload (decode-base64-json payload)
     :signature signature}))


(defn varmista-jwt-signature 
  "Varmistaa kirjautumisen oikellisuuden Cogniton x-iam-accesstoken header tokenista
   https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html#amazon-cognito-user-pools-using-tokens-manually-inspect"
  [accesstoken]
  (let [header (-> accesstoken dekoodaa-token :header)
        issuer (-> accesstoken dekoodaa-token :payload :iss)
        kayttajatunnus (-> accesstoken dekoodaa-token :payload :username)
        kid (:kid header)
        ;; Hae public avain jolla sama :kid
        jwk (hae-public-key kid issuer kayttajatunnus)
        ;; Muunna java muotoon 
        public-key (keys/jwk->public-key jwk)
        ;; Verifioi kutsumalla unsign, joka tarkastaa saapuvien tietojen allekirjoituksen
        ;; Palauttaa Cognito map responsen, jos kirjautuminen menee läpi ja token on voimassa 
        ;; Heittää virheen, jos tiedoissa on jotain väärin 
        response (jwt/unsign accesstoken public-key {:alg :rs256})
        _ (println "\n res: " response)]
    response))


(def dekoodaa-ja-varmista-cache (atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000))))

(defn dekoodaa-ja-varmista 
  "Kutsuu headerin varmistuksen, palauttaa dekoodatut tunnistetiedot, mikäli varmistus menee läpi
   JWT (Json Web Token) sisältää pisteellä erotetut osiot: HEADER.PAYLOAD.SIGNATURE
   
   Header: Metadata, algoritmi, kid (key identifier)
   Payload: Claims (käyttäjä data, issuer, expiration..)
   Signature: Kryptografinen todistus, että tokenia ei ole muokattu

   1. Tarkistetaan, että metadatassa on sama key identifier, kun public avaimessa : 
      cognito-idp.eu-west1.bla.bla/.well-known/jwks.json 
      Tämä linkki on payloadin sisällä :iss (issuer) avaimessa. Sieltä etsitään :kid arvo, joka täsmätään käyttäjän headereihin
   
   2. Lasketaan odotettu allekirjoitus, ja verrataan alkuperäiseen jwt:n signatureen, tämä tapahtuu (jwt/unsign)
      Täällä myös katsotaan tokenin expiration, sun muut, ja virhe heitetään jos mitään on väärin 
   
   3. Jos kaikki menevät läpi, palautetaan dekoodattu tunnusdata

   Nyt, tuloksena, jos kirjautumisen aikana muokataan Cognito payloadia esim -> 'rooli:jarjestelmavastaava', 
   -> laskettu allekirjoitus ei täsmää enää alkuperäiseen 
   -> kirjautuminen estetään, ja virhe heitetään"
  [jwt-body accesstoken kehitysmoodi?]
  (let [cache-key accesstoken]
    ;; Todennusta kutsutaan ilman malttia, joten tarvimme jonkin cachetuksen
    ;; Cachetus tyyli sama kun defn koka->kayttajatiedot
    ;; Nyt haluttuja funktioita kutsutaan vain tarvittaessa
    (get (swap! dekoodaa-ja-varmista-cache #(cache/through
                                              (fn [_]
                                                (try
                                                  ;; Jos tietoja ei ole, kutsu varmistus
                                                  (let [_ (when-not kehitysmoodi? (varmista-jwt-signature accesstoken))]
                                                    (tunnistetiedot jwt-body))
                                                  (catch Exception e
                                                    (do
                                                      (log/error (str
                                                                   "Kirjautumisen varmistus ei onnistunut (JWT signature): " (.getMessage e) "  -  "
                                                                   "\nKäyttäjätiedot: " (select-keys (tunnistetiedot jwt-body) ["custom:rooli" "custom:sukunimi" "custom:email" "custom:ytunnus" "custom:uid"])))
                                                      (log/error (str
                                                                   "Saatu JWT Header: " (-> accesstoken dekoodaa-token :header) "  -  "
                                                                   "Saatu payload: " (-> accesstoken dekoodaa-token :payload)))
                                                      ;; Kirjautuminen ei mennyt läpi, poista oikeudet käyttäjältä
                                                      ;; Tämä uudelleenohjaa "Ei käyttöoikeutta" näkymään 
                                                      (apply dissoc (tunnistetiedot jwt-body) ["custom:rooli"])))))
                                              ;; Tiedot on jo cachessa, palauta
                                              %
                                              cache-key))
      cache-key)))
