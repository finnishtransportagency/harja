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
            [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.binary Base64)
           (org.apache.commons.codec.net BCodec)))


(defn testi-enkoodattu-uu []
  "blank")


(defn testi-enkoodattu []
  "blank")


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
(defn verify-jwt [token accesstoken]
  (let [header (json/read-json (String. (Base64/decodeBase64 (first (clojure.string/split token #"\.")))))
        _ (println "\n header access: " (decode-jwt accesstoken))
        
        header (-> accesstoken decode-jwt :header)
        kid (:kid header)
        jwk (get-public-key kid)
        _ (println "\n kid: " kid)
        _ (println "\n jwk: " jwk)
        public-key (buddy.core.keys/jwk->public-key jwk)
        ;; Palauttaa Cognito map responsen, jos kirjautuminen menee läpi ja token on voimassa 
        response (jwt/unsign accesstoken public-key {:alg :rs256})
        _ (println "\n res: " response)]
    response))


(def cached-decode (memoize (fn [jwt-body x-iam-data accesstoken]
                              ;; memoize funktio kutsuu tätä vain kerran, jotta ei tehdä spammilla get kutsuja  
                              (println "\n Decoding JWT for the first time...")
                              (try
                                ;; Tässä tapahtuu tokenin verifiointi
                                (verify-jwt x-iam-data accesstoken)
                                (catch Exception e
                                  (do
                                    (println "\nERROR, TODO: Kirjautuminen ei edennyt.")
                                    (println "Virhe: " (.getMessage e))
                                    (throw (Exception. "Kirjautuminen Harjaan ei onnistunut.")))))

                              ;; TODO tee tällekin jokin wrapperi
                              (some->
                                ^String jwt-body
                                Base64/decodeBase64
                                String.
                                cheshire/decode))))
