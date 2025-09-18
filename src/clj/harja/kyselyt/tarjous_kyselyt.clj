(ns harja.kyselyt.tarjous-kyselyt
  (:require [clojure.string :as str]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]))

(defqueries "harja/kyselyt/tarjous_kyselyt.sql"
  {:positional? true})

(declare tallenna-tarjous<! paivita-tarjous<!
  tallenna-tarjouskustannus<! paivita-tarjouskustannus<!
  tallenna-tarjouksen-johto-ja-hallintokorvaus<! paivita-tarjouksen-johto-ja-hallintokorvaus<!
  hae-tarjouksen-tiedot hae-tarjous-vuodella
  hae-kustannus-tarjoukselle hae-toimenkuva-tarjoukselle poista-tarjouksen-johto-ja-hallintokorvaus<!
  hae-tarjouksen-viimeisin-muokkaaja)

(def osiojarjestys
  {"hankintakustannukset" 1
   "tavoitehintaiset-rahavaraukset" 2
   "erillishankinnat" 3
   "johto-ja-hallintokorvaus" 4
   "hoidonjohtopalkkio" 5})

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
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        ;; Lisätään default tarjoukseen Kilpailutettavat hankinnat, Erillishankinnat ja Hoidonjohtopalkkio
        tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil :jarjestys 1
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot :yhteensa 0.00}
                 {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                 {:nimi "Hoidonjohtopalkkio" :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}]
        ; haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        rahavaraus-rivit (reduce (fn [lopulliset rahavaraus]
                                   (vec (concat lopulliset [{:nimi (:nimi rahavaraus), :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id (:id rahavaraus)
                                                             :hoitovuosittaiset-arvot hoitovuosittaiset-arvot :yhteensa 0.00}])))
                           [] rahavaraukset)
        ;; Järjestetään rahavaraukset kilpailutettavien hankintojen jälkeen
        rahavaraus-rivit (map-indexed (fn [indeksi rivi] (assoc rivi :jarjestys (+ 1 (inc indeksi)))) rahavaraus-rivit)
        ;; Yhdistetään tarjous ja rahavaraukset
        tarjous (vec (concat tarjous rahavaraus-rivit))
        toimenkuvat (hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)
        tarjous (vec (concat tarjous toimenkuvat))
        jarjestetty-tarjous (sort-by (fn [rivi] (get osiojarjestys (:osio rivi)))
                              tarjous)]

    jarjestetty-tarjous))

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
                                                     (if (and
                                                           (not= "yhteensa" (:osio vuosisumma))
                                                           (= vuosi (:vuosi vuosisumma)))
                                                       (:summa vuosisumma) 0))
                                                   hoitovuoden-arvot))]
                 vuosittaiset-arvot))
             (:tarjous tarjous-tietomalli))))

