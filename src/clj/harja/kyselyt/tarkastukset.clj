(ns harja.kyselyt.tarkastukset
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.hoitoluokat :as hoitoluokat-q]
            [harja.geo :as geo]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.domain.laadunseuranta :as laadunseuranta]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [specql.core :refer [upsert! delete!]]
            [harja.kyselyt.specql-db :refer [define-tables]])
  (:import (org.postgis PGgeometry)))

(defqueries "harja/kyselyt/tarkastukset.sql"
  {:positional? true})

(define-tables
  ;; Määritellään tässä nimiavaruudessa, linkkitaulu on lähinnä sisäiseen käyttöön
  ;; päättelemään onko ylläpitokohteelle linkitystä
  ["tarkastus_yllapitokohde" ::tarkastus-yllapitokohde
   {"tarkastus" ::tarkastus-id
    "yllapitokohde" ::yllapitokohde-id}])

(defn- paivita-tarkastuksen-yllapitokohde! [db tarkastus-id yllapitokohde-id]
  (if (nil? yllapitokohde-id)
    (delete! db ::tarkastus-yllapitokohde {::tarkastus-id tarkastus-id})
    (upsert! db ::tarkastus-yllapitokohde
             {::tarkastus-id tarkastus-id
              ::yllapitokohde-id yllapitokohde-id})))

(defn- luo-tarkastus*
  [db user urakka-id {:keys [id lahde aika tr tyyppi tarkastaja sijainti
                             ulkoinen-id havainnot laadunalitus yllapitokohde
                             nayta-urakoitsijalle] :as tarkastus} urakoitsija?]
  (do
    (log/debug "Luodaan uusi tarkastus")
    (luo-tarkastus<! db
                     lahde
                     urakka-id (konv/sql-timestamp aika)
                     (:numero tr) (:alkuosa tr) (:alkuetaisyys tr)
                     (:loppuosa tr) (:loppuetaisyys tr)
                     sijainti tarkastaja (name tyyppi) (:id user) ulkoinen-id
                     havainnot laadunalitus yllapitokohde
                     (if urakoitsija? true (boolean nayta-urakoitsijalle)))
    (luodun-tarkastuksen-id db)))

(defn- paivita-tarkastus*
  [db user urakka-id {:keys [id lahde aika tr tyyppi tarkastaja sijainti
                             ulkoinen-id havainnot laadunalitus yllapitokohde
                             nayta-urakoitsijalle] :as tarkastus} urakoitsija?]
  (do (log/debug (format "Päivitetään tarkastus id: %s " id))
      (paivita-tarkastus! db
                          (konv/sql-timestamp aika)
                          (:numero tr) (:alkuosa tr) (:alkuetaisyys tr) (:loppuosa tr) (:loppuetaisyys tr)
                          sijainti tarkastaja (name tyyppi) (:id user)
                          havainnot laadunalitus yllapitokohde
                          (if urakoitsija? true (boolean nayta-urakoitsijalle))
                          urakka-id id)
      id))

(defn luo-tai-paivita-tarkastus
  "Luo uuden tai päivittää tarkastuksen ja palauttaa id:n."
  [db user urakka-id {:keys [id yllapitokohde sijainti nayta-urakoitsijalle] :as tarkastus}]
  (log/debug "Tallenna tai päivitä urakan " urakka-id " tarkastus: " tarkastus)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka-id yllapitokohde)
  (let [sijainti (if (instance? PGgeometry sijainti)
                   sijainti
                   (and sijainti (geo/geometry (geo/clj->pg sijainti))))
        urakoitsija? (= (roolit/osapuoli user) :urakoitsija)
        id (if (nil? id)
             (luo-tarkastus* db user urakka-id tarkastus urakoitsija?)
             (paivita-tarkastus* db user urakka-id tarkastus urakoitsija?))]
    (paivita-tarkastuksen-yllapitokohde! id yllapitokohde)
    id))

(defn luo-tai-paivita-talvihoitomittaus [db tarkastus uusi?
                                         {:keys [hoitoluokka lumimaara tasaisuus tr
                                                 kitka lampotila-ilma lampotila-tie ajosuunta] :as mittaukset}]
  (let [talvihoitoluokka (or hoitoluokka
                             (when tr
                               (let [hoitoluokka-kannasta
                                     (into #{}
                                           (map :hoitoluokka
                                                (hoitoluokat-q/hae-hoitoluokka-tr-pisteelle db {:tie               (:numero tr)
                                                                                                :aosa              (:alkuosa tr)
                                                                                                :aet               (:alkuetaisyys tr)
                                                                                                :losa              (:loppuosa tr)
                                                                                                :let               (:loppuetaisyys tr)
                                                                                                :tietolajitunniste "talvihoito"})))]
                                 (if (= 1 (count hoitoluokka-kannasta))
                                   (first hoitoluokka-kannasta)
                                   nil))))
        params {:tarkastus tarkastus
                :talvihoitoluokka talvihoitoluokka :lumimaara lumimaara :tasaisuus tasaisuus :kitka kitka
                :lampotila_ilma lampotila-ilma :lampotila_tie lampotila-tie :ajosuunta (or ajosuunta 0)}
        poista-rivi? (not-any? #(get-in mittaukset %) laadunseuranta/talvihoitomittauksen-lomakekentat)]

    (if poista-rivi?
      (poista-talvihoitomittaus! db tarkastus)
      (if uusi?
        (luo-talvihoitomittaus<! db params)
        (paivita-talvihoitomittaus! db params)))))

(defn luo-tai-paivita-soratiemittaus [db tarkastus uusi?
                                      {:keys [hoitoluokka tasaisuus kiinteys polyavyys sivukaltevuus] :as mittaukset}]
  (let [params {:hoitoluokka   hoitoluokka
                :tasaisuus     tasaisuus :kiinteys kiinteys :polyavyys polyavyys
                :sivukaltevuus sivukaltevuus :tarkastus tarkastus}
        poista-rivi? (not-any? #(get-in (dissoc mittaukset :tarkastus) %)
                               laadunseuranta/soratiemittauksen-kentat)]
    (if poista-rivi?
      (poista-soratiemittaus! db tarkastus)
      (if uusi?
        (luo-soratiemittaus<! db params)
        (paivita-soratiemittaus! db params)))))
