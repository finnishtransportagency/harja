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
  hae-kustannus-tarjoukselle hae-toimenkuva-tarjoukselle poista-tarjouksen-johto-ja-hallintokorvaus<!)

(def osiojarjestys
  {"hankintakustannukset" 1
   "tavoitehintaiset-rahavaraukset" 2
   "erillishankinnat" 3
   "johto-ja-hallintokorvaus" 4
   "hoidonjohtopalkkio" 5})

(defn hae-urakan-toimenkuvat [db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot]
  (let [toimenkuvat (toimenkuva-kyselyt/hae-urakan-toimenkuvat-alkuvuoden-perusteella db {:urakka-id urakka-id
                                                                                          :urakan-alkuvuosi urakan-alkuvuosi})
        toimenkuvat (if (<= urakan-alkuvuosi 2021)
                      (reduce (fn [uudet-toimenkuvat toimenkuva]
                                (let [uusi-toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "päätoiminen apulainen"
                                                         :nimike "Päätoiminen apulainen (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "apulainen/työnjohtaja"
                                                         :nimike "Apulainen/työnjohtaja (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        :else nil)

                                      toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Päätoiminen apulainen (kesäkausi)")
                                                   (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Apulainen/työnjohtaja (kesäkausi)")
                                                   :else (merge toimenkuva {:nimike (:toimenkuva toimenkuva)}))]

                                  ;; Lisätään talvi/kesäkausi vain, jos ne on noita erikois kovakoodattuja toimenkuvia
                                  (if uusi-toimenkuva
                                    (conj uudet-toimenkuvat uusi-toimenkuva toimenkuva)
                                    (conj uudet-toimenkuvat toimenkuva))))
                        [] toimenkuvat)
                      toimenkuvat)

        ;; Järjestetään toimenkuvat järkevään järjestykseen
        toimenkuvat (mapv (fn [toimenkuva]
                            (-> toimenkuva
                              (assoc :jarjestys (toimenkuva-kyselyt/paattele-toimenkuvan-jarjestys (:nimike toimenkuva))
                                :nimi (str/capitalize (:nimike toimenkuva))
                                :toimenkuva-id (:id toimenkuva)
                                :osio "johto-ja-hallintokorvaus"
                                :tehtava-id nil
                                :tehtavaryhma-id nil
                                :rahavaraus-id nil
                                :hoitovuosittaiset-arvot hoitovuosittaiset-arvot)))
                      toimenkuvat)
        toimenkuvat (sort-by :jarjestys toimenkuvat)]
    toimenkuvat))

