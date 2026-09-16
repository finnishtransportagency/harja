(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.domain.mhu :as mhu]
            [harja.kyselyt.kulut :as kulut-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit :as apurit]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as k-domain]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

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
          vanha-urakka? (boolean
                          (when urakan-alkuvuosi
                            (< urakan-alkuvuosi 2025)))
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
          tarjous (if-not vanha-urakka?
                    (tarjous-kyselyt/hae-tarjous db urakka-id)
                    (suunnitelma-q/hae-vanhan-urakan-hoitovuoden-tarjous db
                      {:urakka_id urakka-id
                       :hoitokausi hoitovuosinro}))

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

          aiempien-vuosien-pysyvat-muutokset (muutos-palvelu/hae-aiempien-vuosien-pysyvat-muutokset db urakka-id hoitovuoden-alkuvuosi true)
          ;; Lasketaan indeksikorjaamaton pysyvien muutosten määrä, indeksikorjattu saatavilla :tavoitehinnan-muutos-indeksikorjattu
          pysyvat-muutokset-maara (reduce + (map :tavoitehinnan-muutos aiempien-vuosien-pysyvat-muutokset))

          hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta_indeksikorjattu tavoitetiedot)
          kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
          hoitovuoden-alun-kattohinta (:kattohinta tavoitetiedot)
          hoitovuoden-alun-indeksikorjattu-kattohinta (:kattohinta_indeksikorjattu tavoitetiedot)
          laskutusraja-rivi (first (kulut-q/hae-urakan-laskutusraja db {:urakka-id urakka-id :hoitokausinro hoitovuosinro}))
          laskutusraja (:laskutusraja laskutusraja-rivi)
          laskutusraja-kaytossa? (:laskutusraja-kaytossa laskutusraja-rivi)

          ;; Haetaan osio-kohtaisesti onko tulevilla hoitovuosilla >0 euroja tallennettuna 
          tulevaisuudessa-arvoja (suunnitelma-q/onko-tulevilla-hoitovuosilla-arvoja? db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-loppuvuosi)
          tulevaisuudessa-arvoja (into {}
                                   (map (juxt :tyyppi :arvoja?))
                                   tulevaisuudessa-arvoja)

          hoitovuoden-alun-tavoitehinta (:tavoitehinta tavoitetiedot)

          k {:urakka-id urakka-id
             :urakan-alkuvuosi urakan-alkuvuosi
             :valittu-hoitokausi [(pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi)) (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))]
             :tarjous tarjous
             :vanha-urakka? vanha-urakka?
             :tulevaisuudessa-arvoja? {:muut (get tulevaisuudessa-arvoja "muut" false)
                                       :johto-ja-hallintokorvaukset (get tulevaisuudessa-arvoja "jjh" false)
                                       :hoidonjohtopalkkiot (get tulevaisuudessa-arvoja "hoidonjohto" false)
                                       :erillishankinnat (get tulevaisuudessa-arvoja "erillishankinnat" false)
                                       :kilpailutettavat-hankinnat (get tulevaisuudessa-arvoja "hankinnat" false)}
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
                                    :laskutusraja-kaytossa? laskutusraja-kaytossa?
                                    :laskutusraja laskutusraja
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
        (suunnitelma-q/tallenna-kilpailutettavat-hankinnat db kayttaja urakka-id vuosi (:toimenpiteet tiedot)))
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id
                                                     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defn tallenna-erillishankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-erillishankinnat :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-erillishankinnat db kayttaja urakka-id (:erillishankinnat tiedot) vuosi)))
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id
                                                   :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

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
        (suunnitelma-q/tallenna-johto-ja-hallintokorvaukset db kayttaja urakka-id (get tiedot avain) vuosi))
      (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id
                                                     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))))

(defn tallenna-hoidonjohtopalkkiot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "tallenna-hoidonjohtopalkkiot :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?)]
      (doseq [vuosi vuodet]
        (suunnitelma-q/tallenna-hoidonjohtopalkkiot db kayttaja urakka-id (:hoidonjohtopalkkiot tiedot) vuosi)))
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))

