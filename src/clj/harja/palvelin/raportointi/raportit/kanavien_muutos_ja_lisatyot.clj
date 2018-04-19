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

(defn- urakat
  "Palauttaa urakka id:t. Hakee id:t ryhmiteltyjen työrivien avaimesta."
  [tyot-ryhmiteltyna]
  (distinct (map #(get % :urakka) (keys tyot-ryhmiteltyna))))

(defn- urakan-nimi
  [db urakka-id]
  (hae-urakan-nimi db [:urakkaid urakka-id]))

(defn- hinnoittelu-ryhmat
  "Palauttaa hinnoitteluryhmien tunnukset. Hakee ne ryhmiteltyjen työrivien avaimesta."
  [tyot-ryhmiteltyna]
  (distinct (map #(get % :hinnoittelu_ryhma) (keys tyot-ryhmiteltyna))))

(defn- hinnoitteluryhman-nimi
  [hinnoitteluryhma]
  (case (keyword hinnoitteluryhma)
    :sopimushintainen-tyo-tai-materiaali "Sopimushintainen työ tai materiaali"
    :omakustanteinen-tyo-tai-materiaali "Omakustanteinen työ tai materiaali"
    :varaosat-ja-materiaalit "Varaosat ja materiaalit"
    :muu-tyo "Muut työt (ei indeksilaskentaa)"
    :muut-kulut "Muut"))


(defn- urakan-hinnoitteluryhmakohtaiset-rivit
  [db urakka tyot-ryhmiteltyna]
  (let [urakan-nimi (urakan-nimi db urakka)]
    (map (fn[hinnoitteluryhma tyot-ryhmiteltyna](urakan-hinnoitteluryhmakohtainen-summarivi
            (tyot-ryhmiteltyna {:urakka urakka :hinnoittelu_ryhma (name hinnoitteluryhma)})))

         (keys hinnoitteluryhman-nimi))
    )

  ;(get tyot-ryhmiteltyna {:urakka urakka :hinnoittelu_ryhma hinnoitteluryhma})
  )

(defn- urakan-hinnoitteluryhmakohtainen-summarivi
  [tyot-suodatettuna]

  (hash-map
    :hinnoittelu ""
    )
  (reduce +  (keep #(* (get-in % [:maara])(get-in % [:summa])) tyot-suodatettuna))
  )

(defn- sarakkeet [tyyppi]
  (case tyyppi
    :muutos-ja-lisatyot
    [{:leveys 5 :otsikko "Pvm"}
     {:leveys 8 :otsikko "Tehtava"}
     {:leveys 8 :otsikko "Kohde"}
     {:leveys 8 :otsikko "Kohteen osa"}
     {:leveys 8 :otsikko "Huoltokohde"}
     {:leveys 15 :otsikko "Lisätieto"}
     {:leveys 8 :otsikko "Hinnoittelu"}
     {:leveys 5 :otsikko "Summa" :fmt :raha}
     {:leveys 5 :otsikko "Indeksi" :fmt :raha}]
    :muutos-ja-lisatyot-koko-maa
    [{:leveys 5 :otsikko "Hinnoittelu"}
     {:leveys 5 :otsikko "Summa" :fmt :raha}
     {:leveys 5 :otsikko "Indeksi" :fmt :raha}]
    :yhteenveto))

(def ei-indeksilaskentaa-solu (info-solu "Ei indeksilaskentaa"))
(def indeksi-puuttuu-info-solu (info-solu "Indeksi puuttuu"))
(def ei-raha-summaa-info-solu (info-solu "Ei rahasummaa"))




(defn taulukko-hintaryhmakohtaiset-summarivit
  [urakan-hintaryhmien-summarivit]

  )

(defn muodosta-hintaryhmakohtaiset-summarivit
  [urakan-hinnoittelurivit]

  )

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


(defn hintaryhman-otsikko
  [hintaryhma]
  (case (keyword hintaryhma)
    :sopimushintainen-tyo-tai-materiaali "Sopimushintaiset työt ja materiaalit"
    :omakustanteinen-tyo-tai-materiaali "Omakustannushintaiset työt ja materiaalit"
    :muu-tyo "Muut työt (ei indeksilaskesntaa)"
    :varaosat-ja-materiaalit "Varaosat ja materiaalit"
    :muut-kulut "Muut"
    "Hintaryhmän nimi puuttuu"))



(defn summarivit-tehtavat
  [konteksti muutos-ja-lisatyot]
  (log/debug (println-str "muutos-ja-lisatyot " muutos-ja-lisatyot))
  (case (keyword konteksti)
    :muutos-ja-lisatyot-koko-maa
    (mapv #(rivi
             (hintaryhman-otsikko (:hinnoittelu_ryhma %))
             (or (:summa %) ei-raha-summaa-info-solu)
             (or 0 indeksi-puuttuu-info-solu))              ;; TODO
          muutos-ja-lisatyot)
    :muutos-ja-lisatyot
    (mapv #(rivi
             (pvm/pvm (:pvm %))
             (:tehtava %)
             (:kohde %)
             (:kohteenosa %)
             (:huoltokohde %)
             (str (or (:otsikko %) " ") (or (:lisatieto %) " "))
             (hintaryhman-otsikko (:hinnoittelu_ryhma %))
             (or (:summa %) ei-raha-summaa-info-solu)
             (or 0 indeksi-puuttuu-info-solu))              ;; TODO
          muutos-ja-lisatyot)))



;(defn kustannustyyppikohtaiset-rivit
;  [muutos-ja-lisatyot-ryhmiteltyna]
;  (mapv #(rivi
;           (hinnoitteluryhman-nimi (:hinnoittelu_ryhma %))
;           (or (get-in % [:tehtava :summa]) ei-raha-summaa-info-solu)
;           (or (:korotus %) indeksi-puuttuu-info-solu))
;        (sort-by tyypin-sort-avain muutos-ja-lisatyot-ryhmiteltyna)))
;

;(defn- summarivi [tietorivit tyyppi]
;  (let [kaikki-suunnitellut (kentan-summa tietorivit :suunniteltu-maara)
;        kaikki-toteutuneet (kentan-summa tietorivit :toteutunut-maara)]
;    ["Yhteensä" (if (sopimus-tai-yksikkohintainen? tyyppi)
;                  kaikki-suunnitellut
;                  "")
;     kaikki-toteutuneet
;     (if (sopimus-tai-yksikkohintainen? tyyppi)
;       (- kaikki-suunnitellut
;          kaikki-toteutuneet)
;       "")]))
;

;(defn- kulutyypin-rivit [tietorivit tyyppi]
;  (conj
;    (tpi-kohtaiset-rivit tietorivit tyyppi)
;    (summarivi tietorivit tyyppi)))
;
(defn- taulukko-urakka [otsikko data]
  (conj
    [:taulukko {:otsikko otsikko
                :tyhja (when (empty? data) "Ei raportoitavaa.")
                :sheet-nimi otsikko
                :viimeinen-rivi-yhteenveto? false}
     (sarakkeet :muutos-ja-lisatyot)
     ;(summarivit-tehtavat tyyppi data)
     ])
  )


(defn- taulukko-koko-maa [otsikko tyot-ryhmiteltyna]

  ;;(def *u (distinct (map #(get % :urakka) (keys *mlr))))

  (log/debug (println-str "urakan TYOT "   (urakat tyot-ryhmiteltyna)
                          ))

  (map #(println %)
         ;(log/debug (println-str "URAKAT " %))
         ;; Urakan nimi
         ;; Laske kunkin hintaryhmän summa (kaikki-yhteensa)
         ;; Kunkin hintaryhmän nimi ja summa

       (urakat tyot-ryhmiteltyna))


  ;(let [ei-hyva (map #(log/debug (println-str "NÄYTÄ URAKKA " %)) muutos-ja-lisatyot-ryhmiteltyna)])

  (conj
    [:taulukko {:otsikko otsikko
                :tyhja (when (empty? tyot-ryhmiteltyna) "Ei raportoitavaa.") ;;TODO tämä on turha täällä, laita se vaan suorita-funktioon
                :sheet-nimi otsikko
                :viimeinen-rivi-yhteenveto? false}
     (sarakkeet :muutos-ja-lisatyot-koko-maa)


     ;(summarivit-tehtavat :muutos-ja-lisatyot-koko-maa tyot-ryhmiteltyna)
     ])
  )


(defn- summa-rivit-yhteensa [muutos-ja-lisatyot]
  (let [yhteensa-rivi ["Yhteensä"
                       ""
                       (reduce + 0 (keep :summa muutos-ja-lisatyot))
                       ""]]
    yhteensa-rivi))


(defn- kaikki-yhteensa [muutos-ja-lisatyot]
  (log/debug (println-str "Laske summa " muutos-ja-lisatyot))
  ["" "" "" "" "" "" "" (reduce + 0 (keep :summa muutos-ja-lisatyot))])


;
;(defn- summat-ryhmiteltyna [kaikki-muutos-ja-lisatyo-rivit ryhmittelyperuste]
;  (let [rivit-ryhmiteltyna (group-by ryhmittelyperuste kaikki-muutos-ja-lisatyo-rivit)
;        summarivit (conj
;                     (into []
;                           (concat
;                             (for [ryhman-rivit rivit-ryhmiteltyna]
;                               [(key ryhman-rivit)
;                                ""
;                                #((conj (reduce + 0 (keep :summa (val ryhman-rivit)))
;                                        (taulukko (key ryhman-rivit) :muutos-ja-lisatyot ryhman-rivit)
;                                        (log/debug (println-str "" (taulukko (key ryhman-rivit) :muutos-ja-lisatyot ryhman-rivit)))))
;                                ""]
;                               )))
;
;                     ["Yhteensä:"
;                      ""
;                      (reduce + 0 (keep :summa kaikki-muutos-ja-lisatyo-rivit))
;                      ""])]
;    summarivit))
;
;
;
;
;(defn summarivit
;  [konteksti-ja-ryhmittely tyot]
;  (case (keyword konteksti-ja-ryhmittely)
;    :muutos-ja-lisatyot-koko-maa-urakoittain
;    (conj
;      (summat-ryhmiteltyna tyot :urakka)
;      (summarivi-kaikki-yhteensa tyot))
;    :muutos-ja-lisatyot-koko-maa-tehtavittain
;    (conj
;      (summat-ryhmiteltyna tyot :tehtava)
;      (summarivi-kaikki-yhteensa tyot))
;    :muutos-ja-lisatyot-koko-maa-hintaryhmittain
;    (conj
;      (summat-ryhmiteltyna tyot :hintaryhma)
;      (summarivi-kaikki-yhteensa tyot))
;    :muutos-ja-lisatyot
;    (summarivi-kaikki-yhteensa tyot)
;    :muutos-ja-lisatyot-tehtavittain
;    (conj
;      (summat-ryhmiteltyna tyot :tehtava)
;      (summarivi-kaikki-yhteensa tyot))
;    :muutos-ja-lisatyot-hintaryhmittain
;    (conj
;      (summat-ryhmiteltyna tyot :hintaryhma)
;      (summarivi-kaikki-yhteensa tyot))
;    :muutos-ja-lisatyot-kohteittain
;    (conj
;      (summat-ryhmiteltyna tyot :kohde)
;      (summarivi-kaikki-yhteensa tyot))))




;; TODO: kohde rajattu + tehtävä rajattu, kokomaan tehtävällä rajatut jne. tarkista kombinaatiot
;; TODo: oikeustarkistus?

(defn hae-kanavien-muutos-ja-lisatyot-raportille
  [db {:keys [alkupvm loppupvm urakka-id kohde-id tehtava-id] :as parametrit} rajaus]
  (let [hakuparametrit {:urakkaid urakka-id
                        :alkupvm (konv/sql-date alkupvm)
                        :loppupvm (konv/sql-date loppupvm)
                        :kohdeid kohde-id
                        :tehtavaid tehtava-id}]

    (log/debug (println-str "Haetaan rajauksella " rajaus))
    (log/debug (println-str "Hakuparametrit " hakuparametrit))
    (case rajaus
      :urakka-kohde-ja-tehtava
      (hae-kanavien-kohde-ja-tehtavakohtaiset-muutos-ja-lisatyot-raportille db hakuparametrit)
      :urakka-ja-tehtava
      (hae-kanavien-tehtavakohtaiset-muutos-ja-lisatyot db hakuparametrit)
      :urakka-ja-kohde
      (hae-kanavien-kohdekohtaiset-muutos-ja-lisatyot db hakuparametrit)
      :urakka
      (hae-kanavien-urakkakohtaiset-muutos-ja-lisatyot db hakuparametrit)
      :ei-rajausta
      (hae-kanavien-muutos-ja-lisatyot db hakuparametrit)
      )))

(defn rajaus
  [urakka-id kohde-id tehtava-id]
  (if (and urakka-id tehtava-id kohde-id)
    :urakka-kohde-ja-tehtava
    (if (and urakka-id tehtava-id)
      :urakka-ja-tehtava
      (if (and urakka-id kohde-id)
        :urakka-ja-kohde
        (if urakka-id
          :urakka
          :ei-rajausta)))))


(defn taulukko-urakoittain-hintaryhmat [muutos-ja-lisatyot-ryhmiteltyna]

  )


(defn ryhmittele-tyot [rajaus muutos-ja-lisatyot]

  ;; (group-by #(select-keys % [:urakka :hinnoittelu_ryhma]) *ml)

  (case (keyword rajaus)
    :urakka


    :ei-rajausta

    (let [muutos-ja-lisatyot-urakoittain (group-by #(select-keys % [:urakka :hinnoittelu_ryhma]) muutos-ja-lisatyot)]

      muutos-ja-lisatyot-urakoittain
      ;;taulukko-hintaryhmakohtaiset-summarivit
      ;; Maarit. Doseq palauttaa tässä
      ;;["sopimushintainen-tyo-tai-materiaali" [{:materiaali_id nil, :pvm #inst "2018-03-26T21:00:00.000-00:00", :kohde_id 3,

      )))





(defn suorita
  [db user {:keys [alkupvm loppupvm urakka-id kohde-id tehtava-id] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        :default :koko-maa)
        rajaus (rajaus urakka-id kohde-id tehtava-id)
        raportin-kontekstin-nimi (case konteksti
                                   :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                                   :koko-maa "KOKO MAA")
        raportin-nimi "Muutos- ja lisätöiden raportti"
        raportin-otsikko (raportin-otsikko raportin-kontekstin-nimi raportin-nimi alkupvm loppupvm)

        ;; TODO: tarkempien raporttien rajaavat tiedot myös otsikkoon: kohde, tehtava
        kohde-nimi (if kohde-id
                     (:nimi (first (hae-kanavakohteen-nimi db {:kohdeid kohde-id})))
                     "Kaikki kohteet")
        tehtava-nimi (if tehtava-id
                       (:nimi (first (hae-kanavatoimenpiteen-nimi db {:tehtavaid tehtava-id}))
                         "Kaikki toimenpiteet"))


        muutos-ja-lisatyot (hae-kanavien-muutos-ja-lisatyot-raportille db parametrit rajaus)

        ]



    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     (conj (when (not-empty muutos-ja-lisatyot)
             (if (= (keyword konteksti) :urakka)
               (taulukko-urakka raportin-otsikko
                                muutos-ja-lisatyot)
               (taulukko-koko-maa raportin-otsikko
                                  (ryhmittele-tyot rajaus muutos-ja-lisatyot))))



           [:taulukko {:otsikko "Kaikki yhteensä"
                       :tyhja (when (empty? muutos-ja-lisatyot) "Ei raportoitavaa.")
                       :sheet-nimi "Yhteensä"
                       :viimeinen-rivi-yhteenveto? false}
            (sarakkeet :yhteenveto)
            (kaikki-yhteensa muutos-ja-lisatyot)
            ;(summarivi-kaikki-yhteensa muutos-ja-lisatyot)
            ])]))







;;sarakkeet
;;toimenpiderivit



;; Konteksti: koko maa
;; Ryhmittelys: urakka
;; Järjestys: tehtävä
;; Summa: hintaryhmä
;; Yhteissumma: hintaryhmien summat yhteensä


;; Konteksti: urakka
;; Ryhmittely: -
;; Rajaus: kohde
;; Järjestys;

;; Konteksti: urakka
;; Ryhmittely: -
;; Järjestys: pvm
;; Summa: yksittäinen tehtävä
;; Yhteissumma: urakan yksittäisten tehtävien summat yhteensä

