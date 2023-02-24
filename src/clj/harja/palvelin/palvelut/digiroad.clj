(ns harja.palvelin.palvelut.digiroad
  "Digiroad haut"
  (:require [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))


(defn hae-kaistat [digiroad kayttaja tiedot]
  (let [urakka-id (:urakka-id tiedot)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset kayttaja urakka-id)

    (let [{:keys [tr-osoite ajorata]} tiedot
          vastaus (digiroad/hae-kaistat digiroad tr-osoite ajorata)]

      (if (false? (:onnistunut? vastaus))
        (cond
          (= digiroad/+virhe-kaistojen-haussa+ (:koodi vastaus))
          (throw (IllegalArgumentException. "Kaistojen haku epäonnistui"))

          (= virheet/+ulkoinen-kasittelyvirhe-koodi+ (:koodi vastaus))
          (throw (Exception. "Kaistojen haku epäonnistui ulkoisen virheen takia"))

          :else
          (throw (Exception. "Kaistojen haku epäonnistui sisäisen virheen takia")))

        vastaus))))

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
