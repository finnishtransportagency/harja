(ns harja.palvelin.integraatiot.api.raportit
  "Raporttien API"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.raportointi.raportit.materiaali :as raportit-materiaali]))

(defn muodosta-materiaaliraportti [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (urakat-kyselyt/hae-urakka db urakka-id))
          alueurakkanumero (konv/konvertoi->int (:alueurakkanumero urakka))
          materiaalien-toteumat (when (and urakka-id alkupvm loppupvm)
                                  (raportit-materiaali/muodosta-materiaaliraportti-urakalle db user
                                    {:urakka-id urakka-id
                                     :alkupvm (json/pvm-string->java-sql-date alkupvm)
                                     :loppupvm (json/pvm-string->java-sql-date loppupvm)}))
          materiaaliraportti (map #(hash-map
                                     :materiaali (:materiaali-nimi %)
                                     :maara {:yksikko (:materiaali-yksikko %)
                                             :maara (:kokonaismaara %)})
                               materiaalien-toteumat)]
      {:raportti {:nimi "Materiaaliraportti"
                  :aikavali {:alkupvm alkupvm
                             :loppupvm loppupvm}
                  :alueurakkanumero alueurakkanumero,
                  :materiaaliraportti materiaaliraportti}})))

(defn hae-urakan-materiaaliraportti [db {:keys [id alkupvm loppupvm] :as parametrit} kayttaja]
  (parametrivalidointi/tarkista-parametrit parametrit {:id "Urakka-id puuttuu"
                                                       :alkupvm "Alkupvm puuttuu"
                                                       :loppupvm "Loppupvm puuttuu"})
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    ;; Aikaväli saa olla korkeintaan vuoden mittainen
    (validointi/tarkista-aikavali (json/pvm-string->joda-date alkupvm) (json/pvm-string->joda-date loppupvm) [1 :vuosi])

    (muodosta-materiaaliraportti db kayttaja {:urakka-id urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})))

(def hakutyypit
  [{:palvelu :hae-urakan-materiaaliraportti
    :polku "/api/urakat/:id/raportit/materiaali/:alkupvm/:loppupvm"
    :oikeus "luku"
    :vastaus-skeema json-skeemat/raportti-materiaaliraportti-response
    :kasittely-fn (fn [parametrit _ kayttaja db]
                    (hae-urakan-materiaaliraportti db parametrit kayttaja))}])


(defrecord Raportit []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku vastaus-skeema kasittely-fn oikeus]} hakutyypit]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request nil vastaus-skeema kasittely-fn oikeus))))
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu hakutyypit))
    this))