(defn vahvista-tai-kumoa-tavoite-ja-kattohinta [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi vahvista? paivitetty-kattohinta] :as tiedot}]
  (log/debug "vahvista-tai-kumoa-tavoite-ja-kattohinta :: tiedot: " tiedot)
  (jdbc/with-db-transaction [db db]
    (let [urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
          urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
          urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          vanha-urakka? (boolean
                          (when urakan-alkuvuosi
                            (< urakan-alkuvuosi 2025)))
          hoitovuosinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
          aiempien-vuosien-pysyvat-muutokset (muutos-palvelu/hae-aiempien-vuosien-pysyvat-muutokset db urakka-id hoitovuoden-alkuvuosi true)
          ;; Päivitä aina urakka_tavoite tauluun tavoitehinta ja kattohinta vahvistuksen yhteydessä
          ;; meni se läpi tai ei. Tämä laskee pysyvät muutokset mukaan 
          _ (when (>= hoitovuoden-alkuvuosi 2025)
              (suunnitelma-q/paivita-tavoite-ja-kattohinta db (:id kayttaja) urakka-id hoitovuoden-alkuvuosi aiempien-vuosien-pysyvat-muutokset))

          ;; Riipumatta vahvistuksen onnistumisesta, aseta kattohinta, jos se on annettu
          _ (when (and paivitetty-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit))
              (suunnitelma-q/paivita-kasin-syotetty-kattohinta db (:id kayttaja) urakka-id hoitovuoden-alkuvuosi
                paivitetty-kattohinta urakan-indeksit
                (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id
                                                               :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
                urakan-parametrit))

          ;; Onko hoitovuoden tarjous tallennettu? Jos ei ole, niin ei voida vahvistaa.
          tarjous (if-not vanha-urakka?
                    (tarjous-kyselyt/hae-tarjousrivit-tietokannasta db urakka-id)
                    (suunnitelma-q/hae-vanhan-urakan-hoitovuoden-tarjous db
                      {:urakka_id urakka-id
                       :hoitokausi hoitovuosinro}))

          hoitovuoden-tarjous (if-not vanha-urakka?
                                (first (filter #(= hoitovuoden-alkuvuosi (:hoitokauden_alkuvuosi %)) tarjous))
                                (-> tarjous first :tarjous_tavoitehinta))
          ;; Äkilliset hoitotyöt | Vahinkojen korjaukset | Tilaajan rahavaraus kannustinjärjestelmään 
          tarjous-kilpailetuttavat (filter #(= "Kilpailutettavat hankinnat" (:nimi %)) (:kustannukset hoitovuoden-tarjous))
          tarjous-kilpailetuttavat-yht (or (apply + (keep #(:summa %) tarjous-kilpailetuttavat)) 0.0)

          tarjous-erillishankinnat (filter #(= "Erillishankinnat" (:nimi %)) (:kustannukset hoitovuoden-tarjous))
          tarjous-erillishankinnat-yht (or (apply + (keep #(:summa %) tarjous-erillishankinnat)) 0.0)

          tarjous-hoidonjohto (filter #(= "Hoidonjohtopalkkio" (:nimi %)) (:kustannukset hoitovuoden-tarjous))
          tarjous-hoidonjohto-yht (or (apply + (keep #(:summa %) tarjous-hoidonjohto)) 0.0)

          tarjous-jjh (filter #(= "johto-ja-hallintokorvaus" (:osio %)) (:toimenkuvat hoitovuoden-tarjous))
          tarjous-jjh-yht (or (apply + (keep #(:summa %) tarjous-jjh)) 0.0)

          ;; Onko indeksit valmiina
          indeksi-olemassa? (boolean (some #(= hoitovuoden-alkuvuosi (:vuosi %)) urakan-indeksit))

          ;; Tarkistetaan, että kilpailutettavat hankinnat, erillishankinnat, hoidonjohtopalkkiot ja johto-ja hallintokorvaukset täsmää tarjouksen kanssa.
          ;; Muuten ei voida vahvistaa tavoitehintaa.
          puuttuvat-suunnitelmat (suunnitelma-q/puuttuvat-suunnitelmat db urakka-id hoitovuoden-alkuvuosi hoitovuoden-tarjous aiempien-vuosien-pysyvat-muutokset)

          suunnitelmat-annettu? (if (empty? puuttuvat-suunnitelmat) true false)

          _ (when (and suunnitelmat-annettu? indeksi-olemassa? hoitovuoden-tarjous)
              (suunnitelma-q/vahvista-tavoite-ja-kattohinta db kayttaja urakka-id vahvista? hoitovuoden-alkuvuosi))

          vastaus (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id
                                                                 :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})

          tarjous-tavoitehinta (if-not vanha-urakka?
                                 (bigdec
                                   (or (:tarjous_tavoitehinta hoitovuoden-tarjous) 0))
                                 (bigdec (or hoitovuoden-tarjous 0)))

          tarjous-puuttuu? (or
                             (not hoitovuoden-tarjous)
                             (<= tarjous-tavoitehinta 0.0M))

          virheet []

          virheet (if-not vanha-urakka?
                    (cond-> virheet
                      (<= tarjous-kilpailetuttavat-yht 0.0)
                      (conj "”Kilpailutettavat hankinnat” -tarjous puuttuu.")

                      (<= tarjous-erillishankinnat-yht 0.0)
                      (conj "”Erillishankinnat” -tarjous puuttuu.")

                      (<= tarjous-hoidonjohto-yht 0.0)
                      (conj "”Hoidonjohtopalkkiot” -tarjous puuttuu.")

                      (<= tarjous-jjh-yht 0.0)
                      (conj "”Johto- ja hallintokorvaukset” -tarjous puuttuu."))
                    virheet)

          virheet (if tarjous-puuttuu?
                    (into virheet
                      (concat
                        [(str "Hoitovuoden " hoitovuoden-alkuvuosi " tarjous puuttuu.")]))

                    (into virheet
                      (concat
                        (when-not indeksi-olemassa?
                          [(str "Hoitovuoden " hoitovuoden-alkuvuosi " indeksikerroin ei ole vielä saatavilla")])

                        (when (seq puuttuvat-suunnitelmat)
                          (map #(str %) puuttuvat-suunnitelmat)))))]

      (if-not (empty? virheet)
        (assoc-in vastaus [:kustannussuunnitelma :vahvistus-virhe] virheet)
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
