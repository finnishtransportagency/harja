(ns harja.palvelin.palvelut.hallinta.kojelauta
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.kojelauta :as q]))


(defn hae-urakat-kojelautaan [db kayttaja {:keys [hoitokauden-alkuvuosi urakka-idt] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset kayttaja)
  (let [urakat (mapv
                 (fn [ks-tilat]
                   (update ks-tilat :ks_tila konv/jsonb->clojuremap))
                 (q/hae-urakat-kojelautaan db {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                                               :urakat_annettu (boolean (not (empty? urakka-idt)))
                                               :urakka_idt urakka-idt}))]
    urakat))

(defrecord KojelautaHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakat-kojelautaan
      (fn [kayttaja hakuehdot]
        (hae-urakat-kojelautaan db kayttaja hakuehdot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakat-kojelautaan)
    this))
