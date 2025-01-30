(ns harja.palvelin.komponentit.todennus-varmistus
  "Kirjautumisen JWT tokenin varmistus joka tulee Cognitolta"
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)))


(defn- tunnistetiedot [jwt-body]
  (some->
    ^String jwt-body
    Base64/decodeBase64
    String.
    cheshire/decode))


(defn hae-jwks-json
  "Tekee GET kutsun joka hakee jwks public avaimet cognitolta"
  [issuer kayttajatunnus]
  (let [response @(http/get (str issuer "/.well-known/jwks.json") {:headers {"OAM_REMOTE_USER" kayttajatunnus
                                                                             "Content-Type" "application/json"}})
        response (json/read-json (:body response))]
    (:keys response)))


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
  "Varmistaa kirjautumisen oikellisuuden Cogniton x-iam-accesstoken header tokenista"
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


(def dekoodaa-ja-varmista-cached (memoize (fn [jwt-body accesstoken]
                                            ;; Todennusta kutsutaan ilman malttia, joten tarvimme jonkin muistifunktion
                                            ;; koska tämä tekee GET kutsun cogniton public avaimeen, jota ei haluta spammia
                                            ;; Sama idea, kun esim defn koka->kayttajatiedot, jossa on myös cachetus 
                                            (try
                                              (let [_ (varmista-jwt-signature accesstoken)]
                                                (tunnistetiedot jwt-body))
                                              (catch Exception e
                                                (do
                                                  (println "\nERROR, TODO: Kirjautuminen ei edennyt.")
                                                  (println "Virhe: " (.getMessage e))
                                                  ;; Kirjautuminen ei mennyt läpi, poista oikeudet käyttäjältä
                                                  ;; Tämä uudelleenohjaa "Ei käyttöoikeutta" näkymään
                                                  (apply dissoc (tunnistetiedot jwt-body) ["oam_groups"])))))))
