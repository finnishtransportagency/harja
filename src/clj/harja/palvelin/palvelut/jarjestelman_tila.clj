(ns harja.palvelin.palvelut.jarjestelman-tila
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.sonja :as sd]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.kyselyt.konversio :as konv]
            [slingshot.slingshot :refer [try+]]
            [clj-time.coerce :as tc]
            [harja.pvm :as pvm]))

(defn hae-sonjan-tila [db]
  (let [sonjan-tila (q/hae-sonjan-tila db)]
    (map #(update % :tila konv/jsonb->clojuremap) sonjan-tila)))

(defn olion-tila-aktiivinen? [tila]
  (= tila "ACTIVE"))

(defn jono-ok? [jonon-tiedot]
  (let [{:keys [tuottaja vastaanottaja]} (first (vals jonon-tiedot))
        tuottajan-tila-ok? (when tuottaja
                             (olion-tila-aktiivinen? (:tuottajan-tila tuottaja)))
        vastaanottajan-tila-ok? (when vastaanottaja
                                  (olion-tila-aktiivinen? (:vastaanottajan-tila vastaanottaja)))]
    (every? #(not (false? %))
            [tuottajan-tila-ok? vastaanottajan-tila-ok?])))

(defn istunto-ok? [{:keys [jonot istunnon-tila]}]
  (and (olion-tila-aktiivinen? istunnon-tila)
       (not (empty? jonot))
       (every? jono-ok?
               jonot)))

(defn yhteys-ok?
  [{:keys [istunnot yhteyden-tila]} paivitetty]
  (and (pvm/ennen? (tc/to-local-date-time (pvm/sekunttia-sitten 20)) (tc/to-local-date-time paivitetty))
       (olion-tila-aktiivinen? yhteyden-tila)
       (not (empty? istunnot))
       (every? istunto-ok?
               istunnot)))

(defn kaikki-yhteydet-ok? [tilat]
  (and (not (empty? tilat))
       (every? (fn [{:keys [tila paivitetty]}]
                 (yhteys-ok? tila paivitetty))
               tilat)))

(defrecord JarjestelmanTila []
  component/Lifecycle
  (start
    [{db :db
      http :http-palvelin
      :as this}]
    (julkaise-palvelu
      http
      :hae-sonjan-tila
      (fn [kayttaja]
        (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-sonjajonot kayttaja)
        (hae-sonjan-tila db))
      {:kysely-spec ::sd/hae-jonojen-tilat-kysely
       :vastaus-spec ::sd/hae-jonojen-tilat-vastaus})
    this))
