(ns harja.kyselyt.tarjous-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tarjous_kyselyt.sql"
  {:positional? true})

(declare tallenna-tarjous<! tallenna-tarjouskustannus<! tallenna-tarjouksen-johto-ja-hallintokorvaus<!)

(defn vuodet-tietomallista [malli]
  (reduce (fn [rivit vuosi-rivi]
            (let [index (inc (count rivit))]
              (concat rivit [{:otsikko (str index ". Hoitovuosi (€)") :vuosi (:vuosi vuosi-rivi)}])))
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
  (let [;; Vuodet ovat dynaamisia. Päätellään ne tietomallista
        vuodet (vuodet-tietomallista tarjous-tietomalli)
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
                         (let [;; Tallenna tarjous tietokantaan
                               tietokantatarjous (tallenna-tarjous<! db rivi)
                               ;; Tallennetaan tarjouksen rahavaraukset tietokantaan
                               vuosittaiset-kustannukset (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) kustannuksetlistaus)
                               vuosittaiset-toimenkuvat (filter #(= (:hoitokauden_alkuvuosi rivi) (:hoitokauden_alkuvuosi %)) toimenkuvatlistaus)
                               kustannukset (mapv
                                              (fn [r] (tallenna-tarjouskustannus<! db (assoc r :tarjous_id (:id tietokantatarjous))))
                                              vuosittaiset-kustannukset)
                               toimenkuvat (mapv
                                              (fn [t] (tallenna-tarjouksen-johto-ja-hallintokorvaus<! db (assoc t :tarjous_id (:id tietokantatarjous))))
                                             vuosittaiset-toimenkuvat)]
                           {:tarjousid (:id tietokantatarjous)}))
                       vuosittaiset-tarjoushinnat)]
    tallennukset))
