(ns harja.palvelin.raportointi.raportit.kanavien-muutos-ja-lisatyot
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                     pylvaat-kuukausittain ei-osumia-aikavalilla-teksti rivi]]

            [harja.domain.raportointi :refer [info-solu]]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [clojure.string :as str]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/kanavien_muutos_ja_lisatyot.sql")

(def hallintayksikko {:lyhenne "KAN"
                      :nimi "Kanavat ja avattavat sillat"})

(def urakkatyyppi {})


(def ei-indeksilaskentaa-solu (info-solu "Ei indeksilaskentaa"))
(def indeksi-puuttuu-info-solu (info-solu "Indeksi puuttuu"))
(def ei-raha-summaa-info-solu (info-solu "Ei rahasummaa"))



(defn- sopimus-tai-yksikkohintainen? [tyyppi]
  (some #(= tyyppi %) [:sopimushintaiset :yksikkohintaiset]))

(defn kustannustyypin-nimi
  [tyyppi]
  (case tyyppi
    "sopimushintainen-tyo-ja-materiaali" "Sopimushintainen työ ja materiaali"
    "omakustanteinen-tyo" "Omakustanteinen työ ja materiaali"
    "muu-tyo" "Yksikköhintainen työ"
    "materiaali" "Yksikköhintainen materiaali"
    "muu" "Muu kulu"))

(defn tyypin-sort-avain
  [tyo]
  (case (:tyyppi tyo)
    "lisatyo" 0
    "muutostyo" 10
    "vahinkojen-korjaukset" 20
    "akillinen-hoitotyo" 30

    100))



(defn- sarakkeet [tyyppi]
  (case tyyppi
    :muutos-ja-lisatyot
    [{:leveys 9 :otsikko "Hip hei!"}
     {:leveys 1 :otsikko "Pvm"}
     {:leveys 1 :otsikko "Tehtava"}
     {:leveys 1 :otsikko "Kohde"}
     {:leveys 1 :otsikko "Kohteen osa"}
     {:leveys 1 :otsikko "Huoltokohde"}
     {:leveys 1 :otsikko "Lisätieto"}
     {:leveys 1 :otsikko "Hinnoittelu"}
     {:leveys 1 :otsikko "Summa" :fmt :raha}
     {:leveys 1 :otsikko "Indeksi" :fmt :raha}]
    :muutos-ja-lisatyot-koko-maa
    [{:leveys 3 :otsikko "TODO: URAKAN NIMI"}
     {:leveys 1 :otsikko "Hinnoittelu"}
     {:leveys 1 :otsikko "Summa" :fmt :raha}
     {:leveys 1 :otsikko "Indeksi" :fmt :raha}])
  )



(defn- kaikki-yhteensa-rivit [muutos-ja-lisatyot]
  (let [kaikkien-kululajien-rivit muutos-ja-lisatyot
        toimenpideinstansseittain (group-by :tpi-nimi kaikkien-kululajien-rivit)
        rivit (conj
                (into []
                      (concat
                        (for [tpin-rivit toimenpideinstansseittain]
                          [(key tpin-rivit)
                           ""
                           (reduce + 0 (keep :toteutunut-maara (val tpin-rivit)))
                           ""]
                          )))

                ["Yhteensä"
                 ""
                 (reduce + 0 (keep :toteutunut-maara kaikkien-kululajien-rivit))
                 ""])]
    rivit))


(defn- toimenpiteiden-summa [kentat]
  (reduce + (keep identity kentat)))

(defn- kentan-summa [tietorivit kentta]
  (toimenpiteiden-summa (map kentta tietorivit)))

(defn- tpi-kohtaiset-rivit [tietorivit tyyppi]
  (into []
        (concat
          (for [rivi tietorivit]
            [rivi]
            ;[(:tpi-nimi rivi)
            ; (if (sopimus-tai-yksikkohintainen? tyyppi)
            ;   (or (:suunniteltu-maara rivi) 0)
            ;   "")
            ; (or (:toteutunut-maara rivi) 0)
            ; (if (sopimus-tai-yksikkohintainen? tyyppi)
            ;   (- (or (:suunniteltu-maara rivi) 0)
            ;      (or (:toteutunut-maara rivi) 0))
            ;   "")]

            ))))


;(defn yksittaiset-tyorivit
;  [konteksti tyot]
;  (mapv #(rivi (when-not (= konteksti :urakka) (get-in % [:urakka :nimi]))
;               (pvm/pvm (:alkanut %))
;               (tyon-tyypin-nimi (:tyyppi %))
;               (get-in % [:tpi :nimi])
;               (get-in % [:tehtava :nimi])
;               (or (:lisatieto %) "")
;               (if (get-in % [:tehtava :paivanhinta])
;                 "Päivän hinta"
;                 (get-in % [:tehtava :maara]))
;               (or (get-in % [:tehtava :summa]) ei-raha-summaa-info-solu)
;               (or (:korotus %) indeksi-puuttuu-info-solu))
;        tyot))

(defn hintaryhman-otsikko
  [hintaryhma]
  (case (keyword hintaryhma)
    :sopimushintainen-tyo-tai-materiaali "Sopimushintaiset työt ja materiaalit"
    :omakustanteinen-tyo-tai-materiaali "Omakustannushintaiset työt ja materiaalit"
    :muu-tyo "Muut työt (ei indeksilaskentaa)"
    :varaosat-ja-materiaalit "Varaosat ja materiaalit"
    :muut-kulut "Muut"
    ""))

(defn yksittaiset-toimenpiderivit
  [konteksti tyot]
  (mapv #(rivi
           (pvm/pvm (:pvm %))
           (:tehtava %)
           (:kohde %)
           (:kohteenosa %)
           (:huoltokohde %)
           (str (or (:otsikko %) "") (or (:lisatieto %) ""))
           (hintaryhman-otsikko (:hinnoittelu_ryhma %))
           (or (:summa %) ei-raha-summaa-info-solu)
           (or (:maara %) indeksi-puuttuu-info-solu))       ;; TODO: hox että nää ei oo vielä oikein
        tyot))


