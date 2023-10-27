(ns harja.palvelin.palvelut.hallinta.tehtavat
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]
            [harja.kyselyt.konversio :as konversio]
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
   :f17 :aluetieto})

(defn hae-mhu-tehtavaryhmat [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (log/info "hae-mhu-tehtavaryhmat :: tiedot:" (pr-str tiedot))
  (let [mhu-tehtavaryhmat (tehtavamaarat-kyselyt/hae-mhu-tehtavaryhmat-ja-tehtavat db)
        mhu-tehtavaryhmat (->> mhu-tehtavaryhmat
                            (map (fn [ryhma]
                                   (-> ryhma
                                     (update :tehtavat konversio/jsonb->clojuremap))))
                            (map #(update % :tehtavat
                                    (fn [rivit]
                                      (keep
                                        (fn [r]
                                          (when (not (nil? (:f1 r))) ;; Varmista että Left joinilla haettuja rivejä on
                                            (-> r
                                              (clojure.set/rename-keys db-tehtavat->tehtavat))))
                                        rivit)))))]
    mhu-tehtavaryhmat))


(defrecord TehtavatHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-mhu-tehtavaryhmat
      (fn [kayttaja tiedot]
        (hae-mhu-tehtavaryhmat db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-mhu-tehtavaryhmat)
    this))
