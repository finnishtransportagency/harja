(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit
  "Talvihoitoreittien UI:n endpointit."
  (:require [com.stuartsierra.component :as component]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.domain.tierekisteri :as tr]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]))


(defn hae-urakan-talvihoitoreitit [db user {:keys [urakka-id]}]
  ;; TODO: HOX muuta roolit excel
  #_(oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-talvihoitoreitit user urakka-id)
  (let [urakan-talvihoitoreitit (talvihoitoreitit-q/hae-urakan-talvihoitoreitit db {:urakka_id urakka-id})
        _ (log/info "hae-urakan-talvihoitoreitit :: urakan-talvihoitoreitit" urakan-talvihoitoreitit)
        talvihoitoreiti (mapv (fn [rivi]
                                (let [kalustot (mapv
                                                 #(konv/pgobject->map % :id :long :kalustotyyppi :string :maara :long)
                                                 (konv/pgarray->vector (:kalusto rivi)))
                                      rivi (-> rivi
                                             (assoc :kalustot kalustot)
                                             (dissoc :kalusto))
                                      ;; Hakea reitit erikseen
                                      reitit (talvihoitoreitit-q/hae-reitti-talvihoitoreitille db {:talvihoitoreitti_id (:id rivi)})
                                      reitit (map (fn [r]
                                                    (-> r
                                                      (assoc :sijainti (:reitti r))
                                                      (assoc :formatoitu-tr (tr/tr-osoite-moderni-fmt
                                                                              (:tie r) (:alkuosa r) (:alkuetaisyys r)
                                                                              (:loppuosa r) (:loppuetaisyys r)))
                                                      (assoc :hoitoluokka-str (hoitoluokat/talvihoitoluokan-nimi (:hoitoluokka r)))
                                                      (dissoc :reitti))) reitit)
                                      rivi (assoc rivi :reitit reitit)]
                                  rivi))
                          urakan-talvihoitoreitit)]
    talvihoitoreiti))

(defrecord Talvihoitoreitit []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]

    (julkaise-palvelut
      http-palvelin

      :hae-urakan-talvihoitoreitit
      (fn [user tiedot]
        (hae-urakan-talvihoitoreitit db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakan-talvihoitoreitit)
    this))