(defn- summarivi [tietorivit tyyppi]
  (let [kaikki-suunnitellut (kentan-summa tietorivit :suunniteltu-maara)
        kaikki-toteutuneet (kentan-summa tietorivit :toteutunut-maara)]
    ["Yhteensä" (if (sopimus-tai-yksikkohintainen? tyyppi)
                  kaikki-suunnitellut
                  "")
     kaikki-toteutuneet
     (if (sopimus-tai-yksikkohintainen? tyyppi)
       (- kaikki-suunnitellut
          kaikki-toteutuneet)
       "")]))


(defn- kulutyypin-rivit [tietorivit tyyppi]
  (conj
    (tpi-kohtaiset-rivit tietorivit tyyppi)
    (summarivi tietorivit tyyppi)))

(defn- taulukko [otsikko tyyppi data]
  [:taulukko {:otsikko otsikko
              :tyhja (when (empty? data) "Ei raportoitavaa.")
              :sheet-nimi otsikko
              :viimeinen-rivi-yhteenveto? true}
   (sarakkeet tyyppi)
   (yksittaiset-toimenpiderivit tyyppi data)
   ])



;(defn hae-kanavien-muutos-ja-lisatyot-raportille-jep
;  [db user urakka-annettu? urakka-id
;   urakkatyyppi hallintayksikko-annettu? hallintayksikko-id
;   toimenpide-id alkupvm loppupvm ryhmiteltyna? konteksti]
;  (let [parametrit {:urakka_annettu urakka-annettu?
;                    :urakka urakka-id
;                    :urakkatyyppi urakkatyyppi
;                    :hallintayksikko_annettu hallintayksikko-annettu?
;                    :hallintayksikko hallintayksikko-id
;                    :rajaa_tpi (not (nil? toimenpide-id)) :tpi toimenpide-id
;                    :alku alkupvm :loppu loppupvm}
;        toteumat (if ryhmiteltyna?
;                   (if (= konteksti :koko-maa)
;                     (hae-tyypin-ja-hyn-mukaan-ryhmitellyt-muutos-ja-lisatyot-raportille db parametrit)
;                     (when (= konteksti :hallintayksikko)
;                       (hae-tyypin-ja-urakan-mukaan-ryhmitellyt-hyn-muutos-ja-lisatyot-raportille db parametrit)))
;                   (hae-muutos-ja-lisatyot-raportille db parametrit))]
;    toteumat))
;


;; TODO: kohde rajattu + tehtävä rajattu, kokomaan tehtävällä rajatut jne. tarkista kombinaatiot
;; TODo: oikeustarkistus?

