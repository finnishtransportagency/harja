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


(defn- hae-urakan-kustannusten-seuranta-toimenpideittain [db user {:keys [urakka-id tehtavaryhma alkupvm loppupvm] :as tiedot}]
  ;; TODO tarkista käyttöoikeudet
  (if (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
    (let [t (if (= "Kaikki" tehtavaryhma) nil tehtavaryhma)
          res (kustannusten-seuranta-q/listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan db {:urakka urakka-id
                                                                                                   ;:tehtavaryhma t
                                                                                                   ;:alkupvm alkupvm
                                                                                                   ;:loppupvm loppupvm
                                                                                                   })
          _ (println "Rivit kustannusten seurantaan " (pr-str res))]
      res)
    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn hae-urakan-kustannusten-toimenpiteet [db user {:keys [urakka-id]}]
  (if (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
    (kustannusten-seuranta-q/hae-kustannusten-seurannan-toimenpiteet db)
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
      :urakan-kustannusten-seuranta-toimenpideittain
      (fn [user tiedot]
        (hae-urakan-kustannusten-seuranta-toimenpideittain db-replica user tiedot))
      :urakan-kustannusten-toimenpiteet
      (fn [user tiedot]
        (hae-urakan-kustannusten-toimenpiteet db-replica user tiedot))
      )


    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-kustannusten-seuranta-toimenpideittain)
    this))
