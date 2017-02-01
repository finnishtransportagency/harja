(ns harja.domain.tierekisteri.varusteet
  "Tierekisterin Varusteet ja laitteet -teeman tietojen käsittelyä"
  (:require [clojure.string :as str]
    #?@(:cljs [[harja.loki :refer [log]]])
            [harja.domain.tierekisteri :as tr]))

(def varuste-toimenpide->string {nil "Kaikki"
                                 :lisatty "Lisätty"
                                 :paivitetty "Päivitetty"
                                 :poistettu "Poistettu"
                                 :tarkastus "Tarkastus"})

(def varustetoteumatyypit
  (vec varuste-toimenpide->string))

(def tietolaji->selitys
  {"tl523" "Tekninen piste"
   "tl501" "Kaiteet"
   "tl517" "Portaat"
   "tl507" "Bussipysäkin varusteet"
   "tl508" "Bussipysäkin katos"
   "tl506" "Liikennemerkki"
   "tl522" "Reunakivet"
   "tl513" "Reunapaalut"
   "tl196" "Bussipysäkit"
   "tl519" "Puomit ja kulkuaukot"
   "tl505" "Jätehuolto"
   "tl195" "Tienkäyttäjien palvelualueet"
   "tl504" "WC"
   "tl198" "Kohtaamispaikat ja levikkeet"
   "tl518" "Kivetyt alueet"
   "tl514" "Melurakenteet"
   "tl509" "Rummut"
   "tl515" "Aidat"
   "tl503" "Levähdysalueiden varusteet"
   "tl510" "Viheralueet"
   "tl512" "Viemärit"
   "tl165" "Välikaistat"
   "tl516" "Hiekkalaatikot"
   "tl511" "Viherkuviot"})

(def tien-puolet
  [0
   1
   2
   3
   7
   8
   9])

(def oletus-ajoradat
  [0])

(def kaikki-ajoradat
  [0 1 2])

(defn kiinnostaa-listauksessa?
  [ominaisuus]
  (let [tunniste (:kenttatunniste (:ominaisuus ominaisuus))]
    (and (not (#{"x" "y" "z" "urakka"} tunniste))
         (not (re-matches #".*tunn" tunniste)))))

(def varusteen-osoite-skeema
  {:otsikko "Tieosoite"
   :tyyppi :tierekisteriosoite
   :hae (comp :tie :sijainti :tietue :varuste)
   :fmt tr/tierekisteriosoite-tekstina
   :leveys 1})

(defmulti varusteominaisuus->skeema
          "Muodostaa lomake/grid tyyppisen kentän skeeman varusteen ominaisuuden kuvauksen perusteella.
          Dispatch tapahtuu ominaisuuden tietotyypin perusteella."
          (comp :tietotyyppi :ominaisuus))

(defn- varusteominaisuus-skeema-perus [ominaisuus muokattava?]
  {:otsikko (str/capitalize (:selite ominaisuus))
   :pakollinen? (:pakollinen ominaisuus)
   :nimi (keyword (:kenttatunniste ominaisuus))
   :hae #(let [arvo (or (get-in % [:arvot (keyword (:kenttatunniste ominaisuus))])
                        (get-in % [:varuste :tietue :tietolaji :arvot (:kenttatunniste ominaisuus)]))]
           arvo)
   :aseta (fn [rivi arvo]
            (assoc-in rivi [:arvot (keyword (:kenttatunniste ominaisuus))] arvo))
   ;; Varusteen tunnistetta ei saa muokata koskaan
   :muokattava? #(and (not (= "tunniste" (:kenttatunniste ominaisuus))) muokattava?)
   :pituus-max (:pituus ominaisuus)})

(defmethod varusteominaisuus->skeema :koodisto
  [{ominaisuus :ominaisuus} muokattava?]
  (let [koodisto (map #(assoc % :selite (str/capitalize (:selite %))) (:koodisto ominaisuus))]
    (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
           {:tyyppi :valinta
            :valinnat koodisto
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo
                               (:selite arvo)
                               (if muokattava?
                                 "- Valitse -"
                                 "")))
            :leveys 3
            :fmt (fn [arvo]
                   (let [koodi (first (filter #(= arvo (str (:koodi %))) koodisto))]
                     (if koodi
                       (:selite koodi)
                       arvo)))})))

(defmethod varusteominaisuus->skeema :numeerinen
  [{ominaisuus :ominaisuus} muokattava?]
  (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
         {:tyyppi :string
          :regex (re-pattern (str "-?\\d{1," 10 "}"))
          :leveys 1}))

(defmethod varusteominaisuus->skeema :default
  [{ominaisuus :ominaisuus} muokattava?]
  (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
         {:tyyppi :string
          :leveys (if (= "tunniste" (:kenttatunniste ominaisuus))
                    1
                    3)}))


