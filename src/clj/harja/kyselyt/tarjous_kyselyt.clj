(ns harja.kyselyt.tarjous-kyselyt
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [jeesql.core :refer [defqueries]]
            [harja.tyokalut.yleiset :refer [round2] :as yleiset]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as ks-kyselyt]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.kyselyt.kustannusarvioidut-tyot :as ka-q]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]))

(defqueries "harja/kyselyt/tarjous_kyselyt.sql"
  {:positional? true})

(declare tallenna-tarjous<! paivita-tarjous<!
  tallenna-tarjouskustannus<! paivita-tarjouskustannus<! tallenna-tarjousrahavaraus<! paivita-tarjousrahavaraus<!
  tallenna-tarjouksen-johto-ja-hallintokorvaus<! paivita-tarjouksen-johto-ja-hallintokorvaus<!
  hae-tarjouksen-tiedot hae-tarjous-vuodella
  hae-kustannus-tarjoukselle hae-rahavaraus-tarjoukselle hae-toimenkuva-tarjoukselle poista-tarjouksen-johto-ja-hallintokorvaus<!
  hae-tarjouksen-viimeisin-muokkaaja hae-urakan-tarjous-tavoitehinnat
  lisaa-urakan-tavoite-tarjous<! paivita-rahavaraus-budjettiin<! lisaa-rahavaraus-budjettiin<!
  paivita-urakan-tavoite-ja-kattohinta! lisaa-urakan-tavoite-ja-kattohinta<! hae-laskutusraja-kaytossa)

(def osiojarjestys
  {"hankintakustannukset" 1
   "tavoitehintaiset-rahavaraukset" 2
   "erillishankinnat" 3
   "johto-ja-hallintokorvaus" 4
   "hoidonjohtopalkkio" 5
   "yhteensa" 6})

(defn jasenna-toimenkuvat-maksukausittain
  "Tietokannasta saadaan vain osa toimenkuvista 2019-2024 alkavilla urakoilla. Näillä urakoilla osa toimenkuvista
  on kovakoodattu frontissa. Tästä syystä otetaan tietokannasta saadut toimenkuvat ja lisätään niihin samalla
  toimenkuva-id:llä uusia toimenkuvia ja muutetaan nimi ja maksukausi arvot maksukausittain."
  [toimenkuvat urakan-alkuvuosi]
  (if (<= urakan-alkuvuosi 2021)
    (reduce (fn [uudet-toimenkuvat toimenkuva]
              (let [;; Tässä lisätään puuttuva aiemmin frontin puolella kovakoodattu talvikauden toimenkuva
                    uusi-toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                      {:toimenkuva "päätoiminen apulainen"
                                       :nimi "Päätoiminen apulainen (talvikausi)"
                                       :toimenkuva-id (:id toimenkuva)
                                       :id (:id toimenkuva)
                                       :maksukausi "talvi"}
                                      (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                      {:toimenkuva "apulainen/työnjohtaja"
                                       :nimi "Apulainen/työnjohtaja (talvikausi)"
                                       :toimenkuva-id (:id toimenkuva)
                                       :id (:id toimenkuva)
                                       :maksukausi "talvi"}
                                      :else nil)
                    ;; Tässä muokataan kannasta saatu toimenkuva olemaan kesäkauden toimenkuva
                    toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                 (assoc toimenkuva
                                   :nimi "Päätoiminen apulainen (kesäkausi)"
                                   :toimenkuva-id (:id toimenkuva)
                                   :maksukausi "kesä")
                                 (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                 (assoc toimenkuva
                                   :nimi "Apulainen/työnjohtaja (kesäkausi)"
                                   :toimenkuva-id (:id toimenkuva)
                                   :maksukausi "kesä")
                                 :else (merge toimenkuva {:nimi (str/capitalize (:toimenkuva toimenkuva))
                                                          :maksukausi "vuosi"}))]

                ;; Lisätään talvi/kesäkausi vain, jos ne on noita erikois kovakoodattuja toimenkuvia
                (if uusi-toimenkuva
                  (conj uudet-toimenkuvat uusi-toimenkuva toimenkuva)
                  (conj uudet-toimenkuvat toimenkuva))))
      [] toimenkuvat)
    ;; -25 urakoiden toimenkuvat
    (map (fn [toimenkuva]
           (assoc toimenkuva :nimi (:toimenkuva toimenkuva)))
      toimenkuvat)))

(defn hae-urakan-toimenkuvat
  "Haetaan urakan toimenkuvat urakan alkuvuoden perusteella.
  1. Lisätään niitä tarvittaessa, koska tietokannasta ei löydy vanhoille urakoille kaikkia toimenkuvia (lisäämällä kesä ja talvikausille omat).
  2. Päivitetään toimenkuvalle hoitovuosittaiset arvot frontilla käsittelyä helpottamaan.
  3. Järjestetään toimenkuvat järkevään järjestykseen."
  [db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot]
  (let [toimenkuvat (toimenkuva-kyselyt/hae-urakan-toimenkuvat-alkuvuoden-perusteella db {:urakka-id urakka-id
                                                                                          :urakan-alkuvuosi urakan-alkuvuosi})
        toimenkuvat (jasenna-toimenkuvat-maksukausittain toimenkuvat urakan-alkuvuosi)
        ;; Järjestetään toimenkuvat järkevään järjestykseen
        toimenkuvat (mapv (fn [toimenkuva]
                            (-> toimenkuva
                              (assoc
                                :id (:id toimenkuva)
                                :toimenkuva-id (:id toimenkuva)
                                :jarjestys (toimenkuva-kyselyt/paattele-toimenkuvan-jarjestys (:toimenkuva toimenkuva))
                                :toimenkuva (:toimenkuva toimenkuva)
                                :maksukausi (or (:maksukausi toimenkuva) "vuosi")
                                :nimi (str/capitalize (:nimi toimenkuva))
                                :toimenkuva-id (:id toimenkuva)
                                :osio "johto-ja-hallintokorvaus"
                                ;; Poistetaan hoitovuosittaiset arvot kovakoodatusti 'Valmistelukausi ennen urakka-ajan alkua' -toimenkuvalta
                                :hoitovuosittaiset-arvot (if (= "valmistelukausi ennen urakka-ajan alkua" (:toimenkuva toimenkuva))
                                                           [(first hoitovuosittaiset-arvot)] hoitovuosittaiset-arvot))))
                      toimenkuvat)
        toimenkuvat (sort-by :jarjestys toimenkuvat)]
    toimenkuvat))

