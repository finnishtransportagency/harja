(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [harja.domain.mhu :as mhu]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]))

(defn jasenna-rahavaraukset-tarjouksesta
  "Muokkaa tarjouksen tietomallin rahavaraukset sopivaksi kustannussuunnitelman käyttöön.
  Saadaan [{:nimi <rahavarausnimi> :summa <summa> :summa-indeksikorjattu nil} ...]"
  [tarjous suunnitellut-rahavaraukset hoitovuoden-alkuvuosi]
  (let [tarjous-rahavaraukset (filter #(= "tavoitehintaiset-rahavaraukset" (:osio %)) (:tarjous tarjous))
        rahavaraus-rivit (reduce (fn [lopulliset tarjous-rahavaraus]
                                   (let [vuosittainen-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot tarjous-rahavaraus))))
                                         suunniteltu-rahavaraus (some #(when (= (:nimi tarjous-rahavaraus) (:nimi %)) %) suunnitellut-rahavaraukset)]
                                     (vec (concat lopulliset [{:nimi (:nimi tarjous-rahavaraus)
                                                               :tarjous-summa vuosittainen-summa
                                                               :suunniteltu-summa (:suunniteltu-summa suunniteltu-rahavaraus)
                                                               :suunniteltu-summa-indeksikorjattu (:suunniteltu-summa-indeksikorjattu suunniteltu-rahavaraus)}]))))
                           [] tarjous-rahavaraukset)]
    rahavaraus-rivit))

