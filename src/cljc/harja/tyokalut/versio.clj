(ns harja.tyokalut.versio
  "Työkaluja sovelluksen versiotietojen hallintaan"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]))

;; Luetaan harja_image_id.txt tiedostosta containerin imagen versio.
;; Tiedosto luodaan vasta, kun Harjan container käynnistetään kohdeympäristössä, hakemalla tieto ECS:ltä rajapinnan kautta.
;; Tiedosto sisältä imagen versio-id:n, joka on käytössä ajossa olevalla containerilla.
(def palvelimen-versio (some->
                         (try
                           (slurp "harja_image_id.txt")
                           (catch Exception e
                             (log/error "harja_image_id.txt tiedoston lataamisessa tapahtui virhe:", (.getMessage e))
                             nil))
                         (str/trim-newline)))

;; Tiedot, jotka muodostetaan sovelluksen buildauksen aikana
;; Näitä hyödynnetään mm. sovelluksen versiotietojen näyttämisessä käyttöliittymässä
;; Build-tietoja voi hyödyntää myös backendissä.
(defn build-tiedot []
  {:build-aika-iso8601 (pvm/aika-iso8601-aikavyohykkeen-kanssa (pvm/nyt))
   :sovelluksen-versio-sha (str/trim (:out (sh/sh "git" "rev-parse" "HEAD")))})

(def nykyiset-build-tiedot nil)

(defn alusta-build-tiedot []
  (alter-var-root #'nykyiset-build-tiedot
    #(or % (build-tiedot))))


(defmacro sovelluksen-versio-sha []
  (alusta-build-tiedot)
  (get-in nykyiset-build-tiedot [:sovelluksen-versio-sha]))

(defmacro sovelluksen-build-aika-iso8601 []
  (alusta-build-tiedot)
  (get-in nykyiset-build-tiedot [:build-aika-iso8601]))
