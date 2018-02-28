(ns harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn vaadi-kohdeosa-ei-kuulu-yllapitokohteeseen
  [db yllapitokohde-id kohdeosa])

(defn tallenna-muut-kohdeosat [db user {:keys [urakka-id yllapitokohde-id muut-kohdeosat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)

  (doseq [kohdeosa muut-kohdeosat]
    (vaadi-kohdeosa-ei-kuulu-yllapitokohteeseen db yllapitokohde-id kohdeosa))

  (jdbc/with-db-transaction [db db]
    ;; (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id) <- Mikäs tää on?
    ;; TODO:
    ;; - Hae kannassa olevat kohteen muut osat
    ;; - Vertaile tallennettavia osia olemassaoleviin, jotta tiedät mitä poistetaan ja mitä tallennetaan
    ;; - Poista poistettavat osat
    ;; - Tallenna tallennettavat osat
    ;; - Päivitä päivitettävät osat
    ;; - Palauta frontille jotain järkevää

    (let [kannasta-loytyvat-osat (into #{}
                                       (map :id (yy/hae-yllapitokohteen-muut-kohdeosat db yllapitokohde-id)))
          poistettavat-osat (filter #(and (:poista %)
                                          (kannasta-loytyvat-osat %))
                                    muut-kohdeosat)
          tallennettavat-osat (remove :poista muut-kohdeosat)]
      (doseq [poistettava-osa poistettavat-osat]
        (q/poista-yllapitokohdeosa {:id (:id poistettava-osa) :urakka urakka-id}))
      (doseq [tallennettava-osa tallennettavat-osat]
        (if (kannasta-loytyvat-osat (:id tallennettava-osa))
          ;:nimi :tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys :tr_ajorata :tr_kaista :paallystetyyppi :raekoko :tyomenetelma :massamaara :toimenpide
          (q/paivita-yllapitokohdeosa tallennettava-osa)
          ;:yllapitokohde :nimi :tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys :tr_ajorata :tr_kaista :toimenpide :paallystetyyppi :raekoko :tyomenetelma :massamaara :ulkoinen-id
          (q/luo-yllapitokohdeosa tallennettava-osa))))))

(defn hae-yllapitokohteen-muut-kohdeosat [db user {:keys [urakka-id yllapitokohde-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)
  (yy/hae-yllapitokohteen-muut-kohdeosat db yllapitokohde-id))

(defrecord MuutKohdeosat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-muut-kohdeosat
                        (fn [user tiedot]
                          (hae-yllapitokohteen-muut-kohdeosat db user tiedot)))
      (julkaise-palvelu http :tallenna-muut-kohdeosat
                        (fn [user tiedot]
                          (tallenna-muut-kohdeosat db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-muut-kohdeosat
      :tallenna-muut-kohdeosat)
    this))