(defn- laske-2019-jjh-yhteen [johto-ja-hallintokorvaukset]
  (let [summa (apply + (map
                         (fn [rivi]
                           (if (and (:tuntipalkka rivi) (:tunnit rivi))
                             (* (:tuntipalkka rivi) (:tunnit rivi))
                             0))
                         johto-ja-hallintokorvaukset))]
    summa))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [;; Urakan sopimus id
          sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
          ;; Urakan parametrit
          urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
          urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          ;; Varmistetaan, että ei edes yritetä hakea tietoja urakkakauden ulkopuolelta
          hoitovuoden-alkuvuosi (cond
                                  (< hoitovuoden-alkuvuosi urakan-alkuvuosi) urakan-alkuvuosi
                                  (> hoitovuoden-alkuvuosi urakan-loppuvuosi) urakan-loppuvuosi
                                  :else hoitovuoden-alkuvuosi)

          vahvistukset (suunnitelma-q/indeksikorjaukset-vahvistettu? db
                         {:urakka-id urakka-id
                          :alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
                          :loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))})
          indeksikorjaukset-vahvistettu? (every? true? (flatten (map vals vahvistukset)))

          kiinteat (suunnitelma-q/hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          kiinteat (map (fn [tyo]
                          (-> tyo
                            (assoc :toimenpide-avain (mhu/toimenpide->toimenpide-avain (:koodi tyo)))
                            (assoc :toimenpide-nimi (mhu/toimenpide->nimi (mhu/toimenpide->toimenpide-avain (:koodi tyo))))))
                     kiinteat)
          ;; Indeksikerroin
          indeksikerroin (:indeksikerroin
                           (first
                             (filter
                               #(= hoitovuoden-alkuvuosi (:vuosi %))
                               (budjettisuunnittelu/hae-urakan-indeksikertoimet db kayttaja {:urakka-id urakka-id}))))

          ;; Hae tarjouksen tiedot
          tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)

          ;; Kaikki kustannussuunnitelman summat vaikuttaa tavoitehintaan
          ;; Pysyvät muutokset lisätään mukaan joko vähentämään tai lisäämään tavoitehintaa
          hankinnat-yht (:yhteensa (last kiinteat))

          ;: Hae rahavaraukset
          suunnitellut-rahavaraukset (suunnitelma-q/hae-rahavaraukset db sopimus-id hoitovuoden-alkuvuosi)
          rahavaraukset (jasenna-rahavaraukset-tarjouksesta tarjous suunnitellut-rahavaraukset hoitovuoden-alkuvuosi)
          rahavaraukset-yht (apply + (map (fn [rivi] (if (:suunniteltu-summa rivi) (:suunniteltu-summa rivi) 0)) rahavaraukset))

          ;; Hae erillishankinnat
          erillishankinnat (suunnitelma-q/hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          erillishankinnat-yht (apply + (map (fn [rivi] (if (:summa rivi) (:summa rivi) 0)) erillishankinnat))

          ;; Hae johto- ja hallintokorvaukset - Eli toimenkuvien kustannukset
          toimenkuvat-tarjouksesta (filter #(= (:osio %) "johto-ja-hallintokorvaus") (:tarjous tarjous))
          johto-ja-hallintokorvaukset
          (cond
            (and (>= urakan-alkuvuosi 2019) (<= urakan-alkuvuosi 2024))
            (suunnitelma-q/hae-johto-ja-hallintokorvaukset-2019-2024 db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-alkuvuosi toimenkuvat-tarjouksesta)
            (>= urakan-alkuvuosi 2025)
            (suunnitelma-q/hae-johto-ja-hallintokorvaukset db urakka-id hoitovuoden-alkuvuosi toimenkuvat-tarjouksesta)
            :else (suunnitelma-q/hae-johto-ja-hallintokorvaukset db urakka-id hoitovuoden-alkuvuosi toimenkuvat-tarjouksesta))

          johto-ja-hallintokorvaukset-yht (cond
                                            ;; 2019 - 2021
                                            (and (>= urakan-alkuvuosi 2019) (<= urakan-alkuvuosi 2021))
                                            (laske-2019-jjh-yhteen johto-ja-hallintokorvaukset)
                                            ;; 2025 -> ja eteenpäin
                                            (>= urakan-alkuvuosi 2025) (apply + (map (fn [rivi] (if (:summa rivi) (:summa rivi) 0)) johto-ja-hallintokorvaukset))
                                            :else (apply + (map (fn [rivi] (if (:summa rivi) (:summa rivi) 0)) johto-ja-hallintokorvaukset)))

          ;; Hae hoidonjohtopalkkiot
          hoidonjohtopalkkiot (suunnitelma-q/hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          hoidonjohtopalkkiot-yht (apply + (map (fn [rivi] (if (:summa rivi) (:summa rivi) 0)) hoidonjohtopalkkiot))

          hoitovuoden-alun-tavoitehinta (+ hankinnat-yht rahavaraukset-yht erillishankinnat-yht johto-ja-hallintokorvaukset-yht hoidonjohtopalkkiot-yht)
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
             :urakan-alkuvuosi urakan-alkuvuosi
             :valittu-hoitokausi [(pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi)) (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))]
             :tarjous tarjous
             :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet kiinteat}
                                    :rahavaraukset rahavaraukset
                                    :erillishankinnat erillishankinnat
                                    :johto-ja-hallintokorvaukset johto-ja-hallintokorvaukset
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
    (suunnitelma-q/tallenna-erillishankinnat db kayttaja urakka-id (:erillishankinnat tiedot))
    (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn tallenna-tallenna-johto-ja-hallintokorvaukset [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-johto-ja-hallintokorvaukset :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          ;; Valitaan oikea avain riippuen urakan alkamisvuodesta
          ;; 2019-2024 käytetään vanhaa avainta, 2025- eteenpäin uutta avainta

          avain (if (<= urakan-alkuvuosi 2024)
                  :johto-ja-hallintokorvaukset-2019
                  :johto-ja-hallintokorvaukset-2025)]
      (suunnitelma-q/tallenna-johto-ja-hallintokorvaukset db kayttaja urakka-id (get tiedot avain))
      (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defn tallenna-hoidonjohtopalkkiot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-hoidonjohtopalkkiot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (suunnitelma-q/tallenna-hoidonjohtopalkkiot db kayttaja urakka-id (:hoidonjohtopalkkiot tiedot))
    (suunnitelma-q/paivita-tavoite-ja-kattohinta db kayttaja urakka-id hoitovuoden-alkuvuosi)
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn vahvista-tai-kumoa-tavoite-ja-kattohinta [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi vahvista?] :as tiedot}]
  (log/debug "vahvista-tai-kumoa-tavoite-ja-kattohinta :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [;; Tarkistetaan, että kilpailutettavat hankinnat, erillishankinnat, hoidonjohtopalkkiot on tallennettu.
          ;; Muuten ei voida vahvistaa tavoitehintaa.
          vahvistus-mahdollinen? (suunnitelma-q/voidaanko-vahvistaa-tavoitehintaa? db urakka-id hoitovuoden-alkuvuosi)
          _ (when vahvistus-mahdollinen?
              (suunnitelma-q/vahvista-tavoite-ja-kattohinta db kayttaja urakka-id vahvista? hoitovuoden-alkuvuosi))
          vastaus (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
          vastaus (if vahvistus-mahdollinen?
                    vastaus
                    (assoc-in vastaus [:kustannussuunnitelma :vahvistus-virhe] "Tietoja ei voitu vahvistaa. Kustannustietoja puuttuu. Tarkista ja korjaa tiedot."))]
      vastaus)))

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
      :tallenna-johto-ja-hallintokorvaukset-2025 ;; Lyhyempi nimi konfliktaa vanhan kanssa
      (fn [user tiedot]
        (tallenna-tallenna-johto-ja-hallintokorvaukset (:db this) user tiedot))
      {:kysely-spec ::k-domain/johto-ja-hallintokorvaus-2025})
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-johto-ja-hallintokorvaukset-2019 ;; Lyhyempi nimi konfliktaa vanhan kanssa
      (fn [user tiedot]
        (tallenna-tallenna-johto-ja-hallintokorvaukset (:db this) user tiedot))
      {:kysely-spec ::k-domain/johto-ja-hallintokorvaus-2019})
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
      :tallenna-johto-ja-hallintokorvaukset-2025
      :tallenna-johto-ja-hallintokorvaukset-2019
      :vahvista-tavoite-ja-kattohinta)
    this))
