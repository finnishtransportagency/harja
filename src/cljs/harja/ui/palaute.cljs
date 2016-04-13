(ns harja.ui.palaute
  (:require [clojure.string :as string]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as str]))

(def sahkoposti "harja.palaute@solita.fi")

;; Huomaa että rivinvaihto tulee mukaan tekstiin
(def palaute-otsikko
  "Palautetta HARJAsta")
(def palaute-body
  "Kerro meille mitä yritit tehdä, ja millaiseen ongelmaan törmäsit. Harkitse kuvakaappauksen mukaan liittämistä, ne ovat meille erittäin hyödyllisiä.
Voit pyyhkiä tämän tekstin pois.")

(def virhe-otsikko
  "HARJA räsähti")

(defn virhe-body [virheviesti]
  (let [sijainti-harjassa (-> (str (-> js/window .-window .-location .-href))
                              (str/replace  "&" " "))] ;; FIXME &-merkkiä ei saanut escapetettua, tämä toimii kunnes löytyy parempi ratkaisu
    (str
      "
      ---
      Kirjoita ylle, mitä olit tekemässä, kun virhe tuli vastaan. Kuvakaappaukset ovat meille myös hyvä apu. Ethän pyyhi alla olevia virheen teknisiä tietoja pois.
      ---

      Tekniset tiedot:

      "
      virheviesti
      "

      Sijainti Harjassa: " sijainti-harjassa)))

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
   [:span (ikonit/livicon-kommentti) " Palautetta!"]])

(defn virhe-palaute [virhe]
  [:a#palautelinkki
   {:href (-> (mailto)
              (subject virhe-otsikko "?")
              (body (virhe-body virhe)))
    :on-click #(.stopPropagation %)}
   [:span
    [ikonit/envelope]
    [:span " Hupsista, Harja räsähti! Olemme pahoillamme. Kuulisimme mielellämme miten sait vian esiin. Klikkaa tähän, niin pääset lähettämään virheraportin."]]])