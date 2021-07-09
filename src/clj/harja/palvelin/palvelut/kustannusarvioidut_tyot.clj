(ns harja.palvelin.palvelut.kustannusarvioidut-tyot
  (:require [harja.kyselyt.kustannusarvioidut-tyot :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-kustannusarvoidut-tyot-nimineen
  ([db user urakka-id]
   (hae-urakan-kustannusarvoidut-tyot-nimineen db user urakka-id false))
  ([db user urakka-id indeksikorjatut?]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
   (map (fn [m]
          (-> m
              (update :tehtavan-tunniste (fn [tunniste]
                                           (when tunniste (str tunniste))))
              (update :tehtavaryhman-tunniste (fn [tunniste]
                                                (when tunniste (str tunniste))))))
        (q/hae-urakan-kustannusarvioidut-tyot-nimineen db {:urakka urakka-id :indeksikorjatut (if (= :kaikki indeksikorjatut?)
                                                                                                (name indeksikorjatut?)
                                                                                                indeksikorjatut?)}))))