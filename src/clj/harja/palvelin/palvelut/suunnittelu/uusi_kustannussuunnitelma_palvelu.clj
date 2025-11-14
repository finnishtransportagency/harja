(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [harja.domain.mhu :as mhu]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit :as apurit]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [;; Urakan sopimus id
          sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
          ;; Urakan parametrit
          urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
          urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          hoitovuosinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
          ;; Varmistetaan, että ei edes yritetä hakea tietoja urakkakauden ulkopuolelta
          hoitovuoden-alkuvuosi (cond
                                  (< hoitovuoden-alkuvuosi urakan-alkuvuosi) urakan-alkuvuosi
                                  (>= hoitovuoden-alkuvuosi urakan-loppuvuosi) (dec urakan-loppuvuosi)
                                  :else hoitovuoden-alkuvuosi)
          viimeinen-hoitovuosi? (boolean (= hoitovuoden-alkuvuosi (dec urakan-loppuvuosi)))

          kustannussuunnitelma-vahvistettu? (suunnitelma-q/kustannussuunnitelma-vahvistettu? db urakka-id hoitovuoden-alkuvuosi)

          kiinteat (suunnitelma-q/hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          kiinteat (map (fn [tyo]
                          (-> tyo
                            (assoc :toimenpide-avain (mhu/toimenpide->toimenpide-avain (:koodi tyo)))
                            (assoc :toimenpide-nimi (mhu/toimenpide->nimi (mhu/toimenpide->toimenpide-avain (:koodi tyo))))))
                     kiinteat)
          ;; Indeksikerroin
          indeksikertoimet (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)))
          indeksikerroin (:indeksikerroin indeksikertoimet)
          indeksikerroin-str (:indeksikerroin-str indeksikertoimet)

          ;; Hae tarjouksen tiedot
          tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)
          ;: Hae rahavaraukset
          rahavaraukset (suunnitelma-q/hae-rahavaraukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          ;; Hae erillishankinnat
          erillishankinnat (suunnitelma-q/hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)

          ;; Hae johto- ja hallintokorvaukset - Eli toimenkuvien kustannukset
          toimenkuvat-tarjouksesta (filter #(= (:osio %) "johto-ja-hallintokorvaus") (:tarjous tarjous))
          johto-ja-hallintokorvaukset
          (cond
            (and (>= urakan-alkuvuosi 2019) (<= urakan-alkuvuosi 2024))
            (suunnitelma-q/hae-johto-ja-hallintokorvaukset-2019-2024 db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-alkuvuosi toimenkuvat-tarjouksesta)
            (>= urakan-alkuvuosi 2025)
            (suunnitelma-q/hae-johto-ja-hallintokorvaukset-2025 db urakka-id hoitovuoden-alkuvuosi)
            :else (suunnitelma-q/hae-johto-ja-hallintokorvaukset-2025 db urakka-id hoitovuoden-alkuvuosi))

          ;; Hae hoidonjohtopalkkiot
          hoidonjohtopalkkiot (suunnitelma-q/hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
          tavoitetiedot (first (suunnitelma-q/hae-urakan-hoitovuoden-tavoitetiedot db {:hoitokausinumero hoitovuosinro
                                                                                       :urakka-id urakka-id}))

          ;; Nimestään huolimatta näytetään tarjouksen tavoitehinta
          ;hoitovuoden-alun-tavoitehinta (:tarjous_tavoitehinta tavoitetiedot)

          hoitovuoden-alun-tavoitehinta (:tavoitehinta tavoitetiedot)
          aiempien-vuosien-pysyvat-muutokset (muutos-palvelu/hae-aiempien-vuosien-pysyvat-muutokset db urakka-id hoitovuoden-alkuvuosi true)
          ;; Lasketaan indeksikorjaamaton pysyvien muutosten määrä, indeksikorjattu saatavilla :tavoitehinnan-muutos-indeksikorjattu
          pysyvat-muutokset-maara (reduce + (map :tavoitehinnan-muutos aiempien-vuosien-pysyvat-muutokset))

          hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta_indeksikorjattu tavoitetiedot)
          kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
          hoitovuoden-alun-kattohinta (:kattohinta tavoitetiedot)
          hoitovuoden-alun-indeksikorjattu-kattohinta (:kattohinta_indeksikorjattu tavoitetiedot)

          ;; Haetaan tieto, että onko tulevilla hoitovuosilla mitään arvoja tallennettuna.
          tulevaisuudessa-arvoja (suunnitelma-q/onko-tulevilla-hoitovuosilla-arvoja? db urakka-id sopimus-id hoitovuoden-alkuvuosi)

          k {:urakka-id urakka-id
             :urakan-alkuvuosi urakan-alkuvuosi
             :valittu-hoitokausi [(pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi)) (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))]
             :tarjous tarjous
             :tulevaisuudessa-arvoja? tulevaisuudessa-arvoja
             :viimeinen-hoitovuosi? viimeinen-hoitovuosi?
             :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet kiinteat}
                                    :rahavaraukset rahavaraukset
                                    :erillishankinnat erillishankinnat
                                    :johto-ja-hallintokorvaukset johto-ja-hallintokorvaukset
                                    :hoidonjohtopalkkiot hoidonjohtopalkkiot
                                    :hoitovuoden-alun-tavoitehinta hoitovuoden-alun-tavoitehinta
                                    :hoitovuoden-alun-indeksikorjattu-tavoitehinta hoitovuoden-alun-indeksikorjattu-tavoitehinta
                                    :hoitovuoden-alun-kattohinta hoitovuoden-alun-kattohinta
                                    :hoitovuoden-alun-indeksikorjattu-kattohinta hoitovuoden-alun-indeksikorjattu-kattohinta
                                    :pysyvat-muutokset aiempien-vuosien-pysyvat-muutokset
                                    :pysyvat-muutokset-maara pysyvat-muutokset-maara
                                    :indeksikerroin indeksikerroin
                                    :indeksikerroin-str indeksikerroin-str
                                    :kattohintakerroin kattohintakerroin
                                    :vahvistettu? kustannussuunnitelma-vahvistettu?
                                    :muokkaa-kattohinta-kasin (:muokkaa_kattohinta_kasin urakan-parametrit)}}]
      k)))

