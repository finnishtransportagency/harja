(ns harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tierekisteri]))

(defn tallennus-parametrit [luo-uusi?
                            {:keys [nimi tr-numero tr-alkuosa tr-alkuetaisyys
                                    tr-loppuosa tr-loppuetaisyys tr-ajorata
                                    tr-kaista tyomenetelma massamaara toimenpide
                                    raekoko paallystetyyppi hyppy?
                                    yllapitokohde-id yllapitokohdeosa-id urakka-id]
                             :as data}]
  (let [yhteiset {:nimi nimi
                  :tr_numero tr-numero
                  :tr_alkuosa tr-alkuosa
                  :tr_alkuetaisyys tr-alkuetaisyys
                  :tr_loppuosa tr-loppuosa
                  :tr_loppuetaisyys tr-loppuetaisyys
                  :tr_ajorata tr-ajorata
                  :tr_kaista tr-kaista
                  :tyomenetelma tyomenetelma
                  :massamaara massamaara
                  :toimenpide toimenpide
                  :raekoko raekoko
                  :paallystetyyppi paallystetyyppi
                  :hyppy (if (some? hyppy?) hyppy? false)}]
    (merge yhteiset
           (if luo-uusi?
             {:yllapitokohde yllapitokohde-id
              :ulkoinen-id nil}
             {:id yllapitokohdeosa-id
              :urakka urakka-id}))))

(defn- validoi-kysely [db {:keys [urakka-id yllapitokohde-id muut-kohdeosat vuosi]}]
  (let [yllapitokohde (first (q/hae-yllapitokohde db {:id yllapitokohde-id}))
        kaikki-vuoden-yllapitokohdeosat-harjassa (q/hae-saman-vuoden-yllapitokohdeosat db {:vuosi vuosi})
        paalekkaisyydet (keep (fn [[grid-id kohdeosa]]
                                (some #(when (and (tierekisteri/kohdeosat-paalekkain? kohdeosa %)
                                                  (not (= (:kohdeosa-id %) (:id kohdeosa))))
                                         {:rivi grid-id
                                          :viesti "Kohdeosa on päälekkäin toisen kohdeosan kanssa"
                                          :validointivirhe :kohteet-paallekain
                                          :kohteet [% kohdeosa]})
                                      kaikki-vuoden-yllapitokohdeosat-harjassa))
                              muut-kohdeosat)]
    ;; Asserteille on jo frontilla check
    (doseq [kohdeosa (vals muut-kohdeosat)
            :let [muut-rivit (keep (fn [[grid-id muu-kohdeosa]]
                                     (when-not (= (:id kohdeosa) (:id muu-kohdeosa))
                                       muu-kohdeosa))
                                   muut-kohdeosat)]]
      ;; Tarkistetaan, että joku tie on annettu
      (assert (and (not (nil? (:tr-numero kohdeosa)))
                   (not (nil? (:tr-alkuosa kohdeosa)))
                   (not (nil? (:tr-alkuetaisyys kohdeosa)))
                   (not (nil? (:tr-loppuetaisyys kohdeosa)))
                   (not (nil? (:tr-loppuosa kohdeosa))))
              "Kohdeosan tr-numero, tr-alkuosa, tr-alkuetaisyys, tr-loppuosa ja tr-loppuetaisyys eivät saa olla nil")
      ;; Tarkistetaan, että kohteenosa ei ole pääkohteen sisällä
      (assert (not (tierekisteri/tr-vali-paakohteen-sisalla? yllapitokohde kohdeosa))
              "Muihin kohdeosiin ei tulisi tallentaa kohteen sisäisiä osia")
      ;; Tarkistetaan, että kohteenosa ei ole muiden kohteenosien kanssa päälekkäin
      (assert (not (some #(when (tierekisteri/kohdeosat-paalekkain? % kohdeosa)
                            true)
                         muut-rivit))
              "Annetut kohteenosat ovat päällekkäin"))
    (when-not (empty? paalekkaisyydet)
      paalekkaisyydet)))

(defn tallenna-muut-kohdeosat [db user {:keys [urakka-id yllapitokohde-id muut-kohdeosat vuosi] :as params}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)

  (jdbc/with-db-transaction [db db]
    (if-let [virhe (validoi-kysely db params)]
      virhe
      (let [muut-kohdeosat (vals muut-kohdeosat)
            kannasta-loytyvat-osat (into #{}
                                         (map :id (yy/hae-yllapitokohteen-muut-kohdeosat db yllapitokohde-id)))
            poistettavat-osat (filter #(and (:poistettu %)
                                            (kannasta-loytyvat-osat (:id %)))
                                      muut-kohdeosat)
            tallennettavat-osat (remove :poistettu muut-kohdeosat)]
        (doseq [poistettava-osa poistettavat-osat]
          (q/poista-yllapitokohdeosa! db {:id (:id poistettava-osa) :urakka urakka-id}))
        (doseq [tallennettava-osa (set/rename tallennettavat-osat {:id :yllapitokohdeosa-id})]
          (if (kannasta-loytyvat-osat (:yllapitokohdeosa-id tallennettava-osa))
            (q/paivita-yllapitokohdeosa<! db (tallennus-parametrit false tallennettava-osa))
            (q/luo-yllapitokohdeosa<! db (tallennus-parametrit true
                                                               (assoc tallennettava-osa
                                                                 :yllapitokohde-id yllapitokohde-id)))))
        {:onnistui? true}))))

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