(defn luo-default-tarjous [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        ;; Lisätään default tarjoukseen Kilpailutettavat hankinnat, Erillishankinnat ja Hoidonjohtopalkkio
        tarjousrivit [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil :jarjestys 1
                       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot :yhteensa 0.00}
                      {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id (:id tehtavaryhma) :rahavaraus-id nil
                       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                      {:nimi "Hoidonjohtopalkkio" :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id (:id tehtava) :tehtavaryhma-id nil :rahavaraus-id nil
                       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                      {:nimi "Tarjouksen tavoitehinta"
                       :osio "yhteensa"
                       :yhteensa 0.00
                       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot}
                      {:nimi (if (:muokkaa_kattohinta_kasin urakan-parametrit) "Tarjouksen kattohinta"
                               (str "Tarjouksen kattohinta (" (fmt/desimaaliluku (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit) nil nil false) " x tarjouksen tavoitehinta)"))
                       :osio "yhteensa"
                       :yhteensa 0.00
                       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot}]

        ; haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        rahavaraus-rivit (reduce (fn [lopulliset rahavaraus]
                                   (vec (concat lopulliset [{:nimi (:nimi rahavaraus),
                                                             :osio "tavoitehintaiset-rahavaraukset"
                                                             :jarjestys (:jarjestys rahavaraus)
                                                             :toimenkuva-id nil
                                                             :tehtava-id nil
                                                             :tehtavaryhma-id nil
                                                             :rahavaraus-id (:id rahavaraus)
                                                             :hoitovuosittaiset-arvot hoitovuosittaiset-arvot
                                                             :yhteensa 0.00}])))
                           [] rahavaraukset)
        ;; Järjestetään rahavaraukset kilpailutettavien hankintojen jälkeen
        rahavaraus-rivit (sort-by :jarjestys rahavaraus-rivit)
        rahavaraus-rivit (map-indexed (fn [indeksi rivi] (assoc rivi :jarjestys (+ 1 (inc indeksi)))) rahavaraus-rivit)
        ;; Yhdistetään tarjous ja rahavaraukset
        tarjousrivit (vec (concat tarjousrivit rahavaraus-rivit))
        toimenkuvat (hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)
        tarjousrivit (vec (concat tarjousrivit toimenkuvat))
        jarjestetyt-tarjousrivit (sort-by (fn [rivi] (get osiojarjestys (:osio rivi)))
                                   tarjousrivit)]

    jarjestetyt-tarjousrivit))

(defn vuodet-tietomallista [malli]
  (reduce (fn [rivit vuosi-rivi]
            (concat rivit [{:vuosi (:vuosi vuosi-rivi)}]))
    [] (:hoitovuosittaiset-arvot (first (:tarjous malli)))))

