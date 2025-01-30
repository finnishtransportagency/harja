(ns harja.palvelin.komponentit.todennus-varmistus
  "Kirjautumisen JWT tokenin varmistus joka tulee Cognitolta"
  (:require [cheshire.core :as cheshire]
            [clojure.core.cache :as cache]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [harja.kyselyt.konversio :as konv]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)
           (org.apache.commons.codec.net BCodec)))

(defn testi-enkoodattu-uu []
  "blank")

(defn testi-enkoodattu []
  "balnk")

;; TODO: Koodimössöä
(def cognito-issuer "blank")
(def jwks-url (str cognito-issuer "/.well-known/jwks.json"))

(def headers {"OAM_REMOTE_USER" "lx-test"
              "OAM_GROUPS" (interpose "," "Ja")
              "Content-Type" "application/json"
              "x-csrf-token" "baz"})

;; 1. Hae JWKS avaimet Cotnitolta
(defn get-jwks []
  (let [response @(http/get jwks-url {:headers headers})
        response (json/read-json (:body response))
        _ (println "\n response: " response)]
    (:keys response)))

;; 2. Hae public avaimesta JWT täsmäävä `kid` arvo 
(defn get-public-key [kid]
  (some #(do 
           (println "\n entry: " (:kid %))
           (when 
           (= kid (:kid %)) %) )
    (get-jwks)))

(defn decode-base64-json [s]
  (-> s Base64/decodeBase64 String. (json/read-json)))

(defn decode-jwt [token]
  (let [[header payload signature] (str/split token #"\.")]
    {:header  (decode-base64-json header)
     :payload (decode-base64-json payload)
     :signature signature}))

;; 3. Varmista JWT Signature
(defn verify-jwt [accesstoken]
  (let [;; Halutaan verrata headerin :kid arvo
        header (-> accesstoken decode-jwt :header)
        kid (:kid header)
        ;; Hae public avain jolla sama :kid
        jwk (get-public-key kid)
        ;; Muunna java muotoon 
        public-key (keys/jwk->public-key jwk)
        ;; Verifioi kutsumalla unsign, joka tarkastaa saapuvien tietojen allekirjoituksen
        ;; Palauttaa Cognito map responsen, jos kirjautuminen menee läpi ja token on voimassa 
        ;; Heittää virheen, jos tiedoissa on jotain väärin 
        response (jwt/unsign accesstoken public-key {:alg :rs256})
        _ (println "\n res: " response)]
    response))


(def cached-decode (memoize (fn [jwt-body accesstoken]
                              ;; memoize funktio kutsuu tätä vain kerran, jotta ei tehdä spammilla get kutsuja  
                              (println "\n Decoding JWT for the first time...")
                              (try
                                (let [_ (verify-jwt accesstoken)]
                                  (some->
                                    ^String jwt-body
                                    Base64/decodeBase64
                                    String.
                                    cheshire/decode))
                                (catch Exception e
                                  (do
                                    (println "\nERROR, TODO: Kirjautuminen ei edennyt.")
                                    (println "Virhe: " (.getMessage e))
                                    {}))))))
