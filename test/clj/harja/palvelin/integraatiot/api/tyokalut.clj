(ns harja.palvelin.integraatiot.api.tyokalut
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [org.httpkit.client :as http]))

(defn post-kutsu
  "Tekee POST-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  [api-polku-vec kayttaja portti body]
  @(http/post (reduce str (concat ["http://localhost:" portti] api-polku-vec))
              {:body    body
               :headers {"OAM_REMOTE_USER" kayttaja
                         "Content-Type"    "application/json"}}))

(defn get-kutsu
  "Tekee GET-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen."
  [api-polku-vec kayttaja portti]
  @(http/get (reduce str (concat ["http://localhost:" portti] api-polku-vec))
             {:headers {"OAM_REMOTE_USER" kayttaja
                        "Content-Type"    "application/json"}}))