(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.laatupoikkeamat :as laatupoikkeamat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def kuvaile-tekija laatupoikkeamat/kuvaile-tekija)
(def kuvaile-kasittelytapa laatupoikkeamat/kuvaile-kasittelytapa)
(def kuvaile-paatostyyppi laatupoikkeamat/kuvaile-paatostyyppi)
(def kuvaile-paatos laatupoikkeamat/kuvaile-paatos)

(defonce voi-kirjata? (reaction
                        (let [kayttaja @istunto/kayttaja
                              urakka @nav/valittu-urakka]
                          (and kayttaja
                               urakka
                               (roolit/rooli-urakassa? kayttaja roolit/laadunseuranta-kirjaus (:id urakka))))))

(defn hae-urakan-laatupoikkeamat
  "Hakee annetun urakan laatupoikkeamat urakka id:n ja aikavälin perusteella."
  [listaus urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-laatupoikkeamat {:listaus   listaus
                                  :urakka-id urakka-id
                                  :alku      alkupvm
                                  :loppu     loppupvm}))

(defn hae-laatupoikkeaman-tiedot
  "Hakee urakan laatupoikkeaman tiedot urakan id:n ja laatupoikkeaman id:n perusteella.
  Palauttaa kaiken tiedon mitä laatupoikkeaman muokkausnäkymään tarvitaan."
  [urakka-id laatupoikkeama-id]
  (k/post! :hae-laatupoikkeaman-tiedot {:urakka-id   urakka-id
                                  :laatupoikkeama-id laatupoikkeama-id}))

(defn tallenna-laatupoikkeama [laatupoikkeama]
  (k/post! :tallenna-laatupoikkeama laatupoikkeama))
