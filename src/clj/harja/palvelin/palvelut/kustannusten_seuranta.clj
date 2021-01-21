(ns harja.palvelin.palvelut.kustannusten-seuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.kustannusten-seuranta :as kustannusten-seuranta-q]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.palvelut.kulut.kustannusten-seuranta-excel :as kustannusten-seuranta-excel]
            [clojure.string :as str]))


(defn- hae-urakan-kustannusten-seuranta-paaryhmittain [db user {:keys [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm] :as tiedot}]
  (if (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
    (if (nil? hoitokauden-alkuvuosi)
      (throw+ {:type virheet/+sisainen-kasittelyvirhe+
               :virheet [{:koodi 400
                          :viesti "Tuntematon hoitokauden-alkuvuosi."}]})
      (let [_ (log/info "hae-urakan-kustannusten-seuranta-paaryhmittain :: tiedot " (pr-str tiedot))
            res (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain db {:urakka urakka-id
                                                                               :alkupvm alkupvm
                                                                               :loppupvm loppupvm
                                                                               :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})]
        res))

    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))


(defrecord KustannustenSeuranta []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          db-replica (:db-replica this)
          excel (:excel-vienti this)]
      (assert (some? db-replica))

      (julkaise-palvelu
        http
        :urakan-kustannusten-seuranta-paaryhmittain
        (fn [user tiedot]
          (hae-urakan-kustannusten-seuranta-paaryhmittain db-replica user tiedot)))
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :kustannukset (partial #'kustannusten-seuranta-excel/kustannukset-excel db)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-kustannusten-seuranta-paaryhmittain)
    (when (:excel-vienti this)
      (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :kustannukset))
    this))
