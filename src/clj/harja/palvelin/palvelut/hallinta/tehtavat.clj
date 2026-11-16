(ns harja.palvelin.palvelut.hallinta.tehtavat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhmat-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit-kyselyt]
            [clojure.string :as str]))

(def db-tehtavat->tehtavat
  {:f1 :id,
   :f2 :nimi,
   :f3 :yksikko,
   :f4 :jarjestys,
   :f5 :api_seuranta,
   :f6 :suoritettavatehtava,
   :f7 :piilota,
   :f8 :api_tunnus,
   :f9 :mhu-tehtava?,
   :f10 :yksiloiva_tunniste,
   :f11 :voimassaolo_alkuvuosi,
   :f12 :voimassaolo_loppuvuosi,
   :f13 :kasin_lisattava_maara,
   :f14 :raportoi-tehtava?,
   :f15 :materiaaliluokka_id,
   :f16 :materiaalikoodi_id,
   :f17 :aluetieto,
   :f18 :maaramitattava?})

(defn- hae-tehtavaryhmaotsikot [db kayttaja tiedot haku]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (let [tehtavaryhmaotsikot (sort-by first
                              (group-by :otsikko (haku db)))
        otsikot-ja-ryhmat (reduce (fn [vastaus elementti]
                                    (let [tehtavaryhmat (->> (second elementti)
                                                          (mapv (fn [data]
                                                                  (-> data
                                                                    (update :tehtavat konversio/jsonb->clojuremap))))

                                                          (mapv #(update % :tehtavat
                                                                  (fn [rivit]
                                                                    (keep
                                                                      (fn [r]
                                                                        (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                                                          (-> r
                                                                            (clojure.set/rename-keys db-tehtavat->tehtavat))))
                                                                      rivit)))))
                                          data {:tehtavaryhmaotsikko_id (first elementti)
                                                :otsikko (first elementti)
                                                :tehtavaryhmat tehtavaryhmat}]
                                      (conj vastaus data)))
                            [] tehtavaryhmaotsikot)]
    otsikot-ja-ryhmat))

(defn hae-mhu-tehtavaryhmaotsikot [db kayttaja tiedot]
  (log/debug "hae-mhu-tehtavaryhmaotsikot :: tiedot:" (pr-str tiedot))
  (hae-tehtavaryhmaotsikot db kayttaja tiedot
    tehtavaryhmat-kyselyt/hae-mhu-tehtavaryhmaotsikot-tehtavaryhmat-ja-tehtavat))

(defn hae-vhu-tehtavaryhmaotsikot [db kayttaja tiedot]
  (log/debug "hae-vhu-tehtavaryhmaotsikot :: tiedot:" (pr-str tiedot))
  (hae-tehtavaryhmaotsikot db kayttaja tiedot
    tehtavaryhmat-kyselyt/hae-vhu-tehtavaryhmaotsikot-tehtavaryhmat-ja-tehtavat))

(defn hae-suoritettavat-tehtavat [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (log/debug "hae-suoritettavat-tehtavat :: tiedot:" (pr-str tiedot))
  (toimenpidekoodit-kyselyt/hae-suoritettavat-tehtavat db))

(defn- tallenna-tehtavaryhmat* [db kayttaja tiedot hae-otsikot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (log/debug "tallenna-tehtavaryhmat :: tiedot:" (pr-str tiedot))
  (doseq [tehtavaryhma (:muokatut-tehtavaryhmat tiedot)
        :let [;; Tarkista, että tehtäväryhmä on olemassa
              tehtavaryhadb (tehtavaryhmat-kyselyt/hae-tehtavaryhma db (:id (:tehtavaryhma_id tehtavaryhma)))
              _ (if (nil? tehtavaryhadb)
                  ;; Varoita käyttäjää tuntemattomasta tehtäväryhmästä
                  (throw (SecurityException. (str "Tehtäväryhmää " (:tehtavaryhma_id tehtavaryhma) " ei ole olemassa.")))
                  ;; Tehtäväryhmä löytyy, joten tallennetaan muutokset
                  (tehtavaryhmat-kyselyt/paivita-tehtavaryhma! db tehtavaryhma))]]

    ;; Palauta kaikki tehtäväryhmäotsikot ja niihin liittyvät tehtävät - uudistukset tulevat samalla
    (hae-otsikot db kayttaja tiedot)))

(defn tallenna-tehtavaryhmat [db kayttaja tiedot]
  (tallenna-tehtavaryhmat* db kayttaja tiedot hae-mhu-tehtavaryhmaotsikot))

(defn tallenna-vhu-tehtavaryhmat [db kayttaja tiedot]
  (tallenna-tehtavaryhmat* db kayttaja tiedot hae-vhu-tehtavaryhmaotsikot))

(defn- tallenna-tehtavat* [db kayttaja tiedot hae-otsikot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (log/debug "tallenna-tehtavat :: tiedot:" (pr-str tiedot))
  (doseq [tehtava (:muokatut-tehtavat tiedot)
          :let [;; Tarkista, että tehtava on olemassa
                tehtavadb (tehtavaryhmat-kyselyt/hae-tehtava db (:id (:id tehtava)))
                _ (if (nil? tehtavadb)
                  ;; Varoita käyttäjää tuntemattomasta tehtävästä
                    (throw (SecurityException. (str "Tehtävää " (:id tehtava) " ei ole olemassa.")))
                  ;; Tehtävä löytyy, joten tallennetaan muutokset
                    (tehtavaryhmat-kyselyt/paivita-tehtava! db tehtava))]]

    ;; Palauta kaikki tehtäväryhmäotsikot ja niihin liittyvät tehtävät - uudistukset tulevat samalla
    (hae-otsikot db kayttaja tiedot)))

(defn tallenna-tehtavat [db kayttaja tiedot]
  (tallenna-tehtavat* db kayttaja tiedot hae-mhu-tehtavaryhmaotsikot))

(defn tallenna-vhu-tehtavat [db kayttaja tiedot]
  (tallenna-tehtavat* db kayttaja tiedot hae-vhu-tehtavaryhmaotsikot))


(defrecord TehtavatHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-mhu-tehtavaryhmaotsikot
      (fn [kayttaja tiedot]
        (hae-mhu-tehtavaryhmaotsikot db kayttaja tiedot)))
    ;; vhu = vesivayla-hoito
    (julkaise-palvelu http-palvelin :hae-vhu-tehtavaryhmaotsikot
      (fn [kayttaja tiedot]
        (hae-vhu-tehtavaryhmaotsikot db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hallinta-tallenna-vhu-tehtavaryhmat
      (fn [kayttaja tiedot]
        (tallenna-vhu-tehtavaryhmat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hallinta-tallenna-vhu-tehtavat
      (fn [kayttaja tiedot]
        (tallenna-vhu-tehtavat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hae-suoritettavat-tehtavat
      (fn [kayttaja tiedot]
        (hae-suoritettavat-tehtavat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hallinta-tallenna-tehtavaryhmat
      (fn [kayttaja tiedot]
        (tallenna-tehtavaryhmat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hallinta-tallenna-tehtavat
      (fn [kayttaja tiedot]
        (tallenna-tehtavat db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-mhu-tehtavaryhmaotsikot
      :hae-vhu-tehtavaryhmaotsikot
      :hallinta-tallenna-vhu-tehtavaryhmat
      :hallinta-tallenna-vhu-tehtavat
      :hae-suoritettavat-tehtavat
      :hallinta-tallenna-tehtavaryhmat
      :hallinta-tallenna-tehtavat)
    this))
