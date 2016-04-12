(ns harja.ui.palaute
  (:require [clojure.string :as string]
            [harja.ui.ikonit :as ikonit]))

(def sahkoposti "teemu.kaukoranta@solita.fi")

;; Huomaa että rivinvaihto tulee mukaan tekstiin
(def palaute-otsikko
  "Palautetta HARJAsta")
(def palaute-body
  "Kerro meille mitä yritit tehdä, ja millaiseen ongelmaan törmäsit. Harkitse kuvakaappauksen mukaan liittämistä, ne ovat meille erittäin hyödyllisiä.")

(def virhe-otsikko
  "HARJA räsähti")

(defn virhe-body [virheviesti]
  (str
    "
    Kirjoita ylle, mitä olit tekemässä kun virhe tuli vastaan. Kuvakaappaukset ovat meille hyvä apu. Ethän pyyhi alla olevaa varoitustekstiä pois.

    --"
    virheviesti))

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
   [:span (ikonit/kommentti) " Palautetta!"]])

(defn virhe-palaute [virhe]
  [:button.nappi-ensisijainen
   [:a
    {:href (-> (mailto)
               (subject virhe-otsikko "?")
               (body virhe-body))}
    [:span (ikonit/envelope) " Lähetä meille vikaraportti"]]])