(ns harja.palvelin.palvelut.paallystys
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt.paallystys :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]))

(def muunna-desimaaliluvut-xf
  (map #(-> %
            (assoc-in [:bitumi_indeksi]
                      (or (some-> % :bitumi_indeksi double) 0))
            (assoc-in [:sopimuksen_mukaiset_tyot]
                      (or (some-> % :sopimuksen_mukaiset_tyot double) 0))
            (assoc-in [:arvonvahennykset]
                      (or (some-> % :arvonvahennykset double) 0))
            (assoc-in [:lisatyot]
                      (or (some-> % :lisatyot double) 0))
            (assoc-in [:kaasuindeksi]
                      (or (some-> % :kaasuindeksi double) 0)))))

(defn hae-urakan-paallystyskohteet [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystyskohteet. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      muunna-desimaaliluvut-xf
                      (q/hae-urakan-paallystyskohteet db urakka-id sopimus-id))]
    (log/debug "Päällystyskohteet saatu: " (pr-str vastaus))
    vastaus))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paallystyskohteet
                        (fn [user tiedot]
                          (hae-urakan-paallystyskohteet db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paallystyskohteet)
    this))
