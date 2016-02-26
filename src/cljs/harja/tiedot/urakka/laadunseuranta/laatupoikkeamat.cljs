(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.roolit :as roolit]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn kuvaile-tekija [tekija]
  (case tekija
    :tilaaja "Tilaaja"
    :urakoitsija "Urakoitsija"
    :konsultti "Konsultti"
    "Tekijä ei tiedossa"))

(defn kuvaile-kasittelytapa [kasittelytapa]
  (case kasittelytapa
    :tyomaakokous "Työmaakokous"
    :puhelin "Puhelimitse"
    :kommentit "Harja-kommenttien perusteella"
    :muu "Muu tapa"
    nil))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :sanktio "Sanktio"
    :ei_sanktiota "Ei sanktiota"
    :hylatty "Hylätty"))

(defn kuvaile-paatos [{:keys [kasittelyaika paatos kasittelytapa]}]
  (when paatos
    (str
      (pvm/pvm kasittelyaika)
      " "
      (kuvaile-paatostyyppi paatos)
      " ("
      (kuvaile-kasittelytapa kasittelytapa) ")")))

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
  {:tekija (roolit/osapuoli @istunto/kayttaja (:id @nav/valittu-urakka))})

(defonce valittu-laatupoikkeama
         (reaction<! [id @valittu-laatupoikkeama-id]
                     {:nil-kun-haku-kaynnissa? false}
                     (when id
                       (go (let [laatupoikkeama (if (= :uusi id)
                                                  (uusi-laatupoikkeama)
                                                  (<! (hae-laatupoikkeaman-tiedot (:id @nav/valittu-urakka) id)))]
                             (-> laatupoikkeama

                                 ;; Tarvitsemme urakan liitteen linkitystä varten

                                 (assoc :urakka (:id @nav/valittu-urakka))
                                 (assoc :sanktiot (into {}
                                                        (map (juxt :id identity) (:sanktiot laatupoikkeama))))))))))