(ns harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.domain.oikeudet :as oikeudet]))

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

(defn vaadi-kohdeosa-ei-kuulu-yllapitokohteeseen
  [db yllapitokohde-id kohdeosa])

(defn tallenna-muut-kohdeosat [db user {:keys [urakka-id yllapitokohde-id muut-kohdeosat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)

  (doseq [kohdeosa muut-kohdeosat]
    (assert (and (not (nil? (:tr-numero kohdeosa)))
                 (not (nil? (:tr-alkuosa kohdeosa)))
                 (not (nil? (:tr-alkuetaisyys kohdeosa)))
                 (not (nil? (:tr-loppuetaisyys kohdeosa)))
                 (not (nil? (:tr-loppuosa kohdeosa))))
            "Kohdeosan tr-numero, tr-alkuosa, tr-alkuetaisyys, tr-loppuosa ja tr-loppuetaisyys eivät saa olla nil")
    ;; TODO: Toteuta tää funktio
    (vaadi-kohdeosa-ei-kuulu-yllapitokohteeseen db yllapitokohde-id kohdeosa))

  (jdbc/with-db-transaction [db db]
    ;; TODO:
    ;; - Vertaile tallennettavia osia olemassaoleviin, jotta tiedät mitä poistetaan ja mitä tallennetaan
    ;; - Poista poistettavat osat
    ;; - Palauta frontille jotain järkevää

    (let [kannasta-loytyvat-osat (into #{}
                                       (map :id (yy/hae-yllapitokohteen-muut-kohdeosat db yllapitokohde-id)))
          poistettavat-osat (filter #(and (:poistettu %)
                                          (kannasta-loytyvat-osat (:id %)))
                                    muut-kohdeosat)
          tallennettavat-osat (remove :poistettu muut-kohdeosat)]
      (log/debug "KANNASTA LÖYTYVÄT OSAT: " (pr-str kannasta-loytyvat-osat))
      (log/debug "POISTETTAVAT OSAT: " (pr-str poistettavat-osat))
      (log/debug "TALLENNETAAVAT OSAT: " (pr-str tallennettavat-osat))
      (doseq [poistettava-osa poistettavat-osat]
        (q/poista-yllapitokohdeosa! db {:id (:id poistettava-osa) :urakka urakka-id}))
      (doseq [tallennettava-osa (set/rename tallennettavat-osat {:id :yllapitokohdeosa-id})]
        (if (kannasta-loytyvat-osat (:yllapitokohdeosa-id tallennettava-osa))
          ;:nimi :tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys :tr_ajorata :tr_kaista :toimenpide :paallystetyyppi :raekoko :tyomenetelma :massamaara
          (do (println "PÄIVITETÄÄN") (clojure.pprint/pprint (tallennus-parametrit false tallennettava-osa)) (q/paivita-yllapitokohdeosa<! db (tallennus-parametrit false tallennettava-osa)))
          ;:yllapitokohde :nimi :tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys :tr_ajorata :tr_kaista :toimenpide :paallystetyyppi :raekoko :tyomenetelma :massamaara :ulkoinen-id
          (q/luo-yllapitokohdeosa<! db (tallennus-parametrit true
                                                             (assoc tallennettava-osa
                                                               :yllapitokohde-id yllapitokohde-id))))))))

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