(defn hae-kanavien-muutos-ja-lisatyot-raportille
  [db {:keys [alkupvm loppupvm urakka kohde tehtava] :as parametrit}]
  (let [hakuparametrit {:urakka urakka :alkupvm alkupvm :loppupvm loppupvm :kohde kohde :tehtava tehtava}

        muutos-ja-lisatyot (if tehtava
                             (hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot db hakuparametrit)
                             (if kohde
                               (hae-kanavien-kohdekohtaiset-muutos-ja-lisatyot db hakuparametrit)
                               (if urakka
                                 (hae-kanavien-urakkakohtaiset-muutos-ja-lisatyot db hakuparametrit)
                                 (hae-kanavien-muutos-ja-lisatyot db hakuparametrit)
                                 )))]
    (log/debug (prn-str "muutos-ja-lisatyot" muutos-ja-lisatyot))
    (log/debug (prn-str "hakuparametrit" hakuparametrit))
    muutos-ja-lisatyot
    ))

(defn suorita
  [db user {:keys [alkupvm loppupvm urakka-id kohde-id tehtava-id] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        :default :koko-maa)

        ;; kahden tyyppisiä rapskoja KONTEKSTIN mukaan, muut sql mrajauksisaa

        ;; kayta-ryhmittelya? (and (not urakoittain?) (not= :urakka konteksti))

        raportin-kontekstin-nimi (case konteksti
                                   :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                                   :koko-maa "KOKO MAA")
        raportin-nimi "Muutos- ja lisätöiden raportti"

        kohde-nimi (if kohde-id
                     (:nimi (first (hae-kanavakohteen-nimi db {:id kohde-id})))
                     "Kaikki kohteet")

        tpi-nimi (if tehtava-id
                   (:nimi (first (hae-kanavatoimenpiteen-nimi db {:id tehtava-id})))
                   "Kaikki toimenpiteet")


        raportin-otsikko (raportin-otsikko raportin-kontekstin-nimi raportin-nimi alkupvm loppupvm) ;; TODO: tarkempien raporttien rajaavat tiedot: kohde, tehtava

        ; hakuparametrit {:urakka urakka-id :alkupvm alkupvm :loppupvm loppupvm :kohde kohde-id :tehtava tehtava-id}

        muutos-ja-lisatyot (hae-kanavien-muutos-ja-lisatyot-raportille db parametrit)

        ;muutos-ja-lisatyot (map #(assoc % :toteutunut-maara
        ;                                  (+ (:summat %)
        ;                                     (:summat_kan_hinta_yksikkohinnalla %)
        ;                                     (:summat_yht_yksikkohinnalla %)))
        ;                        (hae-muutos-ja-lisatyot db hakuparametrit))

        ]

    (log/debug "Kanavien muutos- ja lisätyöt, suorita: " parametrit)


    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}



     (let [kaikki-yht-rivit (kaikki-yhteensa-rivit muutos-ja-lisatyot)
           sarakkeet (sarakkeet :muutos-ja-lisatyot)
           toimenpiderivit (taulukko "Muutos- ja lisätyöt" :muutos-ja-lisatyot muutos-ja-lisatyot)
       ]
    [:taulukko {:otsikko "Kaikki yhteensä"
                :tyhja (when (empty? kaikki-yht-rivit) "Ei raportoitavaa.")
                :sheet-nimi "Yhteensä"
                :viimeinen-rivi-yhteenveto? true}
     sarakkeet
     toimenpiderivit
     ]) ] ))


