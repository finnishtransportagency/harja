(ns harja.palvelin.palvelut.digiroad
  "Digiroad haut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad]
            [harja.kyselyt.paallystys-kyselyt :as paallystys-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [try+]]
            [clj-time.core :as t]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [clojure.set :as set]))


(defn hae-kaistat [digiroad kayttaja tiedot]
  (let [urakka-id (:urakka-id tiedot)]
    ;; TODO: Tarkista, onko tämä oikeustarkastus riittävä
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset kayttaja urakka-id)

    (let [{:keys [tr-osoite ajorata]} tiedot]
      (digiroad/hae-kaistat digiroad tr-osoite ajorata))))

(def hakutyypit
  [{:palvelu :hae-kaistat-digiroadista
    :kasittely-fn (fn [digiroad _ kayttaja tiedot]
                    (hae-kaistat digiroad kayttaja tiedot))}])


(defrecord Digiroad []
  component/Lifecycle
  (start [{http :http-palvelin db :db digiroad :digiroad-integraatio :as this}]
    (doseq [{:keys [palvelu kasittely-fn]} hakutyypit]
      (julkaise-palvelu http palvelu (partial kasittely-fn digiroad db)))
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu hakutyypit))
    this))