(defn tallenna-tarjous-tietokantaan
  "Tarjous koostuu kolmesta kokonaisuudesta: Tarjouksen kokonaissummasta eli Tavoite- ja Kattohinnasta, Johto-ja-hallintokorvauksista (toimenkuvat) sekä
  Hankinnoista (Kilpailutettavat hankinnat, Erillishankinnat, Rahavarauksista, Hoidonjohtopalkkiosta)."
  [db urakka-id kayttaja-id kattohintakerroin tarjous]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        vuodet (map (fn [vuosi]
                      {:vuosi vuosi}) (range (pvm/vuosi (:alkupvm urakan-tiedot)) (pvm/vuosi (:loppupvm urakan-tiedot))))

        ;; Muokkaa tietomallin vuosittaiset summat tarjous- ja kattohinnaksi
        vuosittaiset-tarjoushinnat (mapv
                                     (fn [vuosi-rivi]
                                       (let [summa (tarjoustietomallista-vuosittaiset-hinnat tarjous (:vuosi vuosi-rivi))]
                                         {:hoitokauden_alkuvuosi (:vuosi vuosi-rivi)
                                          :tarjous_tavoitehinta summa
                                          :urakka_id urakka-id,
                                          :tarjous_kattohinta (* summa kattohintakerroin),
                                          :luoja kayttaja-id}))
                                     vuodet)
        ;; Erota toimenkuvat ja muut kustannukset toisistaan
        hankinta-osiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio", "tavoitehintaiset-rahavaraukset"}
        johto-ja-hallintokorvausosiot #{"johto-ja-hallintokorvaus"}
        kustannukset-tarjouksesta (filter #(contains? hankinta-osiot (:osio %)) (:tarjous tarjous))
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
        toimenkuvat-tarjouksesta (filter #(contains? johto-ja-hallintokorvausosiot (:osio %)) (:tarjous tarjous))

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
                                                               :summa (:summa r)
                                                               :osio (:osio rivi)
                                                               :luoja kayttaja-id})
                                                            (:hoitovuosittaiset-arvot rivi))]
                                          (conj kaikki uudet-rivit)))
                                      [] paivitettavat-toimenkuvat))

        ;; Tallennetaan tarjous- ja kattohinnat tarjouksen päätauluun, johon muut tiedot linkitetään
        tallennukset (mapv
                       (fn [rivi]
                         (let [;; Etsi vuodelle ja urakalle tarjousta
                               tarjousdb (first (hae-tarjous-vuodella db {:vuosi (:hoitokauden_alkuvuosi rivi)
                                                                          :urakka_id urakka-id}))
                               tietokantatarjous (if tarjousdb
                                                   (paivita-tarjous<! db (assoc rivi
                                                                           :muokkaaja kayttaja-id
                                                                           :id (:id tarjousdb)))
                                                   (tallenna-tarjous<! db rivi))
                               ;; Tallennetaan tarjouksen kustannukset ja toimenkuvat tietokantaan
                               vuosittaiset-kustannukset (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) kustannuksetlistaus)
                               _ (mapv (fn [kustannus]
                                         (let [; tarkistetaan, että löytyykö jo tietokannasta
                                               kustannusdb (if (:id tarjousdb)
                                                             (first (hae-kustannus-tarjoukselle db {:tarjous_id (:id tarjousdb)
                                                                                                    :urakka_id urakka-id
                                                                                                    :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi kustannus)
                                                                                                    :rahavaraus_id (:rahavaraus_id kustannus)
                                                                                                    :tehtavaryhma_id (:tehtavaryhma_id kustannus)
                                                                                                    :tehtava_id (:tehtava_id kustannus)
                                                                                                    :osio (:osio kustannus)}))
                                                             nil)]
                                           (if kustannusdb
                                             (paivita-tarjouskustannus<! db (assoc kustannusdb :summa (:summa kustannus)
                                                                              :muokkaaja kayttaja-id))
                                             (tallenna-tarjouskustannus<! db (assoc kustannus :tarjous_id (:id tietokantatarjous))))))
                                   vuosittaiset-kustannukset)

                               vuosittaiset-toimenkuvat (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) toimenkuvatlistaus)

                               ;; Loopataan vuosittaiset toimenkuvat ja lisätään tarvittaessa uudet ja päivitetään olemassaolevat
                               _ (mapv
                                   (fn [toimenkuva]
                                     (let [uusi-db-toimenkuva (when (= -1 (:id toimenkuva))
                                                                ;; Tämä map pyörähtää jokaisena hoitovuonna. Mutta toimenkuvan kannalta riittää
                                                                ;; että toimenkuvia lisätään vain kerran urakalle
                                                                ;; Tarkistetaan siis, ettei toimenkuvaa löydy jo tietokannasta
                                                                (when-not (seq (toimenkuva-kyselyt/hae-urakan-toimenkuva db {:toimenkuva (:toimenkuva toimenkuva)
                                                                                                                             :urakkaid urakka-id}))
                                                                  (toimenkuva-kyselyt/lisaa-urakan-toimenkuva<! db {:toimenkuva (:toimenkuva toimenkuva)
                                                                                                                    :urakkaid urakka-id
                                                                                                                    :urakkakohtainen-nimi (:nimi toimenkuva)})))
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
                                   vuosittaiset-toimenkuvat)]
                           {:tarjousid (:id tietokantatarjous)}))
                       vuosittaiset-tarjoushinnat)]
    tallennukset))

