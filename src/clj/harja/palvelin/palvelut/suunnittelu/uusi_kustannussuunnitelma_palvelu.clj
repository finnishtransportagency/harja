(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.kustannusarvioidut-tyot :as ka-q]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]))

(defn jasenna-rahavaraukset-tarjouksesta
  "Muokkaa tarjouksen tietomallin rahavaraukset sopivaksi kustannussuunnitelman käyttöön.
  Saadaan [{:nimi <rahavarausnimi> :summa <summa> :summa-indeksikorjattu nil} ...]"
  [tarjous hoitovuoden-alkuvuosi]
  (let [rahavaraukset (filter #(= "tavoitehintaiset-rahavaraukset" (:osio %)) (:tarjous tarjous))
        rahavaraus-rivit (reduce (fn [lopulliset rahavaraus]
                                   (let [vuosittainen-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot rahavaraus))))]
                                     (vec (concat lopulliset [{:nimi (:nimi rahavaraus) :summa vuosittainen-summa :summa-indeksikorjattu nil}]))))
                           [] rahavaraukset)]
    rahavaraus-rivit))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [;; Urakan sopimus id
          sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
          ;; Urakan parametrit
          urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))

          vahvistukset (suunnitelma-q/indeksikorjaukset-vahvistettu? db
                         {:urakka-id urakka-id
                          :alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
                          :loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))})
          indeksikorjaukset-vahvistettu? (every? true? (flatten (map vals vahvistukset)))

          kiinteat (suunnitelma-q/hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          ;; Indeksikerroin
          indeksikerroin (:indeksikerroin
                           (first
                             (filter
                               #(= hoitovuoden-alkuvuosi (:vuosi %))
                               (budjettisuunnittelu/hae-urakan-indeksikertoimet db kayttaja {:urakka-id urakka-id}))))

          ;; Hae tarjouksen tiedot
          tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)


          rahavaraukset (if indeksikorjaukset-vahvistettu?
                          (suunnitelma-q/hae-rahavaraukset db sopimus-id hoitovuoden-alkuvuosi)
                          ;; Jäsennä rahavaraukset tarjouksesta
                          (jasenna-rahavaraukset-tarjouksesta tarjous hoitovuoden-alkuvuosi))

          ;; Hae erillishankinnat
          erillishankinnat (suunnitelma-q/hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          ;; Hae hoidonjohtopalkkiot
          hoidonjohtopalkkiot (suunnitelma-q/hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)

          ;; Kaikki kustannussuunnitelman summat vaikuttaa tavoitehintaan
          ;; Pysyvät muutokset lisätään mukaan joko vähentämään tai lisäämään tavoitehintaa
          hankinnat-yht (:yhteensa (last kiinteat))
          rahavaraukset-yht (apply + (map (fn [rivi] (:summa rivi 0)) rahavaraukset))
          erillishankinnat-yht (apply + (map (fn [rivi] (:summa rivi 0)) erillishankinnat))
          hoidonjohtopalkkiot-yht (apply + (map (fn [rivi] (:summa rivi 0)) hoidonjohtopalkkiot))
          hoitovuoden-alun-tavoitehinta (+ hankinnat-yht rahavaraukset-yht erillishankinnat-yht hoidonjohtopalkkiot-yht)
          pysyvat-muutokset-maara 0
          hoitovuoden-alun-tavoitehinta (+ hoitovuoden-alun-tavoitehinta pysyvat-muutokset-maara)
          hoitovuoden-alun-indeksikorjattu-tavoitehinta (or (when indeksikerroin
                                                              (* indeksikerroin hoitovuoden-alun-tavoitehinta)) 0)
          kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
          hoitovuoden-alun-kattohinta (or (when kattohintakerroin
                                            (* kattohintakerroin hoitovuoden-alun-tavoitehinta)) 0)
          hoitovuoden-alun-indeksikorjattu-kattohinta (or (when (and indeksikerroin hoitovuoden-alun-kattohinta)
                                                            (* indeksikerroin hoitovuoden-alun-kattohinta)) 0)
          k {:urakka-id urakka-id
             :tarjous tarjous
             :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet kiinteat}
                                    :rahavaraukset rahavaraukset
                                    :erillishankinnat erillishankinnat
                                    :hoidonjohtopalkkiot hoidonjohtopalkkiot
                                    :hoitovuoden-alun-tavoitehinta hoitovuoden-alun-tavoitehinta
                                    :hoitovuoden-alun-indeksikorjattu-tavoitehinta hoitovuoden-alun-indeksikorjattu-tavoitehinta
                                    :hoitovuoden-alun-kattohinta hoitovuoden-alun-kattohinta
                                    :hoitovuoden-alun-indeksikorjattu-kattohinta hoitovuoden-alun-indeksikorjattu-kattohinta
                                    :pysyvat-muutokset-maara pysyvat-muutokset-maara
                                    :indeksikerroin indeksikerroin
                                    :kattohintakerroin kattohintakerroin
                                    :vahvistettu? indeksikorjaukset-vahvistettu?}}]
      k)))

