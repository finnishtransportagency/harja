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
            [harja.domain.paallystys.pot :as pot]

            [harja.kyselyt.paallystys :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]))

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
            (assoc-in [:muutoshinta]
                      (or (some-> % :muutoshinta double) 0))
            (assoc-in [:kaasuindeksi]
                      (or (some-> % :kaasuindeksi double) 0)))))


(def jsonb->clojuremap
  (map #(-> %
            (assoc :ilmoitustiedot
                   (some-> %
                           :ilmoitustiedot
                           .getValue
                           (cheshire/decode true))))))

(defn hae-urakan-paallystyskohteet [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystyskohteet. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      muunna-desimaaliluvut-xf
                      (q/hae-urakan-paallystyskohteet db urakka-id sopimus-id))]
    (log/debug "Päällystyskohteet saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystyskohdeosat [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystyskohdeosat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", paallystyskohde-id: " paallystyskohde-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      muunna-desimaaliluvut-xf
                      (q/hae-urakan-paallystyskohteen-paallystyskohdeosat db urakka-id sopimus-id paallystyskohde-id))]
    (log/debug "Päällystyskohdeosat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystystoteumat [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystystoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      muunna-desimaaliluvut-xf
                      (q/hae-urakan-paallystystoteumat db urakka-id sopimus-id))]
    (log/debug "Päällystystoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      jsonb->clojuremap
                      (q/hae-urakan-paallystysilmoitus-paallystyskohteella db urakka-id sopimus-id paallystyskohde-id))]
    (log/debug "Päällystysilmoitus saatu: " (pr-str vastaus))
    vastaus))

(defn tallenna-paallystysilmoitus [db user {:keys [urakka-id sopimus-id lomakedata]}]
  (log/debug "Käsitellään päällystysilmoitus: " lomakedata ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", päällystyskohde-id:" (:paallystyskohde-id lomakedata))
  (oik/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  ;(skeema/validoi pot/+paallystysilmoitus+ lomakedata) FIXME Vaadi skeema kun yhteys toimii muuten (sallitaan frontilta muutama optional argument tai frontti poistaa ne)
  (jdbc/with-db-transaction [c db]
    ; FIXME Luo uuden, tarkista onko jo olemassa ja jos on niin päivitä
    (let [muutoshinta (reduce + (map (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi))) (:tyot lomakedata)))
          ilmoitus (cheshire/encode lomakedata)
          vastaus (q/luo-paallystysilmoitus<! db (:paallytyskohde-id lomakedata) ilmoitus muutoshinta (:id user))]
      (log/debug "Muutoshinta " muutoshinta)
      (log/debug "enkoodattu ilmoitusdata" ilmoitus)
      (hae-urakan-paallystystoteumat c user {:urakka-id  urakka-id
                                             :sopimus-id sopimus-id}))))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paallystyskohteet
                        (fn [user tiedot]
                          (hae-urakan-paallystyskohteet db user tiedot)))
      (julkaise-palvelu http :urakan-paallystyskohdeosat
                        (fn [user tiedot]
                          (hae-urakan-paallystyskohdeosat db user tiedot)))
      (julkaise-palvelu http :urakan-paallystystoteumat
                        (fn [user tiedot]
                          (hae-urakan-paallystystoteumat db user tiedot)))
      (julkaise-palvelu http :urakan-paallystysilmoitus-paallystyskohteella
                        (fn [user tiedot]
                          (hae-urakan-paallystysilmoitus-paallystyskohteella db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystysilmoitus
                        (fn [user tiedot]
                          (tallenna-paallystysilmoitus db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paallystyskohteet)
    this))
