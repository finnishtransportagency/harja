(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]))

(defn hae-kiinteat-kustannukset [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Haetaan urakan toimenpiteet
        toimenpiteet (suunnitelma-q/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        ;; Kiinteähintaiset kustannukset
        kiinteat (reduce (fn [acc {:keys [nimi toimenpideinstanssi-id] :as toimenpide}]
                           (let [kiinteat (suunnitelma-q/hae-kiintea-kustannus-kuukausittain
                                            db {:sopimus-id sopimus-id
                                                :vuosi hoitovuoden-alkuvuosi
                                                :toimenpideinstanssi-id toimenpideinstanssi-id})
                                 kiinteat-alkukausi (filter #(>= (:kuukausi %) 10) kiinteat)
                                 kiinteat-loppukausi (filter #(<= (:kuukausi %) 9) kiinteat)
                                 alkukausi (if (seq kiinteat-alkukausi) (apply + (map :summa kiinteat-alkukausi)) 0)
                                 alkukausi-indeksikorjattu (if (seq kiinteat-alkukausi)
                                                             (apply + (map (fn [rivi]
                                                                             (if (:summa_indeksikorjattu rivi)
                                                                               (:summa_indeksikorjattu rivi)
                                                                               0))
                                                                        kiinteat-loppukausi))
                                                             0)
                                 loppukausi (if (seq kiinteat-loppukausi) (apply + (map :summa kiinteat-loppukausi)) 0)
                                 loppukausi-indeksikorjattu (if (seq kiinteat-loppukausi)
                                                              (apply + (map (fn [rivi]
                                                                              (if (:summa_indeksikorjattu rivi)
                                                                                (:summa_indeksikorjattu rivi)
                                                                                0))
                                                                         kiinteat-loppukausi))
                                                              0)]
                             (conj acc {:nimi nimi
                                        :toimenpideinstanssi-id toimenpideinstanssi-id
                                        :alkukausi alkukausi
                                        :alkukausi-indeksikorjattu alkukausi-indeksikorjattu
                                        :loppukausi loppukausi
                                        :loppukausi-indeksikorjattu loppukausi-indeksikorjattu
                                        :yhteensa (+ alkukausi loppukausi)
                                        :yhteensa-indeksikorjattu (+ alkukausi-indeksikorjattu loppukausi-indeksikorjattu)
                                        :pysyvat-muutokset "Ei muutoksia"})))
                   []
                   toimenpiteet)
        ;; Yhteenvetorivi
        yhteenveto {:nimi "Yhteensä"
                    :alkukausi (apply + (map :alkukausi kiinteat))
                    :alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu kiinteat))
                    :loppukausi (apply + (map :loppukausi kiinteat))
                    :loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu kiinteat))
                    :yhteensa (+ (apply + (map :alkukausi kiinteat)) (apply + (map :loppukausi kiinteat)))
                    :yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu kiinteat)) (apply + (map :loppukausi-indeksikorjattu kiinteat)))
                    :pysyvat-muutokset "Ei muutoksia"}
        kiinteat (conj kiinteat yhteenveto)]
    kiinteat))

(defn jasenna-rahavaraukset-tarjouksesta [tarjous hoitovuoden-alkuvuosi]
  (let [; haetaan urakan rahavaraukset
        rahavaraukset (filter #(= "tavoitehintaiset-rahavaraukset" (:osio %)) (:tarjous tarjous))
        rahavaraus-rivit (reduce (fn [lopulliset rahavaraus]
                                   (let [vuosittainen-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot rahavaraus))))]
                                     (vec (concat lopulliset [{:nimi (:nimi rahavaraus) :summa vuosittainen-summa :summa-indeksikorjattu nil}]))))
                           [] rahavaraukset)]
    rahavaraus-rivit))

(defn hae-erillishankinnat [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (suunnitelma-q/hae-toimenpideinstanssi-koodilla db {:urakka-id urakka-id
                                                                                           :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        erillishankinnat (when hoidonjohto-tpi-id
                           (suunnitelma-q/hae-erillishankinta-kuukausittain db
                             {:sopimus-id sopimus-id
                              :vuosi hoitovuoden-alkuvuosi
                              :tehtavaryhma-id (:id tehtavaryhma)
                              :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        erillishankinnat (if (seq erillishankinnat)
                           ;; Jos on tallennettu jo erillishankintoja, niin lisätään niihin kalenterikuukausi
                           (map (fn [rivi]
                                  (merge rivi
                                    {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                          (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)}))
                             erillishankinnat)
                           ;; Jos ei ole tallennettu erillishankintoja, niin luodaan nolla arvot
                           (mapv (fn [kk]
                                   (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))
                                         _ (println "(str \"01.\" kk \".\" vuosi)" (str "01." kk "." vuosi))]
                                     {:id nil
                                      :sopimus sopimus-id
                                      :tehtavaryhma (:id tehtavaryhma)
                                      :toimenpideinstanssi hoidonjohto-tpi-id
                                      :kuukausi kk
                                      :vuosi vuosi
                                      :summa 0
                                      :summa_indeksikorjattu nil
                                      :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)}))
                             [10 11 12 1 2 3 4 5 6 7 8 9]))]
    erillishankinnat))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (let [;; Urakan sopimus id
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        kiinteat (hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        ;; Indeksikerroin
        indeksikerroin (:indeksikerroin
                         (first
                           (filter
                             #(= hoitovuoden-alkuvuosi (:vuosi %))
                             (budjettisuunnittelu/hae-urakan-indeksikertoimet db kayttaja {:urakka-id urakka-id}))))

        ;; Hae tarjouksen tiedot
        tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)
        ;; Jäsennä rahavaraukset tarjouksesta
        rahavaraukset (jasenna-rahavaraukset-tarjouksesta tarjous hoitovuoden-alkuvuosi)
        ;; Hae erillishankinnat
        erillishankinnat (hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)

        k {:urakka-id urakka-id
           :tarjous tarjous
           :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet kiinteat}
                                  :rahavaraukset rahavaraukset
                                  :erillishankinnat erillishankinnat
                                  :indeksikerroin indeksikerroin}}]
    k))

(defn tallenna-kilpailutettavat-hankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-kilpailutettavat-hankinnat :: tiedot: " tiedot)
  (suunnitelma-q/tallenna-kilpailutettavat-hankinnat db kayttaja urakka-id hoitovuoden-alkuvuosi (:toimenpiteet tiedot))
  (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))

(defn tallenna-erillishankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-erillishankinnat :: tiedot: " tiedot)
  (suunnitelma-q/tallenna-erillishankinnat db kayttaja urakka-id hoitovuoden-alkuvuosi (:erillishankinnat tiedot))
  (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))

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

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      :tallenna-kilpailutettavat-hankinnat
      :tallenna-erillishankinnat)
    this))