(defn tallenna-kilpailutettavat-hankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-kilpailutettavat-hankinnat :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-kilpailutettavat-hankinnat db kayttaja urakka-id vuosi (:toimenpiteet tiedot))
        #_ (suunnitelma-q/paivita-tavoite-ja-kattohinta db (:id kayttaja) urakka-id vuosi))
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defn tallenna-erillishankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-erillishankinnat :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-erillishankinnat db kayttaja urakka-id (:erillishankinnat tiedot) vuosi)
        #_ (suunnitelma-q/paivita-tavoite-ja-kattohinta db (:id kayttaja) urakka-id vuosi)))
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn tallenna-tallenna-johto-ja-hallintokorvaukset [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-johto-ja-hallintokorvaukset :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)

          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          ;; Valitaan oikea avain riippuen urakan alkamisvuodesta
          ;; 2019-2024 käytetään vanhaa avainta, 2025- eteenpäin uutta avainta

          avain (if (<= urakan-alkuvuosi 2024)
                  :johto-ja-hallintokorvaukset-2019
                  :johto-ja-hallintokorvaukset-2025)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-johto-ja-hallintokorvaukset db kayttaja urakka-id (get tiedot avain) vuosi)
        #_ (suunnitelma-q/paivita-tavoite-ja-kattohinta db (:id kayttaja) urakka-id vuosi))
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defn tallenna-hoidonjohtopalkkiot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-hoidonjohtopalkkiot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-hoidonjohtopalkkiot db kayttaja urakka-id (:hoidonjohtopalkkiot tiedot) vuosi)
        #_ (suunnitelma-q/paivita-tavoite-ja-kattohinta db (:id kayttaja) urakka-id vuosi)))
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn vahvista-tai-kumoa-tavoite-ja-kattohinta [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi vahvista? paivitetty-kattohinta] :as tiedot}]
  (log/debug "vahvista-tai-kumoa-tavoite-ja-kattohinta :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [virheet []
          urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
          urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
          ;; Riipumatta vahvistuksen onnistumisesta, aseta kattohinta, jos se on annettu
          _ (when (and paivitetty-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit))
              (suunnitelma-q/paivita-kasin-syotetty-kattohinta db (:id kayttaja) urakka-id hoitovuoden-alkuvuosi
                paivitetty-kattohinta urakan-indeksit
                (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
                urakan-parametrit))

          ;; Onko hoitovuoden tarjous tallennettu? Jos ei ole, niin ei voida vahvistaa.
          tarjous (tarjous-kyselyt/hae-tarjousrivit-tietokannasta db urakka-id)
          hoitovuoden-tarjous (first (filter #(= hoitovuoden-alkuvuosi (:hoitokauden_alkuvuosi %)) tarjous))
          virheet (if hoitovuoden-tarjous
                    virheet
                    (conj virheet (str "Hoitovuoden " hoitovuoden-alkuvuosi " tarjous puuttuu. Tietoja ei voida vahvistaa.")))

          ;; Onko indeksit valmiina
          indeksi-olemassa? (boolean (some #(= hoitovuoden-alkuvuosi (:vuosi %)) urakan-indeksit))
          virheet (if indeksi-olemassa?
                    virheet
                    (conj virheet (str "Indeksit puuttuvat hoitovuodelle " hoitovuoden-alkuvuosi ". Indeksit on lisättävä ennen vahvistusta.")))

          ;; Tarkistetaan, että kilpailutettavat hankinnat, erillishankinnat, hoidonjohtopalkkiot ja johto-ja hallintokorvaukset täsmää tarjouksen kanssa.
          ;; Muuten ei voida vahvistaa tavoitehintaa.
          puuttuvat-suunnitelmat (suunnitelma-q/puuttuvat-suunnitelmat db urakka-id hoitovuoden-alkuvuosi hoitovuoden-tarjous)
          suunnitelmat-annettu? (if (empty? puuttuvat-suunnitelmat)
                                  true
                                  false)
          _ (when (and suunnitelmat-annettu? indeksi-olemassa? hoitovuoden-tarjous)
              (suunnitelma-q/vahvista-tavoite-ja-kattohinta db kayttaja urakka-id vahvista? hoitovuoden-alkuvuosi))
          vastaus (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
          virheet (if suunnitelmat-annettu?
                    virheet
                    (conj virheet (str
                                    (when hoitovuoden-tarjous "Tietoja ei voitu vahvistaa. ") ;; Jos hoitovuoden tarjousta ei ole, tämä lisätään jo yllä
                                    "Kustannustietoja puuttuu. Tarkista " (str/join ", " puuttuvat-suunnitelmat))))]

      (if-not (empty? virheet)
        (assoc-in vastaus [:kustannussuunnitelma :vahvistus-virhe] (str/join " " virheet))
        vastaus))))

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