(defn tarjoustietomallista-vuosittaiset-hinnat [tarjous-tietomalli vuosi]
  (apply + (mapv
             (fn [tarjous-rivi]
               (let [hoitovuoden-arvot (:hoitovuosittaiset-arvot tarjous-rivi)
                     vuosittaiset-arvot (apply + (mapv
                                                   (fn [vuosisumma]
                                                     (if (= vuosi (:vuosi vuosisumma))
                                                       (or (:summa vuosisumma) 0) 0))
                                                   hoitovuoden-arvot))]
                 vuosittaiset-arvot))
             (filter #(not= "yhteensa" (:osio %)) (:tarjous tarjous-tietomalli)))))

(defn tallenna-tarjouksen-kustannukset [db vuositarjous tietokantatarjous kustannuksetlistaus tarjousdb urakka-id kayttaja-id]
  (let [vuosittaiset-kustannukset (filter #(= (:hoitokauden_alkuvuosi vuositarjous) (:hoitokauden_alkuvuosi %)) kustannuksetlistaus)
        _ (mapv (fn [kustannus]
                  (let [;; Varmistetaan, että käyttäjä antoi summan ennen tallennusta
                        kustannus (if (nil? (:summa kustannus)) (assoc kustannus :summa 0.00M) kustannus)
                        ; tarkistetaan, että löytyykö jo tietokannasta
                        kustannusdb (if (:id tarjousdb)
                                      (first (hae-kustannus-tarjoukselle db {:tarjous_id (:id tarjousdb)
                                                                             :urakka_id urakka-id
                                                                             :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi kustannus)
                                                                             :tehtavaryhma_id (:tehtavaryhma_id kustannus)
                                                                             :tehtava_id (:tehtava_id kustannus)
                                                                             :osio (:osio kustannus)}))
                                      nil)]
                    (if kustannusdb
                      (paivita-tarjouskustannus<! db (assoc kustannusdb :summa (:summa kustannus)
                                                       :muokkaaja kayttaja-id))
                      (tallenna-tarjouskustannus<! db (assoc kustannus :tarjous_id (:id tietokantatarjous))))))
            vuosittaiset-kustannukset)]))

(defn tallenna-tarjouksen-rahavaraukset [db vuositarjous tietokantatarjous rahavarauslistaus tarjousdb urakka-id kayttaja-id]
  (let [vuosittaiset-rahavaraukset (filter #(= (:hoitokauden_alkuvuosi vuositarjous) (:hoitokauden_alkuvuosi %)) rahavarauslistaus)
        _ (mapv (fn [rahavaraus]
                  (let [;; Varmistetaan, että käyttäjä antoi summan ennen tallennusta
                        rahavaraus (if (nil? (:summa rahavaraus)) (assoc rahavaraus :summa 0.00M) rahavaraus)
                        ; tarkistetaan, että löytyykö jo tietokannasta
                        rahavaraus-tarjous-db (if (:id tarjousdb)
                                                (first (hae-rahavaraus-tarjoukselle db {:tarjous_id (:id tarjousdb)
                                                                                        :urakka_id urakka-id
                                                                                        :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi rahavaraus)
                                                                                        :rahavaraus_id (:rahavaraus_id rahavaraus)
                                                                                        :osio (:osio rahavaraus)}))
                                                nil)
                        _ (if rahavaraus-tarjous-db
                            (paivita-tarjousrahavaraus<! db (assoc rahavaraus-tarjous-db :summa (:summa rahavaraus)
                                                              :muokkaaja kayttaja-id))
                            (tallenna-tarjousrahavaraus<! db (assoc rahavaraus :tarjous_id (:id tietokantatarjous))))]))
            vuosittaiset-rahavaraukset)]))

(defn tallanna-rahavaraukset-kustannussuuunnitelmaan
  "Rahavaraukset tallennetaan sekä tarjoukseen että kustannussuunnitelmaan."
  [db vuositarjous urakka-id sopimus-id urakan-indeksit kuluva-hoitovuosi-nro rahavaraukset-tarjouksesta kayttaja-id]
  (let [;; Tallenna rahavaraukset myös kustannusarvioitu_tyo tauluun
        _ (mapv (fn [rahavaraus]
                  (let [rahavaraus-id (:rahavaraus-id rahavaraus)
                        vuosittainen-summa (:summa (first (filter #(= (:hoitokauden_alkuvuosi vuositarjous) (:vuosi %)) (:hoitovuosittaiset-arvot rahavaraus))))

                        ;; Jokaisella kustannusarvoitu_tyo -rivillä pitää olla toimenpideinstanssi.
                        ;; Rahavaraukset eivät kuulu millekään tällä hetkellä tiedetylle toimenpideinstanssille.
                        ;; Mutta yksinkertaisuuden vuoksi toimenpideinstanssin pakollisuutta ei lähdetty muuttamaan, vaan laitetaan
                        ;; Rahavaraukselle vain jokin toimenpideinstanssi. Sen olemassaolo filtteröidään muualla pois.
                        ensimmainen-toimenpideinstanssi-id (:id (first (rahavaraus-kyselyt/hae-rahavarauksen-toimenpideinstanssi db {:urakka_id urakka-id})))

                        ;; Päivitetään rahavarauksen summa ja indeksikorjattu summa kustannusarvioitu_työ tauluun
                        kt-rahavaraus-kuukaudet (ka-q/hae-rahavarauskustannus db {:rahavaraus_id rahavaraus-id
                                                                                  :vuosi (:hoitokauden_alkuvuosi vuositarjous)
                                                                                  :sopimus_id sopimus-id})

                        db-budjetoitu-rahavaraus (if (seq kt-rahavaraus-kuukaudet)
                                                   (let [kk (atom 0)] ;; Lokaalisti voi olla vaikka vain kolmena kuukautena summa, vaikka pitäisi olla 12
                                                     (doseq [r kt-rahavaraus-kuukaudet
                                                             :let [_ (swap! kk inc)
                                                                   kuukausimaara (count kt-rahavaraus-kuukaudet)
                                                                   kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa kuukausimaara))) ;; Tallenna nil kantaan, jos nil arvo on syötetty
                                                                   viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* (dec kuukausimaara) kuukausisumma))))
                                                                   summa (if (and (>= kuukausimaara 9) (= @kk 9)) viimeinen-kuukausisumma kuukausisumma)]]
                                                       ;; Rahavarauksesta ei voi muuttua, kuin summa
                                                       (paivita-rahavaraus-budjettiin<! db {:summa summa
                                                                                            :summa_indeksikorjattu (when summa
                                                                                                                     (indeksi-kyselyt/indeksikorjaa (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro) summa))
                                                                                            :muokattu (pvm/nyt)
                                                                                            :muokkaaja kayttaja-id
                                                                                            :id (:id r)})))
                                                   (doseq [kk (range 1 13)
                                                           :let [kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (/ vuosittainen-summa 12)))
                                                                 viimeinen-kuukausisumma (when-not (nil? vuosittainen-summa) (round2 2 (- vuosittainen-summa (* 11 kuukausisumma))))
                                                                 vuosi (if (< kk 10) (inc (:hoitokauden_alkuvuosi vuositarjous)) (:hoitokauden_alkuvuosi vuositarjous))
                                                                 summa (if (= kk 9) viimeinen-kuukausisumma kuukausisumma)]]
                                                     (lisaa-rahavaraus-budjettiin<! db {:vuosi vuosi
                                                                                        :kuukausi kk
                                                                                        :sopimus_id sopimus-id
                                                                                        :toimenpideinstanssi_id ensimmainen-toimenpideinstanssi-id
                                                                                        :tehtava_id nil
                                                                                        :rahavaraus_id rahavaraus-id
                                                                                        :summa summa
                                                                                        :summa_indeksikorjattu (when summa
                                                                                                                 (indeksi-kyselyt/indeksikorjaa (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro) summa))
                                                                                        :luoja kayttaja-id})))]
                    db-budjetoitu-rahavaraus))
            rahavaraukset-tarjouksesta)]))

