(ns harja.domain.tierekisteri.varusteet
  "Tierekisterin Varusteet ja laitteet -teeman tietojen käsittelyä"
  (:require [clojure.string :as str]
            #?@(:cljs [[harja.loki :refer [log]]])))

(def varuste-toimenpide->string {nil         "Kaikki"
                                 :lisatty    "Lisätty"
                                 :paivitetty "Päivitetty"
                                 :poistettu  "Poistettu"
                                 :tarkastus  "Tarkastus"})

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

(defn kiinnostaa-listauksessa?
  [ominaisuus]
  (let [tunniste (:kenttatunniste (:ominaisuus ominaisuus))]
    (and (not (#{"x" "y" "z"} tunniste))
         (not (re-matches #".*tunn" tunniste)))))

(defmulti varusteominaisuus->skeema
  "Muodostaa lomake/grid tyyppisen kentän skeeman varusteen ominaisuuden kuvauksen perusteella.
  Dispatch tapahtuu ominaisuuden tietotyypin perusteella."
  (comp :tietotyyppi :ominaisuus))

(defn- varusteominaisuus-skeema-perus [ominaisuus]
  {:otsikko (:selite ominaisuus)
   :pakollinen? (:pakollinen ominaisuus)
   :nimi (keyword (:kenttatunniste ominaisuus))
   :hae #(get-in % [:varuste :tietue :tietolaji :arvot (:kenttatunniste ominaisuus)])
   :aseta (fn [rivi arvo]
            (assoc rivi (:kenttatunniste ominaisuus) arvo))})

(defmethod varusteominaisuus->skeema :koodisto
  [{ominaisuus :ominaisuus}]
  (let [koodisto (:koodisto ominaisuus)]
    (merge (varusteominaisuus-skeema-perus ominaisuus)
           {:tyyppi :valinta
            :valinnat koodisto
            :valinta-nayta :selite
            :fmt (fn [arvo]
                   (let [koodi (first (filter #(= arvo (str (:koodi %))) koodisto))]
                     (if koodi
                       (:selite koodi)
                       arvo)))})))

(defmethod varusteominaisuus->skeema :numeerinen
  [{ominaisuus :ominaisuus}]
  (merge (varusteominaisuus-skeema-perus ominaisuus)
         {:tyyppi :numero
          :kokonaisluku? true}))

(defmethod varusteominaisuus->skeema :default
  [{ominaisuus :ominaisuus}]
  (merge (varusteominaisuus-skeema-perus ominaisuus)
         {:tyyppi :string}))
