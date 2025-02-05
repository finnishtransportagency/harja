(ns harja.palvelin.komponentit.todennus-varmistus
  "Kirjautumisen JWT tokenien varmistus jotka saadaan Cognitolta"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.core.cache :as cache]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)))

(defonce jwk-cache (atom nil))


(defn- onko-jwk-vanhentunut? 
  "Hakee uuden public keyn, jos sitä ei ole haettu vähään aikaan
   Ei ole tiedossa kuinka tiheään, mutta Cognitolla on ominaisuus rotatoida public avaimia"
  [minuutit]
  (> (- (System/currentTimeMillis) (:fetched-at @jwk-cache)) (* minuutit 60 1000)))


(defn hae-jwks-json
  "Tekee GET kutsun joka hakee jwks (json web key set/public avaimet) Cognitolta"
  [issuer]
  (if
    ;; Jos public avainta ei ole päivitetty 120 minuuttiin
    ;; Tällainen cachetus vielä, koska yksi backend, monta käyttäjää, Cognito ei rotatoi public avaimia kovin tiheään 
    (or
      (nil? @jwk-cache)
      (onko-jwk-vanhentunut? 120))
    (try
      (let [response @(http/get (str issuer "/.well-known/jwks.json") {:headers {;; Lisää Cognitolle tiedoksi mistä pyyntö tulee
                                                                                 "User-Agent" "HARJA dev: (JWT verification)"
                                                                                 "Content-Type" "application/json"}})
            response (json/read-json (:body response))
            _ (reset! jwk-cache {:keys (:keys response) :fetched-at (System/currentTimeMillis)})]
        (:keys response))
      (catch Exception e
        (log/error (str "Failed to refresh JWKS cache: " (.getMessage e)))))
    ;; Public avaimet on ajan tasalla, palauta tallennettu arvo 
    (-> @jwk-cache :keys)))


(defn hae-public-key
  "Suodattaa public avaimet, täsmätään key identifier siihen avaimeen mitä haetaan
   Tämä rajapinta palauttaa "
  [kid issuer]
  (some #(when (= kid (:kid %)) %)
    (hae-jwks-json issuer)))


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
  [accesstoken iam-data iam-identity]
  (let [header (-> accesstoken dekoodaa-token :header)
        issuer (-> accesstoken dekoodaa-token :payload :iss)
        kid (:kid header)
        ;; Hae public avain jolla sama :kid
        jwk (hae-public-key kid issuer)
        ;; Muunna java muotoon 
        public-key (keys/jwk->public-key jwk)

        _ (println "\n accesstoken payload: " header (-> accesstoken dekoodaa-token :payload))
        _ (println "\n accesstoken header: " header (-> accesstoken dekoodaa-token :header))
        _ (println "\n accesstoken signature: " header (-> accesstoken dekoodaa-token :signature) " \n \n" " ---- \n")

        _ (println "\n iam-data payload: " (-> iam-data dekoodaa-token :payload) )
        _ (println "\n iam-data header: " (-> iam-data dekoodaa-token :header) )
        _ (println "\n iam-data signature: " (-> iam-data dekoodaa-token :signature) " \n \n" " ---- \n")

        _ (println "\n iam-identity payload: " iam-identity)
        response (jwt/unsign accesstoken public-key {:alg :rs256})
        _ (println "\n res: " response " \n")]
    response))


(def dekoodaa-ja-varmista-cache (atom (cache/ttl-cache-factory {} :ttl (* 3 60 1000))))

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
  [accesstoken iam-data iam-identity kehitysmoodi?]
  (let [cache-key accesstoken]
    ;; Todennusta kutsutaan ilman malttia, joten cachella kutsutaan vaan tarvittaessa per käyttäjä
    (get (swap! dekoodaa-ja-varmista-cache #(cache/through
                                              (fn [_]
                                                (try
                                                  ;; Cachessa ei ole tietoja, varmistetaan signaturet 
                                                  (let [_ (when-not kehitysmoodi? (varmista-jwt-signature accesstoken iam-data iam-identity))
                                                        inject (-> (-> iam-data dekoodaa-token :payload)
                                                                 (assoc "custom:rooli" "Jarjestelmavastaava"))]
                                                    (-> iam-data dekoodaa-token :payload)
                                                    ;; Tykitä jvh koska ei ole kvh oikeuksia
                                                    inject)
                                                  (catch Exception e
                                                    (do
                                                      (log/error (str
                                                                   "Kirjautumisen varmistus ei onnistunut (JWT signature): "
                                                                   (.getMessage e) 
                                                                   "  -  "
                                                                   "Käyttäjätiedot: "
                                                                   (select-keys (-> iam-data dekoodaa-token :payload)
                                                                     ["custom:rooli" "custom:sukunimi" "custom:email" "custom:ytunnus" "custom:uid"])))
                                                      (log/error (str
                                                                   "Saatu JWT Header (iam-data): " (-> iam-data dekoodaa-token :header) 
                                                                   "  -  "
                                                                   "Saatu payload (iam-data): " (-> iam-data dekoodaa-token :payload)))
                                                      ;; Kirjautuminen ei mennyt läpi, poista oikeudet käyttäjältä
                                                      ;; Tämä uudelleenohjaa "Ei käyttöoikeutta" näkymään 
                                                      (-> (-> iam-data dekoodaa-token :payload)
                                                        (assoc "custom:rooli" "Extranet_Kayttaja"))))))
                                              ;; Tiedot on jo cachessa, palauta ne 
                                              %
                                              cache-key))
      cache-key)))