(defn tallenna-tarjouksen-toimenkuvat [db vuositarjous tietokantatarjous toimenkuvatlistaus tarjousdb urakka-id kayttaja-id]
  (let [vuosittaiset-toimenkuvat (filter #(= (:hoitokauden_alkuvuosi vuositarjous) (:hoitokauden_alkuvuosi %)) toimenkuvatlistaus)
        ;; Loopataan vuosittaiset toimenkuvat ja lisätään tarvittaessa uudet ja päivitetään olemassaolevat
        _ (mapv
            (fn [toimenkuva]
              (let [uusi-db-toimenkuva (when (= -1 (:id toimenkuva))
                                         ;; Tämä map pyörähtää jokaisena hoitovuonna. Mutta toimenkuvan kannalta riittää
                                         ;; että toimenkuvia lisätään vain kerran urakalle
                                         ;; Tarkistetaan siis, ettei toimenkuvaa löydy jo tietokannasta
                                         (let [toimenkuva-kannasta (first (toimenkuva-kyselyt/hae-urakan-toimenkuva db {:toimenkuva (:toimenkuva toimenkuva)
                                                                                                                        :urakkaid urakka-id}))
                                               palautettava-toimenkuva (if (nil? toimenkuva-kannasta)
                                                                         (toimenkuva-kyselyt/lisaa-urakan-toimenkuva<! db {:toimenkuva (:toimenkuva toimenkuva)
                                                                                                                           :urakkaid urakka-id
                                                                                                                           :urakkakohtainen-nimi (:nimi toimenkuva)})
                                                                         toimenkuva-kannasta)]
                                           palautettava-toimenkuva))
                    toimenkuvadb (if (:id tarjousdb)
                                   (first (hae-toimenkuva-tarjoukselle db
                                            {:tarjous_id (:id tarjousdb)
                                             :maksukausi (:maksukausi toimenkuva)
                                             :urakka_id urakka-id
                                             :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi toimenkuva)
                                             :johto_ja_hallintokorvaus_toimenkuva_id (or (:id uusi-db-toimenkuva) (:johto_ja_hallintokorvaus_toimenkuva_id toimenkuva))
                                             :osio (:osio toimenkuva)}))
                                   nil)]
                (if toimenkuvadb
                  (paivita-tarjouksen-johto-ja-hallintokorvaus<! db (assoc toimenkuvadb
                                                                      :summa (:summa toimenkuva)
                                                                      :muokkaaja kayttaja-id))
                  (let [toimenkuva (if uusi-db-toimenkuva
                                     (assoc toimenkuva :johto_ja_hallintokorvaus_toimenkuva_id (:id uusi-db-toimenkuva))
                                     toimenkuva)]

                    (tallenna-tarjouksen-johto-ja-hallintokorvaus<! db (assoc toimenkuva
                                                                         :tarjous_id (:id tietokantatarjous)))))))
            vuosittaiset-toimenkuvat)]))