(defn tallenna-kilpailutettavat-hankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-kilpailutettavat-hankinnat :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (suunnitelma-q/tallenna-kilpailutettavat-hankinnat db kayttaja urakka-id hoitovuoden-alkuvuosi (:toimenpiteet tiedot))
    (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn tallenna-erillishankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-erillishankinnat :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (suunnitelma-q/tallenna-erillishankinnat db kayttaja urakka-id hoitovuoden-alkuvuosi (:erillishankinnat tiedot))
    (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn tallenna-hoidonjohtopalkkiot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-hoidonjohtopalkkiot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (suunnitelma-q/tallenna-hoidonjohtopalkkiot db kayttaja urakka-id hoitovuoden-alkuvuosi (:hoidonjohtopalkkiot tiedot))
    (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn vahvista-tai-kumoa-tavoite-ja-kattohinta [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi vahvista?] :as tiedot}]
  (log/debug "vahvista-tai-kumoa-tavoite-ja-kattohinta :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [;; Urakan sopimus id
          sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
          urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
          hoitokausinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
          vahvistus-pvm (pvm/nyt)
          hoitokauden-alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
          hoitokauden-loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))
          _ (suunnitelma-q/vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille! db
              {:urakka-id urakka-id
               :alkupvm hoitokauden-alkupvm
               :loppupvm hoitokauden-loppupvm
               :vahvista? vahvista?
               :vahvistaja (:id kayttaja)
               :vahvistus-pvm vahvistus-pvm})

          ;; Rahavaraukset on näytetty tähän asti tarjouksen tiedoista. Kopioidaan ne nyt kustannusarvoitu_tyo tauluun
          ;; Hae ensin tarjouksen tiedot
          tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)
          rahavaraukset (filter #(= "tavoitehintaiset-rahavaraukset" (:osio %)) (:tarjous tarjous))

          _ (mapv (fn [rahavaraus]
                    (let [rahavaraus-id (:rahavaraus-id rahavaraus)
                          vuosittainen-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot rahavaraus))))

                          ;; Jokaisella kustannusarvoitu_tyo -rivillä pitää olla toimenpideinstanssi.
                          ;; Rahavaraukset eivät kuulu millekään tällä hetkellä tiedetylle toimenpideinstanssille.
                          ;; Mutta yksinkertaisuuden vuoksi toimenpideinstanssin pakollisuutta ei lähdetty muuttamaan, vaan laitetaan
                          ;; Rahavaraukselle vain jokin toimenpideinstanssi. Sen olemassaolo filtteröidään muualla pois.
                          ensimmainen-toimenpideinstanssi-id (:id (first (rahavaraus-kyselyt/hae-rahavarauksen-toimenpideinstanssi db {:urakka_id urakka-id})))

                          ;; Päivitetään rahavarauksen summa ja indeksikorjattu summa kustannusarvioitu_työ tauluun
                          kt-rahavaraus-kuukaudet (ka-q/hae-rahavarauskustannus db {:rahavaraus_id rahavaraus-id
                                                                                    :vuosi hoitovuoden-alkuvuosi
                                                                                    :sopimus_id sopimus-id})

                          dbrahavaraus (if (not (empty? kt-rahavaraus-kuukaudet))
                                         (let [kk (atom 0)] ;; Lokaalisti voi olla vaikka vain kolmena kuukautena summa, vaikka pitäisi olla 12
                                           (doseq [r kt-rahavaraus-kuukaudet
                                                   :let [_ (swap! kk inc)
                                                         kuukausimaara (count kt-rahavaraus-kuukaudet)
                                                         kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa kuukausimaara))) ;; Tallenna nil kantaan, jos nil arvo on syötetty
                                                         viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* (dec kuukausimaara) kuukausisumma))))]]
                                             ;; Rahavarauksesta ei voi muuttua, kuin summa
                                             (ka-q/paivita-rahavaraus<! db {:summa (if (and (>= kuukausimaara 9) (= @kk 9)) viimeinen-kuukausisumma kuukausisumma)
                                                                            :muokattu (pvm/nyt)
                                                                            :muokkaaja (:id kayttaja)
                                                                            :id (:id r)})))
                                         (doseq [kk (range 1 13)
                                                 :let [kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa 12)))
                                                       viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* 11 kuukausisumma))))]]
                                           (ka-q/lisaa-rahavaraus<! db {:vuosi (if (< kk 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
                                                                        :kuukausi kk
                                                                        :sopimus_id sopimus-id
                                                                        :toimenpideinstanssi_id ensimmainen-toimenpideinstanssi-id
                                                                        :tehtava_id nil
                                                                        :rahavaraus_id rahavaraus-id
                                                                        :summa (if (= kk 9) viimeinen-kuukausisumma kuukausisumma)
                                                                        :luoja (:id kayttaja)})))]
                      dbrahavaraus))
              rahavaraukset)

          _ (suunnitelma-q/vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille! db
              {:urakka-id urakka-id
               :alkupvm hoitokauden-alkupvm
               :loppupvm hoitokauden-loppupvm
               :vahvista? vahvista?
               :vahvistaja (:id kayttaja)
               :vahvistus-pvm vahvistus-pvm})
          _ (suunnitelma-q/vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille! db
              {:urakka-id urakka-id
               :alkupvm hoitokauden-alkupvm
               :loppupvm hoitokauden-loppupvm
               :vahvista? vahvista?
               :vahvistaja (:id kayttaja)
               :vahvistus-pvm vahvistus-pvm})
          _ (suunnitelma-q/vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille! db
              {:urakka-id urakka-id
               :vuosi hoitovuoden-alkuvuosi
               :hoitovuosi-nro hoitokausinumero
               :vahvista? vahvista?
               :vahvistaja (:id kayttaja)
               :vahvistus-pvm vahvistus-pvm})]
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defrecord UusiKustannussuunnitelmaPalvelu []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      (fn [user tiedot]
        (hae-kustannussuunnitelman-tiedot (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-kilpailutettavat-hankinnat
      (fn [user tiedot]
        (tallenna-kilpailutettavat-hankinnat (:db this) user tiedot))
      {:kysely-spec ::k-domain/kilpailutettavat-hankinnat})
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-erillishankinnat
      (fn [user tiedot]
        (tallenna-erillishankinnat (:db this) user tiedot))
      {:kysely-spec ::k-domain/erillishankinta})
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-hoidonjohtopalkkiot
      (fn [user tiedot]
        (tallenna-hoidonjohtopalkkiot (:db this) user tiedot))
      {:kysely-spec ::k-domain/hoidonjohtopalkkio})
    (julkaise-palvelu (:http-palvelin this)
      :vahvista-tavoite-ja-kattohinta
      (fn [user tiedot]
        (vahvista-tai-kumoa-tavoite-ja-kattohinta (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      :tallenna-kilpailutettavat-hankinnat
      :tallenna-erillishankinnat
      :tallenna-hoidonjohtopalkkiot
      :vahvista-tavoite-ja-kattohinta)
    this))
