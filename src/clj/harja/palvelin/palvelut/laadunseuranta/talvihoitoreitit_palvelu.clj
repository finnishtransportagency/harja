(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-palvelu
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
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (let [urakan-talvihoitoreitit (talvihoitoreitit-q/hae-urakan-talvihoitoreitit db {:urakka_id urakka-id})
        _ (log/debug "hae-urakan-talvihoitoreitit :: urakan-talvihoitoreitit" urakan-talvihoitoreitit)
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
                                      ;; Jaotellaan reitti hoitoluokittan
                                      hoitoluokkat (vec (vals (group-by :hoitoluokka-str (map (fn [r]
                                                                                                (dissoc r :sijainti :tie :alkuosa
                                                                                                  :alkuetaisyys :loppuosa :loppuetaisyys
                                                                                                  :hoitoluokka :id :formatoitu-tr)) reitit))))
                                      ;; Lasketaan jokaiselle hoitoluokalle pituus
                                      hoitoluokkat (map (fn [hoitoluokka-vec]
                                                          {:hoitoluokka-str (:hoitoluokka-str (first hoitoluokka-vec))
                                                           :pituus (reduce + (map :laskettu_pituus hoitoluokka-vec))})
                                                     hoitoluokkat)
                                      rivi (-> rivi
                                             (assoc :reitit reitit)
                                             (assoc :hoitoluokat hoitoluokkat))]
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
