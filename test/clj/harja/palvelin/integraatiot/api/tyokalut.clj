(ns harja.palvelin.integraatiot.api.tyokalut
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [org.httpkit.client :as http]))

(defn post-kutsu
  "Tekee POST-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  ([api-polku-vec kayttaja portti body] (post-kutsu api-polku-vec kayttaja portti body nil))
  ([api-polku-vec kayttaja portti body options]
   @(http/post (reduce str (concat ["http://localhost:" portti] api-polku-vec))
               (merge {:body    body
                       :headers {"OAM_REMOTE_USER" kayttaja
                                 "Content-Type"    "application/json"}}
                      options))))

(defn async-post-kutsu
  "Tekee POST-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  ([api-polku-vec kayttaja portti body callback] (async-post-kutsu api-polku-vec kayttaja portti body nil callback))
  ([api-polku-vec kayttaja portti body options callback]
   (http/post (reduce str (concat ["http://localhost:" portti] api-polku-vec))
               (merge {:body    body
                       :headers {"OAM_REMOTE_USER" kayttaja
                                 "Content-Type"    "application/json"}}
                      options)
              callback)))

(defn get-kutsu
  "Tekee GET-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen."
  ([api-polku-vec kayttaja portti]
   (get-kutsu api-polku-vec kayttaja nil nil portti))
  ([api-polku-vec kayttaja parametrit portti]
   (get-kutsu api-polku-vec kayttaja parametrit nil portti))
  ([api-polku-vec kayttaja parametrit options portti]
   @(http/get (reduce str (concat ["http://localhost:" portti] api-polku-vec))
              (cond-> {:headers {"OAM_REMOTE_USER" kayttaja
                                 "Content-Type" "application/json"}}
                      parametrit (assoc :query-params parametrit)
                      options (merge options)))))

(defn put-kutsu
  "Tekee PUT-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  ([api-polku-vec kayttaja portti body] (put-kutsu api-polku-vec kayttaja portti body nil))
  ([api-polku-vec kayttaja portti body options]
   @(http/put (reduce str (concat ["http://localhost:" portti] api-polku-vec))
              (merge {:body    body
                      :headers {"OAM_REMOTE_USER" kayttaja
                                "Content-Type"    "application/json"}}
                     options))))

(defn delete-kutsu
  "Tekee DELETE-kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  [api-polku-vec kayttaja portti body]
  @(http/delete (reduce str (concat ["http://localhost:" portti] api-polku-vec))
                {:body    body
                 :headers {"OAM_REMOTE_USER" kayttaja
                           "Content-Type"    "application/json"}}))

(defn hae-vapaa-toteuma-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM toteuma WHERE ulkoinen_id = '" id "';"))]
    (if (empty? vastaus) id (recur))))

(defn hae-usea-vapaa-toteuma-ulkoinen-id [maara]
  (reduce
    (fn [edellinen index]
      (let [id (hae-vapaa-toteuma-ulkoinen-id)]
        (if (some #(= % id) edellinen)
          (recur edellinen index)
          (conj edellinen id))))
    []
    (range maara)))

(defn tasmaa-poikkeus [{:keys [type virheet]} tyyppi koodi viesti]
  (and
    (= tyyppi type)
    (some (fn [virhe] (and (= koodi (:koodi virhe)) (.contains (:viesti virhe) viesti)))
          virheet)))
