(ns harja.palvelin.palvelut.digiroad
  "Digiroad haut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad]
            [harja.domain.oikeudet :as oikeudet]))


(defn hae-kaistat [digiroad kayttaja tiedot]
  (let [urakka-id (:urakka-id tiedot)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset kayttaja urakka-id)

    (let [{:keys [tr-osoite ajorata]} tiedot]
      (digiroad/hae-kaistat digiroad tr-osoite ajorata))))

(def hakutyypit
  [{:palvelu :hae-kaistat-digiroadista
    :kasittely-fn (fn [digiroad kayttaja tiedot]
                    (hae-kaistat digiroad kayttaja tiedot))}])


(defrecord Digiroad []
  component/Lifecycle
  (start [{http :http-palvelin db :db digiroad :digiroad-integraatio :as this}]
    (doseq [{:keys [palvelu kasittely-fn]} hakutyypit]
      (julkaise-palvelu http palvelu (fn [kayttaja tiedot]
                                       (kasittely-fn digiroad kayttaja tiedot))))
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu hakutyypit))
    this))
