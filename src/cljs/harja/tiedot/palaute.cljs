(ns harja.tiedot.palaute
  (:require [clojure.string :as string]
            [harja.pvm :as pvm]))

(def sahkoposti "harjapalaute@solita.fi")

;; Huomaa että rivinvaihto tulee mukaan tekstiin
(def palaute-otsikko
  "")

(def virhe-otsikko
  "")

(defn tekniset-tiedot [kayttaja url user-agent]
  (let [enc #(.encodeURIComponent js/window %)]
    (str "\n---\n"
         "Sijainti Harjassa: " (enc url) "\n"
         "Aika: " (pvm/pvm-aika-opt (pvm/nyt)) "\n"
         "User agent: " (enc user-agent) "\n"
         "Käyttäjä: " (enc (pr-str kayttaja)))))

(defn virhe-body [virheviesti kayttaja url user-agent]
  (str
   "\n---\n"
   "Kirjoita ylle, mitä olit tekemässä, kun virhe tuli vastaan. Kuvakaappaukset ovat meille myös "
   "hyvä apu. Ethän pyyhi alla olevia virheen teknisiä tietoja pois."
   "\n---\nTekniset tiedot:\n"
   virheviesti
   (tekniset-tiedot kayttaja url user-agent)))

(defn- mailto []
  (str "mailto:" sahkoposti))

(defn- ilman-valimerkkeja [str]
  (-> str
      (string/replace " " "%20")
      (string/replace "\n" "%0A")))

(defn- lisaa-kentta
  ([kentta pohja lisays] (lisaa-kentta kentta pohja lisays "&"))
  ([kentta pohja lisays valimerkki] (if (empty? lisays)
                                      pohja
                                      (str pohja valimerkki kentta (ilman-valimerkkeja lisays)))))

(defn- subject
  ([pohja lisays] (lisaa-kentta "subject=" pohja lisays))
  ([pohja lisays valimerkki] (lisaa-kentta "subject=" pohja lisays valimerkki)))

(defn- body
  ([pohja lisays] (lisaa-kentta "body=" pohja lisays))
  ([pohja lisays valimerkki] (lisaa-kentta "body=" pohja lisays valimerkki)))
