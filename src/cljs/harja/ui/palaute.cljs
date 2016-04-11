(ns harja.ui.palaute
  (:require [clojure.string :as string]))

(def sahkoposti "teemu.kaukoranta@solita.fi")

;; Huomaa että rivinvaihto tulee mukaan tekstiin
(def palaute-otsikko
  "Palautetta HARJAsta")
(def palaute-body
  "Kerro meille mitä yritit tehdä, ja millaiseen ongelmaan törmäsit. Harkitse kuvakaappauksen mukaan liittämistä, ne ovat meille erittäin hyödyllisiä.")

(defn- mailto []
  (str "mailto:" sahkoposti))

(defn- subject
  ([pohja lisays] (subject pohja lisays "&"))
  ([pohja lisays valimerkki] (str pohja valimerkki "subject=" (string/replace lisays " " "%20"))))

(defn- body
  ([pohja lisays] (body pohja lisays "&"))
  ([pohja lisays valimerkki] (str pohja valimerkki "body=" (string/replace lisays " " "%20"))))


(defn palaute-linkki []
  [:a {:href (-> (mailto)
                 (subject palaute-otsikko "?")
                 (body palaute-body))}
   "Palautetta!"])