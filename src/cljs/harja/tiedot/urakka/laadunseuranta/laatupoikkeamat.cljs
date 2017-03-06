(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.roolit :as roolit]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as u])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def kuvaile-tekija laatupoikkeamat/kuvaile-tekija)
(def kuvaile-kasittelytapa laatupoikkeamat/kuvaile-kasittelytapa)
(def kuvaile-paatostyyppi laatupoikkeamat/kuvaile-paatostyyppi)
(def kuvaile-paatos laatupoikkeamat/kuvaile-paatos)

(defonce voi-kirjata?
  (reaction
   (let [kayttaja @istunto/kayttaja
         urakka @nav/valittu-urakka]
     (and kayttaja
          urakka
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-laatupoikkeamat
                                    (:id urakka))))))

(defn hae-urakan-laatupoikkeamat
  "Hakee annetun urakan laatupoikkeamat urakka id:n ja aikavälin perusteella."
  [listaus urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-laatupoikkeamat {:listaus   listaus
                                  :urakka-id urakka-id
                                  :alku      alkupvm
                                  :loppu     loppupvm}))

(defn hae-laatupoikkeaman-tiedot
  "Hakee urakan laatupoikkeaman tiedot urakan id:n ja laatupoikkeaman id:n
  perusteella. Palauttaa kaiken tiedon mitä laatupoikkeaman muokkausnäkymään
  tarvitaan."
  [urakka-id laatupoikkeama-id]
  (k/post! :hae-laatupoikkeaman-tiedot {:urakka-id urakka-id
                                  :laatupoikkeama-id laatupoikkeama-id}))

(defn tallenna-laatupoikkeama [laatupoikkeama]
  (k/post! :tallenna-laatupoikkeama laatupoikkeama))

(defonce listaus (atom :kaikki))

(defonce urakan-laatupoikkeamat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      [alku loppu] @tiedot-urakka/valittu-aikavali
                      laadunseurannassa? @laadunseuranta/laadunseurannassa?
                      valilehti (nav/valittu-valilehti :laadunseuranta)
                      listaus @listaus]
                     {:nil-kun-haku-kaynnissa? true}
                     (log "urakka-id: " urakka-id "; alku: " alku "; loppu: " loppu "; laadunseurannassa? " laadunseurannassa? "; valilehti: " (pr-str valilehti) "; listaus: " (pr-str listaus))
                     (when (and laadunseurannassa? (= :laatupoikkeamat valilehti)
                                urakka-id alku loppu)
                       (hae-urakan-laatupoikkeamat listaus urakka-id alku loppu))))


(defonce valittu-laatupoikkeama-id (atom nil))

(defn uusi-laatupoikkeama []
  {:tekija (roolit/osapuoli @istunto/kayttaja)
   :aika (pvm/nyt)})

(defonce valittu-laatupoikkeama
         (reaction<! [id @valittu-laatupoikkeama-id]
                     {:nil-kun-haku-kaynnissa? true}
                     (when id
                       (go (let [laatupoikkeama (if (= :uusi id)
                                                  (uusi-laatupoikkeama)
                                                  (<! (hae-laatupoikkeaman-tiedot (:id @nav/valittu-urakka) id)))]
                             (-> laatupoikkeama

                                 ;; Tarvitsemme urakan liitteen linkitystä varten

                                 (assoc :urakka (:id @nav/valittu-urakka))
                                 (assoc :sanktiot (into {}
                                                        (map (juxt :id identity) (:sanktiot laatupoikkeama))))))))))

(defn paivita-yllapitokohteen-tr-tiedot
  [tiedot yllapitokohteet]
  (when yllapitokohteet
    (let [ypk-id (if (integer? (:yllapitokohde tiedot))
                   (:yllapitokohde tiedot)
                   (get-in tiedot [:yllapitokohde :id]))
          k (first (filter #(= (:id %) ypk-id)
                           yllapitokohteet))
          [tie aosa aet losa let] [(:tr-numero k)
                                   (:tr-alkuosa k)
                                   (:tr-alkuetaisyys k)
                                   (:tr-loppuosa k)
                                   (:tr-loppuetaisyys k)]]
      (if (and k tie aosa aet)
        (assoc tiedot
          :tr {:numero        tie
               :alkuosa       aosa
               :alkuetaisyys  aet
               :loppuosa      losa
               :loppuetaisyys let})
        tiedot))))
