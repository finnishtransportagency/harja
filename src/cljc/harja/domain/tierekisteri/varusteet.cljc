(ns harja.domain.tierekisteri.varusteet
  "Tierekisterin Varusteet ja laitteet -teeman tietojen käsittelyä")

(defmulti varusteominaisuus->skeema
  "Muodostaa lomake/grid tyyppisen kentän skeeman varusteen ominaisuuden kuvauksen perusteella.
  Dispatch tapahtuu ominaisuuden tietotyypin perusteella."
  (comp :tietotyyppi :ominaisuus))

(defn- varusteominaisuus-skeema-perus [ominaisuus]
  {:otsikko (:selite ominaisuus)
   :pakollinen? (:pakollinen ominaisuus)
   :nimi (keyword (:kenttatunniste ominaisuus))
   :hae #(get % (:kenttatunniste ominaisuus))
   :aseta (fn [rivi arvo]
            (assoc rivi (:kenttatunniste ominaisuus) arvo))})

(defmethod varusteominaisuus->skeema :koodisto
  [{ominaisuus :ominaisuus}]
  (let [koodisto (:koodisto ominaisuus)]
    (merge (varusteominaisuus-skeema-perus ominaisuus)
           {:tyyppi :valinta
            :valinnat koodisto
            :valinta-nayta :selite})))

(defmethod varusteominaisuus->skeema :numeerinen
  [{ominaisuus :ominaisuus}]
  (merge (varusteominaisuus-skeema-perus ominaisuus)
         {:tyyppi :numero
          :kokonaisluku? true}))

(defmethod varusteominaisuus->skeema :default
  [{ominaisuus :ominaisuus}]
  (merge (varusteominaisuus-skeema-perus ominaisuus)
         {:tyyppi :string}))
