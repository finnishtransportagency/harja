(ns harja.palvelin.komponentit.todennus-jwt-authentikointi-test
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [clojure.data.json :as json]
            [harja.palvelin.komponentit.todennus-varmistus :as jwt-varmistus]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta])
  
  (:import (org.apache.commons.codec.binary Base64)
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


(defn generate-ec-key-pair []
  (let [key-gen (KeyPairGenerator/getInstance "EC")]
    (.initialize key-gen 256)
    (.generateKeyPair key-gen)))


(defn generate-rsa-key-pair []
  (let [key-gen (KeyPairGenerator/getInstance "RSA")]
    (.initialize key-gen 2048)
    (.generateKeyPair key-gen)))


(defn encode-payload [x-iam-accesstoken x-iam-data x-iam-identity]
  (let [encode #(-> %
                  cheshire/encode
                  (.getBytes "UTF-8")
                  Base64/encodeBase64URLSafeString)]
    {"x-iam-accesstoken" (str
                           (encode (first x-iam-accesstoken)) "."
                           (encode (second x-iam-accesstoken)) "."
                           (nth x-iam-accesstoken 2))
     "x-iam-data" (str
                    (encode (first x-iam-data)) "."
                    (encode (second x-iam-data)) "."
                    (nth x-iam-data 2))
     "x-iam-identity" x-iam-identity}))


(def test-accesstoken
  [{}
   {}
   "L1F"])

(def test-iam-data
  [{}
   {}
   "L1F"])

(def test-sub "fdsfa-sbnbq-nbsww")

(deftest mock-testi-1234
  (let [handler (->
                  (fn [req] req)
                  (http-palvelin/wrap-with-common-wrappers true {:public-key-url [{:alg "RS256", 
                                                                                   :e "AQAB", 
                                                                                   :kid "Z+t=", 
                                                                                   :kty "RSA", 
                                                                                   :n "iRWBKQ", 
                                                                                   :use "sig"}
                                                                                  
                                                                                  (jwt-varmistus/parsi-PEM-public-key 
                                                                                    (str 
                                                                                      "-----BEGIN PUBLIC KEY-----\n"
                                                                                     "MF6rv\n"
                                                                                     "Tw==\n"
                                                                                     "-----END PUBLIC KEY-----\n"))]}))
        
        todenna nil ;#(todennus/todenna-pyynto (:todennus jarjestelma) % true)

        
        ]

    (testing "testi title"
      (let [
            ;req (handler {:headers (encode-payload test-accesstoken test-iam-data test-sub)})
            ;req (todenna req)
            
            req nil 
            key-pair (generate-ec-key-pair)
            private-key (.getPrivate key-pair)
            public-key (.getPublic key-pair)

            _ (println "\n EC priv: " private-key)
            _ (println "\n EC pub: " public-key)

            rsa-key-pair (generate-rsa-key-pair)
            rsa-private-key (.getPrivate rsa-key-pair)
            rsa-public-key (.getPublic rsa-key-pair)

            _ (println "\n RSA priv: " rsa-private-key)
            _ (println "\n RSA pub: " rsa-public-key)

            ]
        
        (is (= (get-in req [:kayttaja :sahkoposti]) "dddd"))
        (is (= (get-in req [:kayttaja :kayttajanimi]) "dddd"))
        
        ))
    
    
    ))
