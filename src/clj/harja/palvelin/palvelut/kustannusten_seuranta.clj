(ns harja.palvelin.palvelut.kustannusten-seuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.transit :as transit]
            [clojure.core.async :as async]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri.tietolajit :as tietolajit]
            [harja.tyokalut.functor :as functor]
            [harja.kyselyt.kustannusten-seuranta :as kustannusten-seuranta-q]

            [harja.domain.roolit :as roolit]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [specql.core :as specql]))


(defn- hae-urakan-kustannusten-seuranta-paaryhmittain [db user {:keys [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm] :as tiedot}]
  ;; TODO tarkista käyttöoikeudet
  (if (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
    (let [_ (println "hae-urakan-kustannusten-seuranta-toimenpideittain :: tiedot " (pr-str tiedot))
          res (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain db {:urakka urakka-id
                                                                             :alkupvm alkupvm
                                                                             :loppupvm loppupvm
                                                                             :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})]
      res)
    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defrecord KustannustenSeuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           :as this}]
    (assert (some? db-replica))

    (julkaise-palvelut
      http
      :urakan-kustannusten-seuranta-paaryhmittain
      (fn [user tiedot]
        (hae-urakan-kustannusten-seuranta-paaryhmittain db-replica user tiedot))
      )


    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-kustannusten-seuranta-paaryhmittain)
    this))
