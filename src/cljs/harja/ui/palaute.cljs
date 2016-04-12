(ns harja.ui.palaute
  (:require [clojure.string :as string]
            [harja.ui.ikonit :as ikonit]))

(def sahkoposti "teemu.kaukoranta@solita.fi")

;; Huomaa että rivinvaihto tulee mukaan tekstiin
(def palaute-otsikko
  "Palautetta HARJAsta")
(def palaute-body
  "Kerro meille mitä yritit tehdä, ja millaiseen ongelmaan törmäsit. Harkitse kuvakaappauksen mukaan liittämistä, ne ovat meille erittäin hyödyllisiä.
  Voit pyyhkiä tämän tekstin pois.")

(def virhe-otsikko
  "HARJA räsähti")

(defn virhe-body [virheviesti]
  (str
    "
    ---
    Kirjoita ylle, mitä olit tekemässä kun virhe tuli vastaan. Kuvakaappaukset ovat meille hyvä apu. Ethän pyyhi alla olevaa varoitustekstiä pois.

    ---

    "
    virheviesti))

(defn- mailto []
  (str "mailto:" sahkoposti))

(defn- ilman-valimerkkeja [str]
  (-> str
      (string/replace " " "%20")
      (string/replace "\n" "%0A")))

(defn- lisaa-kentta
  ([kentta pohja lisays] (lisaa-kentta kentta pohja lisays "&"))
  ([kentta pohja lisays valimerkki] (str pohja valimerkki kentta (ilman-valimerkkeja lisays))))

(defn- subject
  ([pohja lisays] (lisaa-kentta "subject=" pohja lisays))
  ([pohja lisays valimerkki] (lisaa-kentta "subject=" pohja lisays valimerkki)))

(defn- body
  ([pohja lisays] (lisaa-kentta "body=" pohja lisays))
  ([pohja lisays valimerkki] (lisaa-kentta "body=" pohja lisays valimerkki)))


(defn palaute-linkki []
  [:a#palautelinkki
   {:href (-> (mailto)
                              (subject palaute-otsikko "?")
                              (body palaute-body))}
   [:span (ikonit/kommentti) " Palautetta!"]])

(defn virhe-palaute [virhe]
  [:button.nappi-ensisijainen
   {:on-click #(.stopPropagation %)}
   [:a
    {:href (-> (mailto)
               (subject virhe-otsikko "?")
               (body (virhe-body virhe)))
     :on-click #(.stopPropagation %)}
    [:span (ikonit/envelope) " Lähetä meille virheraportti"]]])