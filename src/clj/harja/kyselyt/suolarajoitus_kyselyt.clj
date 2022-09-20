(ns harja.kyselyt.suolarajoitus-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/suolarajoitus_kyselyt.sql"
  {:positional? true})

(defn hae-suolatoteumat-rajoitusalueittain [db {:keys [hoitokauden-alkuvuosi alkupvm loppupvm urakka-id] :as tiedot}]
  (let [;; Hae formiaatti ja talvisuolan materiaalityyppien id:t, jotta niiden summatiedot on helpompi laskea toteumista
        suolatoteumat (hae-rajoitusalueet-summatiedoin db
                        {:urakka-id urakka-id
                         :alkupvm alkupvm
                         :loppupvm loppupvm
                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        ;; Konvertoi pohjavesialueet json arvot clojuren tunnistamiin muotoihin
        suolatoteumat (mapv (fn [rivi]
                              (-> rivi
                                (update :pohjavesialueet
                                  (fn [alueet]
                                    (mapv
                                      #(konv/pgobject->map % :tunnus :string :nimi :string)
                                      (konv/pgarray->vector alueet))))))
                        suolatoteumat)

        ;; Laske formiaateille ja suolatoteumille menekki per ajoratakilometri
        suolatoteumat (mapv (fn [rivi]
                              (cond-> rivi
                                true (konv/decimal->double rivi :suolatoteumat :formiaattitoteumat :ajoratojen_pituus)
                                (and
                                  (not (nil? (:formiaattitoteumat rivi)))
                                  (not (nil? (:ajoratojen_pituus rivi)))
                                  (> (:formiaattitoteumat rivi) 0)
                                  (> (:ajoratojen_pituus rivi) 0))
                                (assoc :formiaatit_t_per_ajoratakm
                                       (with-precision 3 (/ (:formiaattitoteumat rivi) (/ (:ajoratojen_pituus rivi) 1000))))
                                (and
                                  (not (nil? (:suolatoteumat rivi)))
                                  (not (nil? (:ajoratojen_pituus rivi)))
                                  (> (:suolatoteumat rivi) 0)
                                  (> (:ajoratojen_pituus rivi) 0))
                                (assoc :talvisuola_t_per_ajoratakm
                                       (with-precision 4 (/ (:suolatoteumat rivi) (/ (:ajoratojen_pituus rivi) 1000))))))
                        suolatoteumat)]
    suolatoteumat))