(defn tallenna-tarjous-tietokantaan
  "Tarjous koostuu kolmesta kokonaisuudesta: Tarjouksen kokonaissummasta eli Tavoite- ja Kattohinnasta, Johto-ja-hallintokorvauksista (toimenkuvat) sekä
  Hankinnoista (Kilpailutettavat hankinnat, Erillishankinnat, Rahavarauksista, Hoidonjohtopalkkiosta).
  Kustannusten suunnittelussa rahavaraukset on irroitettu tallennusprosessista, koska niitä ei voi enää muokata tarjouksen tallennuksen jälkeen.
  Tästä syystä tarjouksen tallentamisen yhteydessä rahavaraukset tallennetaan kustannusarvioitu_tyo tauluun. Ikäänkuin ne olisi Kustannussuunnitelmassa jo suunniteltu."
  [db urakka-id kayttaja-id tarjous vahvistetut-vuodet]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        sopimus-id (urakat-kyselyt/urakan-paasopimus-id db urakka-id)
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        vuodet (map (fn [vuosi]
                      {:vuosi vuosi}) (range (pvm/vuosi (:alkupvm urakan-tiedot)) (pvm/vuosi (:loppupvm urakan-tiedot))))
        sallitut-vuodet (filter
                          #(not (contains? vahvistetut-vuodet (:vuosi %)))
                          vuodet)

        ;; Ennenkuin tallennetaan mitään, varmistetaan, että annettu kattohinta ei ole pienempi, kuin tarjouksen tavoihinta
        kasin-syotetty-kattohinta-rivi (first (filter #(= "Tarjouksen kattohinta" (:nimi %)) (:tarjous tarjous)))
        kattohinnat-valideja (if kasin-syotetty-kattohinta-rivi
                               (mapv
                                 (fn [vuosi-rivi]
                                   (let [tavoitehinta (tarjoustietomallista-vuosittaiset-hinnat tarjous (:vuosi vuosi-rivi))
                                         vuosittainen-kattohinta (:summa (first (filter (fn [arvot]
                                                                                          (when (= (:vuosi vuosi-rivi) (:vuosi arvot))
                                                                                            arvot))
                                                                                  (:hoitovuosittaiset-arvot kasin-syotetty-kattohinta-rivi))))
                                         kattohinta-valid? (> vuosittainen-kattohinta tavoitehinta)]
                                     kattohinta-valid?))
                                 sallitut-vuodet)
                               [true])

        _ (when-not (every? true? kattohinnat-valideja)
            (throw (IllegalArgumentException. (str "Tarjoukselle annettu kattohinta ei ole validi. Kattohinnan tulee olla vähintään yhtä suuri kuin tarjouksen tavoitehinta."))))

        ;; Haetaan urakan mahdolliset aiemmat tarjoushinnat urakka_tavoite -taulusta
        urakan-tavoitteet-tietokannasta (hae-urakan-tarjous-tavoitehinnat db {:urakkaid urakka-id})

        ;; Muokkaa tietomallin vuosittaiset summat tarjous- ja kattohinnaksi
        vuosittaiset-tarjoushinnat (mapv
                                     (fn [vuosi-rivi]
                                       (let [summa (tarjoustietomallista-vuosittaiset-hinnat tarjous (:vuosi vuosi-rivi))
                                             kattohinta-rivi (first (filter #(= "Tarjouksen kattohinta" (:nimi %)) (:tarjous tarjous)))
                                             kasin-syotetty-kattohinta (apply + (mapv
                                                                                  (fn [vuosisumma]
                                                                                    (if (= (:vuosi vuosi-rivi) (:vuosi vuosisumma))
                                                                                      (or (:summa vuosisumma) 0) 0))
                                                                                  (:hoitovuosittaiset-arvot kattohinta-rivi)))
                                             kattohinta (if (and (nil? kattohintakerroin) (:muokkaa_kattohinta_kasin urakan-parametrit))
                                                          kasin-syotetty-kattohinta
                                                          (* summa kattohintakerroin))]
                                         {:hoitokauden_alkuvuosi (:vuosi vuosi-rivi)
                                          :tarjous_tavoitehinta summa
                                          :urakka_id urakka-id,
                                          :tarjous_kattohinta kattohinta
                                          :luoja kayttaja-id}))
                                     sallitut-vuodet)
        ;; Erota toimenkuvat ja muut kustannukset toisistaan
        hankintaosiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio"}
        rahavarausosiot #{"tavoitehintaiset-rahavaraukset"}
        johto-ja-hallintokorvausosiot #{"johto-ja-hallintokorvaus"}
        kustannukset-tarjouksesta (filter #(contains? hankintaosiot (:osio %)) (:tarjous tarjous))
        kustannuksetlistaus (flatten (reduce
                                       (fn [kaikki rivi]
                                         (let [uudet-rivit (mapv
                                                             (fn [r]
                                                               {:urakka_id urakka-id
                                                                :hoitokauden_alkuvuosi (:vuosi r)
                                                                :tehtava_id (:tehtava-id rivi)
                                                                :tehtavaryhma_id (:tehtavaryhma-id rivi)
                                                                :rahavaraus_id (:rahavaraus-id rivi)
                                                                :summa (:summa r)
                                                                :osio (:osio rivi)
                                                                :luoja kayttaja-id})
                                                             (:hoitovuosittaiset-arvot rivi))]
                                           (conj kaikki uudet-rivit)))
                                       [] kustannukset-tarjouksesta))
        rahavaraukset-tarjouksesta (filter #(contains? rahavarausosiot (:osio %)) (:tarjous tarjous))

        rahavarauslistaus (flatten (reduce
                                     (fn [kaikki rivi]
                                       (let [uudet-rivit (mapv
                                                           (fn [r]
                                                             {:urakka_id urakka-id
                                                              :hoitokauden_alkuvuosi (:vuosi r)
                                                              :rahavaraus_id (:rahavaraus-id rivi)
                                                              :summa (:summa r)
                                                              :osio (:osio rivi)
                                                              :luoja kayttaja-id})
                                                           (:hoitovuosittaiset-arvot rivi))]
                                         (conj kaikki uudet-rivit)))
                                     [] rahavaraukset-tarjouksesta))

        ;; Poista nimettömät toimenkuvat. Jos nimi on tyhjä, käyttäjä on poistanut toimenkuvan, vaikka ei olisikaan painanut "poista" nappia
        toimenkuvat-tarjouksesta (filter #(contains? johto-ja-hallintokorvausosiot (:osio %)) (:tarjous tarjous))
        toimenkuvat-tarjouksesta (remove #(= "" (str/trim (:nimi %))) toimenkuvat-tarjouksesta)

        ;; Vaihdetut toimenkuvat jättää jälkensä :uusi-nimi arvoon. Haetaan sen nimen perusteella toimenkuvan id
        toimenkuvat-tarjouksesta (mapv
                                   (fn [rivi]
                                     (if (and (seq (:uusi-nimi rivi))
                                           (not= (:uusi-nimi rivi) (:nimi rivi)))
                                       (let [uusi-toimenkuva (first (toimenkuva-kyselyt/hae-toimenkuvat db {:nimi (:uusi-nimi rivi)}))]
                                         (assoc rivi
                                           :vanha-id (:toimenkuva-id rivi)
                                           :toimenkuva-id (:id uusi-toimenkuva)
                                           :nimi (:nimi uusi-toimenkuva)))
                                       rivi))
                                   toimenkuvat-tarjouksesta)

        ;; Poistettavat toimenkuvat - Poistetaan myös vaihtuneet toimenkuvat, koska ne on korvattu uusilla
        poistettavat-toimenkuvat (filter #(or (true? (:poistettu %)) (not (nil? (:vanha-id %)))) toimenkuvat-tarjouksesta)
        ;; Poistetaan toimenkuvat tietokannasta
        _ (mapv
            (fn [poistettava]
              (let [;; Poista toimenkuva tarjoukselta
                    _ (poista-tarjouksen-johto-ja-hallintokorvaus<! db {:urakkaid urakka-id
                                                                        :toimenkuvaid (or (:vanha-id poistettava)
                                                                                        (:toimenkuva-id poistettava))})
                    ;; Poista toimenkuva urakalta
                    _ (toimenkuva-kyselyt/poista-toimenkuva! db (:toimenkuva-id poistettava))]))
            poistettavat-toimenkuvat)

        ;; Päivitetään vain halutut toimenkuvat normaaliprosessilla
        paivitettavat-toimenkuvat (remove #(true? (:poistettu %)) toimenkuvat-tarjouksesta)
        ;; Muutetaan toimenkuvat listaksi, jossa on yksi rivi per hoitovuosi
        toimenkuvatlistaus (flatten (reduce
                                      (fn [kaikki rivi]
                                        (let [uudet-rivit (mapv
                                                            (fn [r]
                                                              {:id (:id rivi)
                                                               :nimi (:nimi rivi)
                                                               :toimenkuva (:toimenkuva rivi)
                                                               :maksukausi (:maksukausi rivi)
                                                               :urakka_id urakka-id
                                                               :hoitokauden_alkuvuosi (:vuosi r)
                                                               :johto_ja_hallintokorvaus_toimenkuva_id (:toimenkuva-id rivi)
                                                               :summa (or (:summa r) 0)
                                                               :osio (:osio rivi)
                                                               :luoja kayttaja-id})
                                                            (:hoitovuosittaiset-arvot rivi))]
                                          (conj kaikki uudet-rivit)))
                                      [] paivitettavat-toimenkuvat))

        ;; Tallennetaan tarjous- ja kattohinnat tarjouksen päätauluun, johon muut tiedot linkitetään
        tallennukset (mapv
                       (fn [rivi]
                         (let [kuluva-hoitovuosi-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (:hoitokauden_alkuvuosi rivi))
                               ;; Etsi vuodelle ja urakalle tarjousta
                               tarjousdb (first (hae-tarjous-vuodella db {:vuosi (:hoitokauden_alkuvuosi rivi)
                                                                          :urakka_id urakka-id}))
                               tietokantatarjous (if tarjousdb
                                                   (paivita-tarjous<! db (assoc rivi
                                                                           :muokkaaja kayttaja-id
                                                                           :id (:id tarjousdb)))
                                                   (tallenna-tarjous<! db rivi))
                               ;; Päivitetään tarjouksen tiedot myös urakka_tavoite -tauluun, jota muut Harjan osa-alueet käyttävät
                               urakka-tavoite-db (first (filter #(= kuluva-hoitovuosi-nro (:hoitovuosinro %)) urakan-tavoitteet-tietokannasta))
                               tavoitehinta (:tarjous_tavoitehinta rivi)
                               tavoitehinta_indeksikorjattu (indeksi-kyselyt/indeksikorjaa
                                                              (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro) tavoitehinta)
                               ;; Haetaan aiempien vuosien pysyvät muutokset ja lisätään ne laskutusrajaan jo ennen tavoite- ja kattohinnan vahvistusta
                               aiempien-vuosien-pysyvat-muutokset (muutos-palvelu/hae-aiempien-vuosien-pysyvat-muutokset db urakka-id (:hoitokauden_alkuvuosi rivi) true)
                               pysyvat-muutokset-maara (reduce + (map :tavoitehinnan-muutos aiempien-vuosien-pysyvat-muutokset))
                               hoitovuoden-alun-tavoitehinta (+ (or tavoitehinta 0M) (or pysyvat-muutokset-maara 0M))
                               laskutusraja-ennen-vahvistusta (indeksi-kyselyt/indeksikorjaa
                                                                (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro)
                                                                hoitovuoden-alun-tavoitehinta)
                               laskutusraja-kaytossa? (-> (hae-laskutusraja-kaytossa db {:urakka-id urakka-id})
                                                        first
                                                        :laskutusraja-kaytossa)
                               _ (if urakka-tavoite-db
                                   (paivita-urakan-tavoite-ja-kattohinta! db {:urakka-id urakka-id
                                                                              :hoitokausinumero kuluva-hoitovuosi-nro
                                                                              :tarjous_tavoitehinta tavoitehinta
                                                                              :tavoitehinta tavoitehinta
                                                                              :tavoitehinta_indeksikorjattu tavoitehinta_indeksikorjattu
                                                                              :laskutusraja (when laskutusraja-kaytossa? laskutusraja-ennen-vahvistusta)
                                                                              :laskutusraja_alkuperainen (when laskutusraja-kaytossa? laskutusraja-ennen-vahvistusta)
                                                                              :kattohinta (* (or kattohintakerroin 0) tavoitehinta)
                                                                              :kattohinta_indeksikorjattu (indeksi-kyselyt/indeksikorjaa
                                                                                                            (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro)
                                                                                                            (* (or kattohintakerroin 0) tavoitehinta))
                                                                              :muokkaaja kayttaja-id})
                                   ;; Ei lisätä 0 arvoja ollenkaan.
                                   (when-not (zero? tavoitehinta)
                                     (lisaa-urakan-tavoite-ja-kattohinta<! db {:urakka-id urakka-id
                                                                               :hoitokausinumero kuluva-hoitovuosi-nro
                                                                               :tarjous_tavoitehinta tavoitehinta
                                                                               :tavoitehinta tavoitehinta
                                                                               :tavoitehinta_indeksikorjattu tavoitehinta_indeksikorjattu
                                                                               :laskutusraja (when laskutusraja-kaytossa? laskutusraja-ennen-vahvistusta)
                                                                               :laskutusraja_alkuperainen (when laskutusraja-kaytossa? laskutusraja-ennen-vahvistusta)
                                                                               :kattohinta (* (or kattohintakerroin 0) tavoitehinta)
                                                                               :kattohinta_indeksikorjattu (indeksi-kyselyt/indeksikorjaa
                                                                                                             (indeksi-kyselyt/indeksikerroin urakan-indeksit kuluva-hoitovuosi-nro)
                                                                                                             (* (or kattohintakerroin 0) tavoitehinta))
                                                                               :luoja kayttaja-id})))

                               ;; Tallennetaan tarjouksen kustannukset, toimenkuvat ja rahavaraukset tietokantaan
                               _ (tallenna-tarjouksen-kustannukset db rivi tietokantatarjous kustannuksetlistaus tarjousdb urakka-id kayttaja-id)
                               _ (tallenna-tarjouksen-rahavaraukset db rivi tietokantatarjous rahavarauslistaus tarjousdb urakka-id kayttaja-id)
                               ;; Rahavaraukset tallennetaan tarjouksen lisäksi myös kustannusarvioitu_tyo tauluun
                               _ (tallanna-rahavaraukset-kustannussuuunnitelmaan db rivi urakka-id sopimus-id urakan-indeksit kuluva-hoitovuosi-nro rahavaraukset-tarjouksesta kayttaja-id)
                               _ (tallenna-tarjouksen-toimenkuvat db rivi tietokantatarjous toimenkuvatlistaus tarjousdb urakka-id kayttaja-id)
                               
                               _ (ks-kyselyt/paivita-tavoite-ja-kattohinta db kayttaja-id urakka-id (:hoitokauden_alkuvuosi rivi) aiempien-vuosien-pysyvat-muutokset)]
                           {:tarjousid (:id tietokantatarjous)}))
                       vuosittaiset-tarjoushinnat)]
    tallennukset))

(defn hae-kustannuksista-rivit-vuodelle [avain tarjous-rivit nimi]
  (let [;; Etsitään kustannus, joka vastaa annettua nimeä
        rivit (into [] (sort-by :vuosi (reduce (fn [r rivi]
                                                 (let [r-rivit (keep #(when (= nimi (:nimi %))
                                                                        (dissoc (merge % {:vuosi (:hoitokauden_alkuvuosi rivi)})
                                                                          :id :nimi :maksukausi :osio :tehtava_id :tehtavaryhma_id :rahavaraus_id
                                                                          :johto_ja_hallintokorvaus_toimenkuva_id :r_jarjestys))
                                                                 (avain rivi))]
                                                   (vec (concat r r-rivit))))
                                         [] tarjous-rivit)))]
    rivit))

(defn hae-toimenkuvista-rivit-vuodelle [avain tarjous-rivit id maksukausi]
  (let [;; Etsitään toimenkuva, joka vastaa annettua nimeä ja maksukautta
        rivit (into [] (sort-by :vuosi (reduce (fn [r rivi]
                                                 (let [r-rivit (keep #(when (and (= id (:johto_ja_hallintokorvaus_toimenkuva_id %)) (= maksukausi (:maksukausi %)))
                                                                        (dissoc (merge % {:vuosi (:hoitokauden_alkuvuosi rivi)})
                                                                          :id :nimi :maksukausi :osio :tehtava_id :tehtavaryhma_id :rahavaraus_id
                                                                          :johto_ja_hallintokorvaus_toimenkuva_id))
                                                                 (avain rivi))]
                                                   (vec (concat r r-rivit))))
                                         [] tarjous-rivit)))]
    rivit))

(defn- muodosta-kustannusrivi [r hoitovuosittaiset-arvot]
  (-> {:osio (:osio r)
       :nimi (:nimi r)
       :toimenkuva-id (:johto_ja_hallintokorvaus_toimenkuva_id r)
       :tehtava-id (:tehtava_id r)
       :tehtavaryhma-id (:tehtavaryhma_id r)
       :jarjestys (:jarjestys r)
       :rahavaraus-id (:rahavaraus_id r)
       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot
       :yhteensa (apply + (mapv :summa hoitovuosittaiset-arvot))}))

(defn- muodosta-toimenkuvarivi [r hoitovuosittaiset-arvot]
  (-> {:id (:id r)
       :osio (:osio r)
       :maksukausi (:maksukausi r)
       :nimi (:nimi r)
       :toimenkuva (str/lower-case (:nimi r))
       :toimenkuva-id (:johto_ja_hallintokorvaus_toimenkuva_id r)
       :hoitovuosittaiset-arvot hoitovuosittaiset-arvot
       :yhteensa (apply + (mapv :summa hoitovuosittaiset-arvot))}))

(defn lisaa-yhteenvetorivi-tarjoukseen
  "Vanhoille 2019 ja 2020 urakoille lisätään vielä kattohintarivi, koska ne voivat muokata niitä käsin."
  [tarjous]
  (let [;; Lisätään vielä loppuun yhteenvetorivi, joka on viimeisenä
        ;; Vuodet ovat dynaamisia. Päätellään ne tietomallista
        vuodet (vuodet-tietomallista tarjous)
        yhteenvetorivit (reduce (fn [yhteenveto vuosi]
                                  (let [summa (tarjoustietomallista-vuosittaiset-hinnat tarjous (:vuosi vuosi))
                                        hoitovuosittaiset-arvot {:vuosi (:vuosi vuosi)
                                                                 :summa summa}]
                                    {:nimi "Tarjouksen tavoitehinta"
                                     :osio "yhteensa"
                                     :yhteensa (+ (or (:yhteensa yhteenveto) 0) summa)
                                     :hoitovuosittaiset-arvot (into [] (sort-by :vuosi (conj (:hoitovuosittaiset-arvot yhteenveto) hoitovuosittaiset-arvot)))}))
                          {} vuodet)]
    ; Lisätään yhteenvetorivi tarjoukseen
    (update tarjous :tarjous #(vec (concat % [yhteenvetorivit])))))

(defn hae-tarjousrivit-tietokannasta [db urakka-id]
  (let [tarjous-rivit (hae-tarjouksen-tiedot db {:urakka_id urakka-id})
        ;; Mäppää tarjouksen tietokantarivit clojure-mapeiksi.
        tarjous-rivit (mapv
                        (fn [tarjous]
                          (-> tarjous
                            (assoc :kustannukset
                              (if (:kustannukset tarjous)
                                (mapv
                                  (fn [k]
                                    (konversio/pgobject->map k :id :long :nimi :string :r_jarjestys :long :summa :double :osio :string :tehtava_id :long :tehtavaryhma_id :long :rahavaraus_id :long))
                                  (konversio/pgarray->vector (:kustannukset tarjous)))
                                []))
                            (assoc :toimenkuvat
                              (mapv
                                (fn [k]
                                  (konversio/pgobject->map k :id :long :nimi :string :summa :double
                                    :maksukausi :string :osio :string :johto_ja_hallintokorvaus_toimenkuva_id :long))
                                (konversio/pgarray->vector (:toimenkuvat tarjous))))))
                        tarjous-rivit)]
    tarjous-rivit))

(defn hae-tarjous [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (map (fn [vuosi]
                      {:vuosi vuosi}) (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi (:vuosi vuosi) :summa 0.00M}) vuodet)
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        muokkaa-kattohinta-kasin (:muokkaa_kattohinta_kasin urakan-parametrit)

        ;; Tarjouksen viimeisin muokkaaja
        viimeisin-muokkaus (first (hae-tarjouksen-viimeisin-muokkaaja db {:urakkaid urakka-id}))
        tarjous-rivit (hae-tarjousrivit-tietokannasta db urakka-id)
        yhteenveto-tavoitehinta-rivi {:nimi "Tarjouksen tavoitehinta"
                                      :osio "yhteensa"
                                      :yhteensa (apply + (map :tarjous_tavoitehinta tarjous-rivit))
                                      :hoitovuosittaiset-arvot (map
                                                                 (fn [rivi] {:vuosi (:hoitokauden_alkuvuosi rivi)
                                                                             :summa (:tarjous_tavoitehinta rivi)})
                                                                 tarjous-rivit)}
        yhteenveto-kattohinta-rivi {:nimi (if muokkaa-kattohinta-kasin "Tarjouksen kattohinta"
                                            (str "Tarjouksen kattohinta (" (fmt/desimaaliluku kattohintakerroin nil nil false) " x tarjouksen tavoitehinta)"))
                                    :osio "yhteensa"
                                    :yhteensa (apply + (map :tarjous_kattohinta tarjous-rivit))
                                    :hoitovuosittaiset-arvot (map
                                                               (fn [rivi] {:vuosi (:hoitokauden_alkuvuosi rivi)
                                                                           :summa (:tarjous_kattohinta rivi)})
                                                               tarjous-rivit)}

        kustannus-rivit (sort-by :r_jarjestys (:kustannukset (first tarjous-rivit)))
        kustannus-rivit (mapv (fn [rivi]
                                (if (= "tavoitehintaiset-rahavaraukset" (:osio rivi))
                                  (-> rivi
                                    (assoc :jarjestys (:r_jarjestys rivi))
                                    (dissoc :r_jarjestys))
                                  (dissoc rivi :r_jarjestys)))
                          kustannus-rivit)
        ;; Jos urakalle on lisätty rahavarauksia alkuperäisen tarjouksen tallentamisen jälkeen, niin niitä ei löydy tarjouksen tiedoista. Joten lisätään ne näin jälkikäteen
        kaikki-urakan-rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        puuttuvat-rahavaraukset (filter
                                  (fn [r]
                                    (not (some #(= (:id r) (:rahavaraus_id %)) kustannus-rivit)))
                                  kaikki-urakan-rahavaraukset)
        puuttuvat-rahavaraukset (reduce (fn [lopulliset rahavaraus]
                                          (vec (concat lopulliset [{:nimi (:nimi rahavaraus),
                                                                    :summa 0
                                                                    :osio "tavoitehintaiset-rahavaraukset"
                                                                    :tehtava_id nil
                                                                    :tehtavaryhma_id nil
                                                                    :rahavaraus_id (:id rahavaraus)
                                                                    :jarjestys (:jarjestys rahavaraus)}])))
                                  [] puuttuvat-rahavaraukset)
        kustannus-rivit (vec (concat kustannus-rivit puuttuvat-rahavaraukset))
        kustannus-rivit (sort-by :jarjestys kustannus-rivit)
        kustannus-rivit (sort-by (fn [rivi] (get osiojarjestys (:osio rivi))) kustannus-rivit)
        kustannus-rivit (map-indexed (fn [indeksi rivi] (assoc rivi :jarjestys indeksi)) kustannus-rivit)
        kustannus-rivit (mapv #(muodosta-kustannusrivi % (hae-kustannuksista-rivit-vuodelle :kustannukset tarjous-rivit (:nimi %))) kustannus-rivit)
        ;; Uusille rahavarauksille ei synny hoitovuosittaisia arvoja, joten lisätään ne nyt
        kustannus-rivit (mapv (fn [rivi]
                                (if-not (seq (:hoitovuosittaiset-arvot rivi))
                                  (assoc rivi :hoitovuosittaiset-arvot hoitovuosittaiset-arvot)
                                  rivi))
                          kustannus-rivit)

        ;; Jos on tarve lisätä toimenkuva jonkun kustannussuunnitelman vahvistamisen jälkeen, niin sitä ei löydy välttämättä ensimmäisen tarjouksen tiedoista.
        ;; Joten käydään kaikkien tarjousvuosien toimenkuvat läpi ja yhdistetään ne uniikisti
        uniikit-toimenkuvat (distinct (reduce (fn [kaikki-toimenkuvat tarjous-rivi]
                                                (let [toimenkuvat (map #(dissoc % :id :summa) (:toimenkuvat tarjous-rivi))]
                                                  (concat kaikki-toimenkuvat toimenkuvat)))
                                        [] tarjous-rivit))
        toimenkuva-rivit (map #(merge % {:toimenkuva (:nimi %)}) uniikit-toimenkuvat)
        toimenkuva-rivit (mapv #(muodosta-toimenkuvarivi % (hae-toimenkuvista-rivit-vuodelle :toimenkuvat tarjous-rivit (:johto_ja_hallintokorvaus_toimenkuva_id %) (:maksukausi %))) toimenkuva-rivit)

        ;; Päivitä mahdolliset toimenkuvan nimet, jos kesä ja talvikausi on vaikuttamassa tilanteeseen
        toimenkuva-rivit (mapv (fn [rivi]
                                 (let [toimenkuva (:toimenkuva rivi)
                                       nimi (cond
                                              (and (= "päätoiminen apulainen" toimenkuva) (= "talvi" (:maksukausi rivi)))
                                              "Päätoiminen apulainen (talvikausi)"
                                              (and (= "päätoiminen apulainen" toimenkuva) (= "kesä" (:maksukausi rivi)))
                                              "Päätoiminen apulainen (kesäkausi)"
                                              (and (= "apulainen/työnjohtaja" toimenkuva) (= "talvi" (:maksukausi rivi)))
                                              "Apulainen/työnjohtaja (talvikausi)"
                                              (and (= "apulainen/työnjohtaja" toimenkuva) (= "kesä" (:maksukausi rivi)))
                                              "Apulainen/työnjohtaja (kesäkausi)"
                                              :else (:nimi rivi))]
                                   (assoc rivi :nimi nimi :toimenkuva toimenkuva)))
                           toimenkuva-rivit)

        ;; Tarjouksen mukana ei välttämättä tule kaikkia toimenkuvia, jos niitä on tarjouksen tallentamisen jälkeen lisätty urakkalle hallintapaneelista.
        ;; Varmistetaan siis, että kaikki urakan toimenkuvat ovat mukana, kun ne renderöidään frontilla
        kaikki-urakan-toimenkuvat (hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)
        ;; Vertaillaan toimenkuvat-rivit ja kaikki-urakan-toimenkuvat ja lisätään puuttuvat toimenkuvat nollasummilla
        puuttuvat-toimenkuvat (filter
                                (fn [kt]
                                  (not (some #(= (:toimenkuva-id kt) (:toimenkuva-id %)) toimenkuva-rivit)))
                                kaikki-urakan-toimenkuvat)
        toimenkuva-rivit (vec (concat toimenkuva-rivit puuttuvat-toimenkuvat))
        toimenkuva-rivit (sort-by :jarjestys (map
                                               #(assoc % :jarjestys (toimenkuva-kyselyt/paattele-toimenkuvan-jarjestys (:toimenkuva %)))
                                               toimenkuva-rivit))
        tarjousrivit (into [] (sort-by (fn [rivi] (get osiojarjestys (:osio rivi))) (vec (concat kustannus-rivit toimenkuva-rivit [yhteenveto-tavoitehinta-rivi] [yhteenveto-kattohinta-rivi]))))

        kaikki-toimenkuvat (map #(assoc %
                                   :toimenkuva (:nimi %)
                                   :nimi (str/capitalize (:nimi %))) (toimenkuva-kyselyt/hae-toimenkuvat db))

        tarjous {:urakka-id urakka-id
                 :kaikki-toimenkuvat kaikki-toimenkuvat
                 :kattohintakerroin kattohintakerroin
                 :muokkaa-kattohinta-kasin muokkaa-kattohinta-kasin
                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                 :tarjous tarjousrivit}
        ;; Tarkistetaan, että tarjous ei ole tyhjä
        tarjous (if (not= "Kilpailutettavat hankinnat" (:nimi (first (:tarjous tarjous))))
                  {:urakka-id urakka-id
                   :kaikki-toimenkuvat kaikki-toimenkuvat
                   :kattohintakerroin kattohintakerroin
                   :muokkaa-kattohinta-kasin muokkaa-kattohinta-kasin
                   :tarjous (luo-default-tarjous db urakka-id)}
                  tarjous)]
    tarjous))