(defn hae-kustannuksista-rivit-vuodelle [avain tarjous-rivit nimi]
  (let [;; Etsitään kustannus, joka vastaa annettua nimeä
        rivit (into [] (sort-by :vuosi (reduce (fn [r rivi]
                                                 (let [r-rivit (keep #(when (= nimi (:nimi %))
                                                                        (dissoc (merge % {:vuosi (:hoitokauden_alkuvuosi rivi)})
                                                                          :id :nimi :maksukausi :osio :tehtava_id :tehtavaryhma_id :rahavaraus_id
                                                                          :johto_ja_hallintokorvaus_toimenkuva_id))
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

(defn lisaa-yhteenvetorivi-tarjoukseen [tarjous]
  (let [;; Lisätään vielä loppuun yhteenvetorivi, joka on viimeisenä
        ;; Vuodet ovat dynaamisia. Päätellään ne tietomallista
        vuodet (vuodet-tietomallista tarjous)
        yhteenvetorivi (reduce (fn [yhteenveto vuosi]
                                 (let [summa (tarjoustietomallista-vuosittaiset-hinnat tarjous (:vuosi vuosi))
                                       hoitovuosittaiset-arvot {:vuosi (:vuosi vuosi)
                                                                :summa summa}]
                                   {:nimi "Yhteensä tavoitehinta"
                                    :osio "yhteensa"
                                    :yhteensa (+ (or (:yhteensa yhteenveto) 0) summa)
                                    :hoitovuosittaiset-arvot (into [] (sort-by :vuosi (conj (:hoitovuosittaiset-arvot yhteenveto) hoitovuosittaiset-arvot)))}))
                         {} vuodet)]
    ; Lisätään yhteenvetorivi tarjoukseen
    (update tarjous :tarjous #(vec (concat % [yhteenvetorivi])))))


(defn hae-tarjous [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (map (fn [vuosi]
                      {:vuosi vuosi}) (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi (:vuosi vuosi) :summa 0.00M}) vuodet)
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        tarjous-rivit (hae-tarjouksen-tiedot db {:urakka_id urakka-id})
        ;; Tarjouksen viimeisin muokkaaja
        viimeisin-muokkaus (first (hae-tarjouksen-viimeisin-muokkaaja db {:urakkaid urakka-id}))
        ;; Mäppää tarjouksen tietokantarivit clojure-mapeiksi.
        tarjous-rivit (mapv
                        (fn [tarjous]
                          (-> tarjous
                            (assoc :kustannukset
                              (if (:kustannukset tarjous)
                                (mapv
                                  (fn [k]
                                    (konversio/pgobject->map k :id :long :nimi :string :summa :double :osio :string :tehtava_id :long :tehtavaryhma_id :long :rahavaraus_id :long))
                                  (konversio/pgarray->vector (:kustannukset tarjous)))
                                []))
                            (assoc :toimenkuvat
                              (mapv
                                (fn [k]
                                  (konversio/pgobject->map k :id :long :nimi :string :summa :double
                                    :maksukausi :string :osio :string :johto_ja_hallintokorvaus_toimenkuva_id :long))
                                (konversio/pgarray->vector (:toimenkuvat tarjous))))))
                        tarjous-rivit)
        ;; Muutetaan ui:lle välitettävään muotoon
        ;; Kustannusrivit tulevat tietokannasta oikeassa järjestyksessä. Merkitään tämä järjestys talteen
        kustannus-rivit (map-indexed (fn [indeksi rivi] (assoc rivi :jarjestys indeksi)) (:kustannukset (first tarjous-rivit)))
        kustannus-rivit (mapv #(muodosta-kustannusrivi % (hae-kustannuksista-rivit-vuodelle :kustannukset tarjous-rivit (:nimi %))) kustannus-rivit)
        toimenkuva-rivit (map #(merge % {:toimenkuva (:nimi %)}) (:toimenkuvat (first tarjous-rivit)))
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
        tarjousrivit (into [] (sort-by (fn [rivi] (get osiojarjestys (:osio rivi))) (vec (concat kustannus-rivit toimenkuva-rivit))))

        kaikki-toimenkuvat (map #(assoc %
                                   :toimenkuva (:nimi %)
                                   :nimi (str/capitalize (:nimi %))) (toimenkuva-kyselyt/hae-toimenkuvat db))

        tarjous {:urakka-id urakka-id
                 :kaikki-toimenkuvat kaikki-toimenkuvat
                 :kattohintakerroin kattohintakerroin
                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                 :tarjous tarjousrivit}
        ;; Tarkistetaan, että tarjous ei ole tyhjä
        tarjous (if (not= "Kilpailutettavat hankinnat" (:nimi (first (:tarjous tarjous))))
                  {:urakka-id urakka-id
                   :kaikki-toimenkuvat kaikki-toimenkuvat
                   :kattohintakerroin kattohintakerroin
                   :tarjous (luo-default-tarjous db urakka-id)}
                  tarjous)

        tarjous (lisaa-yhteenvetorivi-tarjoukseen tarjous)]
    tarjous))
