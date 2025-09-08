(ns harja.kyselyt.tarjous-kyselyt
  (:require [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]))

(defqueries "harja/kyselyt/tarjous_kyselyt.sql"
  {:positional? true})

(declare tallenna-tarjous<! paivita-tarjous<!
  tallenna-tarjouskustannus<! paivita-tarjouskustannus<!
  tallenna-tarjouksen-johto-ja-hallintokorvaus<! paivita-tarjouksen-johto-ja-hallintokorvaus<!
  hae-tarjouksen-tiedot hae-tarjous-vuodella
  hae-kustannus-tarjoukselle hae-toimenkuva-tarjoukselle
  hae-urakan-tarjous-tavoitehinnat paivita-urakan-tavoite-tarjous<! lisaa-urakan-tavoite-tarjous<!)

(def osiojarjestys
  {"hankintakustannukset" 1
   "tavoitehintaiset-rahavaraukset" 2
   "erillishankinnat" 3
   "johto-ja-hallintokorvaus" 4
   "hoidonjohtopalkkio" 5})

(defn luo-default-tarjous [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        vuodet (range (pvm/vuosi (:alkupvm urakan-tiedot)) (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00}) vuodet)
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        ;; Lisätään default tarjoukseen Kilpailutettavat hankinnat, Erillishankinnat ja Hoidonjohtopalkkio
        tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot :yhteensa 0.00}
                 {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id (:id tehtavaryhma) :rahavaraus-id nil
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                 {:nimi "Hoidonjohtopalkkio" :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id (:id tehtava) :tehtavaryhma-id nil :rahavaraus-id nil
                  :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}]
        ; haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        rahavaraus-rivit (reduce (fn [lopulliset rahavaraus]
                                   (vec (concat lopulliset [{:nimi (:nimi rahavaraus), :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id (:id rahavaraus)
                                                             :hoitovuosittaiset-arvot hoitovuosittaiset-arvot :yhteensa 0.00}])))
                           [] rahavaraukset)
        tarjous (vec (concat tarjous rahavaraus-rivit))
        ;; Pakotetaan tässä vaiheessa kehitystä tietyt toimenkuvat. Nämä voidaan asetaa myöhemmin defaulttina jostain hallintapaneelin käyttiksestä
        ;; id 10, 'Valmistelukausi ennen urakka-ajan alkua',
        ;; id 2, 'Vastuunalainen työnjohtaja'
        ;; id 8, '2. työnjohtaja'
        ;; id 9, '3. työnjohtaja'
        ;; id 5, 'Viherhoidosta vastaava henkilö'
        ;; id 7, 'Harjoittelija'
        toimenkuvat [
                     ;; Johto ja hallintokorvaukset eli toimenkuvat
                     {:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                     {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                     {:nimi "2. työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 8 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                     {:nimi "3. työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 9 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                     {:nimi "Viherhoidosta vastaava henkilö", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 5 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}
                     {:nimi "Harjoittelija", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 7 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                      :hoitovuosittaiset-arvot hoitovuosittaiset-arvot, :yhteensa 0.00}]
        tarjous (vec (concat tarjous (sort-by :toimenkuva-id toimenkuvat)))
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
  [db urakka-id kayttaja-id kattohintakerroin tarjous-tietomalli]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        ;; Vuodet ovat dynaamisia. Päätellään ne tietomallista
        vuodet (vuodet-tietomallista tarjous-tietomalli)

        ;; HAetaan urakan mahdolliset aiemmat tarjoushinnat urakka_tavoite -taulusta
        urakan-tavoitteet-tietokannasta (hae-urakan-tarjous-tavoitehinnat db {:urakkaid urakka-id})

        ;; Muokkaa tietomallin vuosittaiset summat tarjous- ja kattohinnaksi
        vuosittaiset-tarjoushinnat (mapv
                                     (fn [vuosi-rivi]
                                       (let [summa (tarjoustietomallista-vuosittaiset-hinnat tarjous-tietomalli (:vuosi vuosi-rivi))]
                                         {:hoitokauden_alkuvuosi (:vuosi vuosi-rivi)
                                          :tarjous_tavoitehinta summa
                                          :urakka_id urakka-id,
                                          :tarjous_kattohinta (* summa kattohintakerroin),
                                          :luoja kayttaja-id}))
                                     vuodet)
        hankinta-osiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio", "tavoitehintaiset-rahavaraukset"}
        johto-ja-hallintokorvausosiot #{"johto-ja-hallintokorvaus"}
        kustannukset-tarjouksesta (filter #(contains? hankinta-osiot (:osio %)) (:tarjous tarjous-tietomalli))
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
        toimenkuvat-tarjouksesta (filter #(contains? johto-ja-hallintokorvausosiot (:osio %)) (:tarjous tarjous-tietomalli))
        toimenkuvatlistaus (flatten (reduce
                                      (fn [kaikki rivi]
                                        (let [uudet-rivit (mapv
                                                            (fn [r]
                                                              {:urakka_id urakka-id
                                                               :hoitokauden_alkuvuosi (:vuosi r)
                                                               :johto_ja_hallintokorvaus_toimenkuva_id (:toimenkuva-id rivi)
                                                               :tehtava_id (:tehtava-id rivi)
                                                               :tehtavaryhma_id (:tehtavaryhma-id rivi)
                                                               :summa (:summa r)
                                                               :osio (:osio rivi)
                                                               :luoja kayttaja-id})
                                                            (:hoitovuosittaiset-arvot rivi))]
                                          (conj kaikki uudet-rivit)))
                                      [] toimenkuvat-tarjouksesta))

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
                               dbvastaus (if urakka-tavoite-db
                                           (paivita-urakan-tavoite-tarjous<! db (assoc urakka-tavoite-db
                                                                                  :tarjous_tavoitehinta (:tarjous_tavoitehinta rivi)
                                                                                  :muokkaaja kayttaja-id))
                                           (lisaa-urakan-tavoite-tarjous<! db {:urakkaid urakka-id
                                                                               :hoitovuosinro kuluva-hoitovuosi-nro
                                                                               :tarjous_tavoitehinta (:tarjous_tavoitehinta rivi)
                                                                               :luoja kayttaja-id}))

                               ;; Tallennetaan tarjouksen kustannukset ja toimenkuvat tietokantaan
                               vuosittaiset-kustannukset (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) kustannuksetlistaus)
                               vuosittaiset-toimenkuvat (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) toimenkuvatlistaus)
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
                               _ (mapv
                                   (fn [toimenkuva]
                                     (let [toimenkuvadb (if (:id tarjousdb)
                                                          (first (hae-toimenkuva-tarjoukselle db {:tarjous_id (:id tarjousdb)
                                                                                                  :urakka_id urakka-id
                                                                                                  :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi toimenkuva)
                                                                                                  :johto_ja_hallintokorvaus_toimenkuva_id (:johto_ja_hallintokorvaus_toimenkuva_id toimenkuva)
                                                                                                  :tehtava_id (:tehtava_id toimenkuva)
                                                                                                  :tehtavaryhma_id (:tehtavaryhma_id toimenkuva)
                                                                                                  :osio (:osio toimenkuva)}))
                                                          nil)]
                                       (if toimenkuvadb
                                         (paivita-tarjouksen-johto-ja-hallintokorvaus<! db (assoc toimenkuvadb :summa (:summa toimenkuva)
                                                                                             :muokkaaja kayttaja-id))
                                         (tallenna-tarjouksen-johto-ja-hallintokorvaus<! db (assoc toimenkuva :tarjous_id (:id tietokantatarjous))))))
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
        tarjousrivit (into [] (sort-by (fn [rivi] (get osiojarjestys (:osio rivi))) (vec (concat kustannus-rivit toimenkuva-rivit))))

        tarjous {:urakka-id urakka-id
                 :tarjous tarjousrivit}
        ;; Tarkistetaan, että tarjous ei ole tyhjä
        tarjous (if (empty? (first (:tarjous tarjous)))
                  {:urakka-id urakka-id :tarjous (luo-default-tarjous db urakka-id)}
                  tarjous)

        tarjous (lisaa-yhteenvetorivi-tarjoukseen tarjous)]
    tarjous))
