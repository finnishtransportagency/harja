(ns harja.palvelin.integraatiot.api.raportit
  "Raporttien API"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely
             :refer [kasittele-kutsu tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.materiaalit :as q-materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire]))


(defn hae-urakan-materiaaliraportti [db {:keys [id alkupvm loppupvm] :as parametrit} kayttaja]
  (parametrivalidointi/tarkista-parametrit parametrit {:id "Urakka-id puuttuu"
                                                       :alkupvm "Alkupvm puuttuu"
                                                       :loppupvm "Loppupvm puuttuu"})
  (let [urakka-id (Integer/parseInt id)
        alkupvm (pvm/iso-8601->pvm alkupvm)
        loppupvm (pvm/iso-8601->pvm loppupvm)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    ;; Aikaväli saa olla korkeintaan vuoden mittainen
    (validointi/tarkista-aikavali alkupvm loppupvm [1 :vuosi])

    ;; TODO: Dummy-vastaus, hae oikea data.
    {:raportti {:nimi "Materiaaliraportti"
                 :aikavali {:alkupvm "2022-01-01"
                             :loppupvm "2022-01-31"
                            }
                  :alueurakkanumero 146,
                  :materiaaliraportti [{:materiaali "Talvisuolaliuos CaCl2",
                                         :maara {:yksikko "t",
                                                  :maara 1}}]}}))



(def hakutyypit
  [{:palvelu        :hae-urakan-materiaaliraportti
    :polku          "/api/urakat/:id/raportit/materiaali/:alkupvm/:loppupvm"
    :vastaus-skeema json-skeemat/raportti-materiaaliraportti-response
    :kasittely-fn   (fn [parametrit _ kayttaja db]
                      (hae-urakan-materiaaliraportti db parametrit kayttaja))}])


(defrecord Raportit []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku vastaus-skeema kasittely-fn]} hakutyypit]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request nil vastaus-skeema kasittely-fn))))
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu hakutyypit))
    this))