(defn luo-default-tarjous [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00}) vuodet)
        ;; Lisätään default tarjoukseen Kilpailutettavat hankinnat, Erillishankinnat ja Hoidonjohtopalkkio
        tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
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
              (poista-tarjouksen-johto-ja-hallintokorvaus<! db {:urakkaid urakka-id
                                                                :toimenkuvaid (or (:vanha-id poistettava)
                                                                                (:toimenkuva-id poistettava))})
              (toimenkuva-kyselyt/poista-toimenkuva! db (:id poistettava)))
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
                                                               :urakka_id urakka-id
                                                               :hoitokauden_alkuvuosi (:vuosi r)
                                                               :johto_ja_hallintokorvaus_toimenkuva_id (:toimenkuva-id rivi)
                                                               :tehtava_id (:tehtava-id rivi)
                                                               :tehtavaryhma_id (:tehtavaryhma-id rivi)
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
                                                                (when-not (seq (toimenkuva-kyselyt/hae-urakan-toimenkuva db {:nimi (:nimi toimenkuva)
                                                                                                                         :urakkaid urakka-id}))
                                                                  (toimenkuva-kyselyt/lisaa-urakan-toimenkuva<! db {:toimenkuva (:nimi toimenkuva)
                                                                                                                  :urakkaid urakka-id
                                                                                                                  :urakkakohtainen-nimi (:nimi toimenkuva)})))
                                           toimenkuvadb (if (:id tarjousdb)
                                                          (first (hae-toimenkuva-tarjoukselle db
                                                                   {:tarjous_id (:id tarjousdb)
                                                                    :urakka_id urakka-id
                                                                    :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi toimenkuva)
                                                                    :johto_ja_hallintokorvaus_toimenkuva_id (or (:id uusi-db-toimenkuva) (:johto_ja_hallintokorvaus_toimenkuva_id toimenkuva))
                                                                    :tehtava_id (:tehtava_id toimenkuva)
                                                                    :tehtavaryhma_id (:tehtavaryhma_id toimenkuva)
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

(defn hae-tarjouksesta-rivit-vuodelle [avain tarjous-rivit nimi]
  (let [;; Etsitään kustannus, joka vastaa annettua nimeä
        rivit (into [] (sort-by :vuosi (reduce (fn [r rivi]
                                                 (let [r-rivit (keep #(when (= nimi (:nimi %))
                                                                        (dissoc (merge % {:vuosi (:hoitokauden_alkuvuosi rivi)})
                                                                          :id :nimi :osio :tehtava_id :tehtavaryhma_id :rahavaraus_id
                                                                          :johto_ja_hallintokorvaus_toimenkuva_id))
                                                                 (avain rivi))]
                                                   (vec (concat r r-rivit))))
                                         [] tarjous-rivit)))]
    rivit))

(defn- muodosta-tarjous-rivi [r hoitovuosittaiset-arvot]
  (-> {:osio (:osio r)
       :nimi (:nimi r)
       :toimenkuva-id (:johto_ja_hallintokorvaus_toimenkuva_id r)
       :tehtava-id (:tehtava_id r)
       :tehtavaryhma-id (:tehtavaryhma_id r)
       :rahavaraus-id (:rahavaraus_id r)
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
  (let [tarjous-rivit (hae-tarjouksen-tiedot db {:urakka_id urakka-id})
        ;; Mäppää tarjouksen tietokantarivit clojure-mapeiksi.
        tarjous-rivit (mapv
                        (fn [tarjous]
                          (-> tarjous
                            (assoc :kustannukset
                              (mapv
                                (fn [k]
                                  (konversio/pgobject->map k :id :long :nimi :string :summa :double :osio :string :tehtava_id :long :tehtavaryhma_id :long :rahavaraus_id :long))
                                (konversio/pgarray->vector (:kustannukset tarjous))))
                            (assoc :toimenkuvat
                              (mapv
                                (fn [k]
                                  (konversio/pgobject->map k :id :long :nimi :string :summa :double :osio :string :johto_ja_hallintokorvaus_toimenkuva_id :long))
                                (konversio/pgarray->vector (:toimenkuvat tarjous))))))
                        tarjous-rivit)

        ;; Muutetaan ui:lle välitettävään muotoon
        kustannus-rivit (mapv #(muodosta-tarjous-rivi % (hae-tarjouksesta-rivit-vuodelle :kustannukset tarjous-rivit (:nimi %))) (:kustannukset (first tarjous-rivit)))
        toimenkuva-rivit (mapv #(muodosta-tarjous-rivi % (hae-tarjouksesta-rivit-vuodelle :toimenkuvat tarjous-rivit (:nimi %))) (:toimenkuvat (first tarjous-rivit)))
        toimenkuva-rivit (sort-by :jarjestys (map
                                               #(assoc % :jarjestys (toimenkuva-kyselyt/paattele-toimenkuvan-jarjestys (:nimi %)))
                                               toimenkuva-rivit))
        tarjousrivit (into [] (sort-by (fn [rivi] (get osiojarjestys (:osio rivi))) (vec (concat kustannus-rivit toimenkuva-rivit))))

        kaikki-toimenkuvat (map #(assoc % :nimi (str/capitalize (:nimi %))) (toimenkuva-kyselyt/hae-toimenkuvat db))

        tarjous {:urakka-id urakka-id
                 :kaikki-toimenkuvat kaikki-toimenkuvat
                 :tarjous tarjousrivit}
        ;; Tarkistetaan, että tarjous ei ole tyhjä
        tarjous (if (empty? (first (:tarjous tarjous)))
                  {:urakka-id urakka-id
                   :kaikki-toimenkuvat kaikki-toimenkuvat
                   :tarjous (luo-default-tarjous db urakka-id)}
                  tarjous)

        tarjous (lisaa-yhteenvetorivi-tarjoukseen tarjous)]
    tarjous))