;(defn hae-muutos-ja-lisatyot-aikavalille
;  [db user urakka-annettu? urakka-id
;   urakkatyyppi hallintayksikko-annettu? hallintayksikko-id
;   toimenpide-id alkupvm loppupvm ryhmiteltyna? konteksti]
;  (let [parametrit {:urakka_annettu urakka-annettu?
;                    :urakka urakka-id
;                    :urakkatyyppi urakkatyyppi
;                    :hallintayksikko_annettu hallintayksikko-annettu?
;                    :hallintayksikko hallintayksikko-id
;                    :rajaa_tpi (not (nil? toimenpide-id)) :tpi toimenpide-id
;                    :alku alkupvm :loppu loppupvm}
;        toteumat (if ryhmiteltyna?
;                   (if (= konteksti :koko-maa)
;                     (hae-tyypin-ja-hyn-mukaan-ryhmitellyt-muutos-ja-lisatyot-raportille db parametrit)
;                     (when (= konteksti :hallintayksikko)
;                       (hae-tyypin-ja-urakan-mukaan-ryhmitellyt-hyn-muutos-ja-lisatyot-raportille db parametrit)))
;                   (hae-muutos-ja-lisatyot-raportille db parametrit))]
;    toteumat))
;
;(defn yksittaiset-tyorivit
;  [konteksti tyot]
;  (mapv #(rivi (when-not (= konteksti :urakka) (get-in % [:urakka :nimi]))
;               (pvm/pvm (:alkanut %))
;               (tyon-tyypin-nimi (:tyyppi %))
;               (get-in % [:tpi :nimi])
;               (get-in % [:tehtava :nimi])
;               (or (:lisatieto %) "")
;               (if (get-in % [:tehtava :paivanhinta])
;                 "Päivän hinta"
;                 (get-in % [:tehtava :maara]))
;               (or (get-in % [:tehtava :summa]) ei-raha-summaa-info-solu)
;               (or (:korotus %) indeksi-puuttuu-info-solu))
;        tyot))
;
;(defn tyyppikohtaiset-rivit
;  [tyot]
;  (mapv #(rivi
;          (tyon-tyypin-nimi (:tyyppi %))
;          (or (get-in % [:tehtava :summa]) ei-raha-summaa-info-solu)
;          (or (:korotus %) indeksi-puuttuu-info-solu))
;        (sort-by tyypin-sort-avain tyot)))
;
;(defn suorita [db user {:keys [urakka-id hallintayksikko-id toimenpide-id
;                               alkupvm loppupvm urakkatyyppi urakoittain?] :as parametrit}]
;  (let [konteksti (cond urakka-id :urakka
;                        hallintayksikko-id :hallintayksikko
;                        :default :koko-maa)
;        urakka-annettu? (boolean urakka-id)
;        hallintayksikko-annettu? (boolean hallintayksikko-id)
;        kayta-ryhmittelya? (and (not urakoittain?) (not= :urakka konteksti))
;        muutos-ja-lisatyot-kannasta (into []
;                                          (map konv/alaviiva->rakenne)
;                                          (hae-muutos-ja-lisatyot-aikavalille db user
;                                                                              urakka-annettu? urakka-id
;                                                                              (when urakkatyyppi (name urakkatyyppi))
;                                                                              hallintayksikko-annettu? hallintayksikko-id
;                                                                              toimenpide-id
;                                                                              alkupvm loppupvm kayta-ryhmittelya?
;                                                                              konteksti))
;        muutos-ja-lisatyot (if kayta-ryhmittelya?
;                             muutos-ja-lisatyot-kannasta
;                             (reverse (sort-by (juxt (comp :id :urakka) :alkanut) muutos-ja-lisatyot-kannasta)))
;        sort-avain (if (= konteksti :koko-maa) :elynumero
;                     (if (= konteksti :hallintayksikko) :nimi :id))
;        muutos-ja-lisatyot-hyn-mukaan (sort-by #(or (sort-avain (first %)) 100000)
;                                               (seq (group-by :hallintayksikko
;                                                              muutos-ja-lisatyot)))
;        raportin-nimi "Muutos- ja lisätöiden raportti"
;        tpi-nimi (if toimenpide-id
;                   (:nimi (first (toimenpiteet-q/hae-tuote-kolmostason-toimenpidekoodilla db {:id toimenpide-id})))
;                   "Kaikki toimenpiteet")
;        otsikko (raportin-otsikko
;                  (case konteksti
;                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
;                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
;                    :koko-maa "KOKO MAA")
;                  raportin-nimi alkupvm loppupvm)
;
;        otsikot (if kayta-ryhmittelya?
;                  [{:leveys 7 :otsikko "Tyyppi"}
;                   {:leveys 5 :otsikko "Summa €" :fmt :raha}
;                   {:leveys 5 :otsikko "Ind.korotus €" :fmt :raha}]
;                  [(when-not (= konteksti :urakka) {:leveys 10 :otsikko "Urakka"})
;                   {:leveys 5 :otsikko "Pvm"}
;                   {:leveys 7 :otsikko "Tyyppi"}
;                   {:leveys 12 :otsikko "Toimenpide"}
;                   {:leveys 12 :otsikko "Tehtävä"}
;                   {:leveys 12 :otsikko "Lisätieto"}
;                   {:leveys 5 :otsikko "Määrä"}
;                   {:leveys 5 :otsikko "Summa €" :fmt :raha}
;                   {:leveys 5 :otsikko "Ind.korotus €" :fmt :raha}])]
;    [:raportti {:nimi raportin-nimi
;                :orientaatio :landscape}
;     [:taulukko {:otsikko                    (str otsikko ", " tpi-nimi)
;                 :viimeinen-rivi-yhteenveto? true
;                 :sheet-nimi                 raportin-nimi}
;      (keep identity otsikot)
;
;
;      (keep identity
;            (into []
;                  (concat
;                    (apply concat
;                           (for [[hy hyn-tyot] muutos-ja-lisatyot-hyn-mukaan]
;                             (concat
;                               (when (or urakoittain? (not= :urakka konteksti))
;                                 [{:otsikko (if (:nimi hy)
;                                              (str (:elynumero hy) " " (:nimi hy))
;                                              "Ilmoitukset ilman urakkaa")}])
;
;                               (if kayta-ryhmittelya?
;                                 (apply concat
;                                        (for [[urakka tyot] (group-by :urakka hyn-tyot)]
;                                          (tyyppikohtaiset-rivit tyot)))
;                                 (apply concat
;                                        (for [[urakka tyot] (group-by :urakka hyn-tyot)]
;                                          (yksittaiset-tyorivit konteksti tyot))))
;                               (when-not (empty? hyn-tyot)
;                                 (let [summat-yht (reduce + (keep #(get-in % [:tehtava :summa]) hyn-tyot))
;                                       korotukset-yht (reduce + (keep :korotus hyn-tyot))
;                                       alueen-teksti (if (= :urakka konteksti)
;                                                       (str (get-in (first hyn-tyot) [:urakka :nimi]) " yhteensä")
;                                                       (str (:nimi hy) " yhteensä"))
;                                       kentat (if kayta-ryhmittelya?
;                                                (keep identity [alueen-teksti summat-yht korotukset-yht])
;                                                (if (or (not urakoittain?) (= :urakka konteksti))
;                                                  (keep identity [alueen-teksti "" "" "" "" "" summat-yht korotukset-yht])
;                                                  (keep identity [alueen-teksti "" "" "" "" "" "" summat-yht korotukset-yht])))]
;                                   (when (:nimi hy)
;                                     [{:lihavoi? true
;                                       :rivi     kentat}]))))))
;                    ;; koko maan kaikki työt ryhmiteltynä
;                    (when (and (not= konteksti :urakka)
;                               (not (empty? muutos-ja-lisatyot))
;                               kayta-ryhmittelya?)
;
;                      (let [alueen-nimi (if (= konteksti :koko-maa)
;                                          "KOKO MAA"
;                                          (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id))))]
;                        (concat
;                         [{:otsikko alueen-nimi}]
;                         (let [kokomaan-muutos-ja-lisatyot-ryhmiteltyna
;                               (for [[tyyppi toteumat] (group-by :tyyppi muutos-ja-lisatyot)]
;                                 (reduce (fn [summa toteuma]
;                                           (assoc-in
;                                             (assoc summa :tyyppi tyyppi
;                                                          :korotus (when (and (:korotus summa) (:korotus toteuma))
;                                                                     (+ (:korotus summa)
;                                                                        (:korotus toteuma))))
;                                             [:tehtava :summa] (when (and (get-in summa [:tehtava :summa])
;                                                                          (get-in toteuma [:tehtava :summa]))
;                                                                 (+ (get-in summa [:tehtava :summa])
;                                                                    (get-in toteuma [:tehtava :summa])))))
;                                         {:tehtava {:summa 0} :korotus 0}
;                                         toteumat))
;                               kokomaa-yhteensa [(str alueen-nimi " yhteensä")
;                                                 (reduce + (keep #(get-in % [:tehtava :summa]) muutos-ja-lisatyot))
;                                                 (reduce + (keep :korotus muutos-ja-lisatyot))]]
;
;                           (concat (tyyppikohtaiset-rivit kokomaan-muutos-ja-lisatyot-ryhmiteltyna)
;                                   [{:lihavoi? true
;                                     :rivi     kokomaa-yhteensa}]))))))))]
;     [:teksti (str "Summat ja indeksit yhteensä "
;                   (fmt/euro-opt (+
;                                   (reduce + (keep #(get-in % [:tehtava :summa]) muutos-ja-lisatyot))
;                                   (reduce + (keep :korotus muutos-ja-lisatyot)))))]]))
;
;
