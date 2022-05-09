(ns harja.palvelin.raportointi.raportit.ymparisto
  (:require [harja.domain.materiaali :as materiaalidomain]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.kyselyt
             [hallintayksikot :as hallintayksikot-q]
             [lampotilat :as suolasakko-q]
             [konversio :as konv]
             [urakat :as urakat-q]]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko] :as yleinen]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]))

(defqueries "harja/palvelin/raportointi/raportit/ymparisto.sql"
  {:positional? true})

(defn- hae-raportin-tiedot
  [db parametrit]
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(update-in % [:kk]
                               (fn [pvm]
                                 (when pvm
                                   (yleinen/kk-ja-vv pvm))))))
        (hae-ymparistoraportti-tiedot db parametrit)))

(def materiaali-kaikki-talvisuola-yhteensa
  {:nimi "Talvisuolat yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "talvisuola"})

(def materiaali-kaikki-formiaatit-yhteensa
  {:nimi "Formiaatit yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "formiaatti"})

(def materiaali-kaikki-kesasuolat-yhteensa
  {:nimi "Kesäsuola yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "kesasuola"})

(def materiaali-kaikki-murskeet-yhteensa
  {:nimi "Murskeet yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "murske"})

(defn hae-raportti* [db hakuasetukset]
  (let [urakoittain? (:urakoittain? hakuasetukset)
        rivit (hae-raportin-tiedot db hakuasetukset)
        materiaali-rivit (hae-materiaalit db)
        urakat (into #{} (map :urakka rivit))
        materiaali-avaimet (if urakoittain?
                             [:materiaali :urakka]
                             [:materiaali])
        materiaalit (into {}
                          (for [m materiaali-rivit
                                u urakat]
                            (if urakoittain?
                              [{:materiaali m :urakka u} []]
                              [{:materiaali m} []])))]
    (sort-by (comp :nimi :materiaali first)
             (merge materiaalit
                    (group-by
                      #(select-keys % materiaali-avaimet)
                      rivit)))))


(defn hae-raportti [db alkupvm loppupvm urakka-id hallintayksikko-id
                    urakkatyyppi urakoittain?]
  (hae-raportti* db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :urakka urakka-id
                     :urakkatyyppi (some-> urakkatyyppi name)
                     :hallintayksikko hallintayksikko-id
                     :urakoittain? urakoittain?}))

(defn hae-raportti-urakoittain [db alkupvm loppupvm hallintayksikko-id
                                urakkatyyppi urakoittain?]
  (hae-raportti* db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :urakka nil
                     :urakkatyyppi (some-> urakkatyyppi name)
                     :hallintayksikko hallintayksikko-id
                     :urakoittain? urakoittain?}))

(defn- materiaalin-nimi [nimi]
  (if-not (= "Talvisuola" nimi)
    nimi
    ;; Osa käyttäjistä on sekoittanut Talvisuola nimen tarkoittavan kaikkea käytettyä
    ;; talvisuolaa. Tehdään siihen ero kertomalla että tämä on rakeista NaCl:ia
    "Talvisuola, NaCl"))

(defn- materiaalin-nimi-ja-selite [nimi]
  (case nimi
    "Talvisuola"
    {:arvo "Talvisuola, rakeinen"
     :selite "NaCl"}
    "Talvisuolaliuos CaCl2"
    {:arvo "Talvisuolaliuos"
     :selite "CaCl2"}
    "Talvisuolaliuos NaCl"
    {:arvo "Talvisuolaliuos"
     :selite "NaCl"}
    "Talvisuolat yhteensä"
    {:arvo "Talvisuolat yhteensä"
     :selite "100% kuivatonnia"}
    "Formiaatit yhteensä"
    {:arvo "Formiaatit yhteensä"
     :selite "50% kuivatonnia"}
    "Kesäsuola (pölynsidonta)"
    {:arvo "Kesäsuola"
     :selite "pölynsidonta"}
    "Kesäsuola (sorateiden kevätkunnostus)"
    {:arvo "Kesäsuola"
     :selite "sorateiden kevätkunnostus"}
    "Kesäsuola yhteensä"
    {:arvo "Kesäsuola yhteensä"
     :selite "100% kuivatonnia"}
    "Murskeet yhteensä"
    {:arvo "Murskeet yhteensä"
     :selite "tonnia"}

    ;; default
    {:arvo nimi}))


(defn- materiaalien-jarjestys-ymparistoraportilla
  [materiaalinimi]
  (if (= "Kaikki talvisuola yhteensä" materiaalinimi)
    7.5
    (materiaalidomain/materiaalien-jarjestys materiaalinimi)))

(defn- kk-rivit
  "Kk-rivit toteumille eli tummmennetut rivit - ei hoitoluokkakohtaiset"
  [rivit]
  (group-by :kk (filter (comp not :luokka) (remove #(nil? (:kk %)) rivit))))

(defn- kk-arvot
  "Funktio joka palauttaa kk-arvot raportin ymmärtämässä muodossa ja oikeassa järjestyksessä"
  [kk-rivit materiaali yksikot-soluissa?]
  (reduce-kv (fn [kk-arvot kk rivit]
                    (assoc kk-arvot kk
                                    (if yksikot-soluissa?
                                      [:arvo-ja-yksikko {:arvo (reduce + (keep :maara rivit))
                                                         :yksikko (:yksikko materiaali)
                                                         :desimaalien-maara 2}]
                                      (reduce + (keep :maara rivit)))))
                  {} kk-rivit))

(defn- yhteensa-arvo
  [arvot]
  (reduce + (remove nil? (if (every? number? arvot)
                           arvot
                           (map (comp :arvo second) arvot)))))

(defn- materiaalien-comparator
  "Järjestää materiaalit ensisijaisesti materiaalin nimen, toissijaisesti urakan nimen perusteella (if any)"
  [x y]
  (let [c (compare (materiaalien-jarjestys-ymparistoraportilla
                     (get-in (first y) [:materiaali :nimi]))
                   (materiaalien-jarjestys-ymparistoraportilla
                     (get-in (first x) [:materiaali :nimi])))]
    (if (not= c 0)
      c
      (let [c (compare (get-in (first y) [:urakka :nimi])
                       (get-in (first x) [:urakka :nimi]))]
        c))))

(defn- urakan-talvisuolan-maxmaara
  "Palauttaa Ympäristöraportille talvisuolan käyttörajarivin.

  Kun kk on nil, tulkitaan suunnittelutiedoksi eikä toteumaksi.
  Kun luokka on nil, tulkitaan toteumaksi, ei hoitoluokittaiseksi määräksi."
  [talvisuolan-maxmaarat urakka urakoittain?]
  (let [talvisuolan-maxmaara (if urakoittain?
                               (:talvisuolaraja (first (get talvisuolan-maxmaarat urakka)))
                               (reduce + 0 (keep :talvisuolaraja
                                                (apply concat (vals talvisuolan-maxmaarat)))))]
    {:maara talvisuolan-maxmaara
     :luokka nil :kk nil :urakka (when urakoittain? urakka)
     :materiaali materiaali-kaikki-talvisuola-yhteensa}))

(def isantarivi-indeksi (atom -1))

(defn koosta-taulukko [{:keys [otsikko konteksti kuukaudet urakoittain? osamateriaalit yksikot-soluissa?] :as taulukon-tiedot}]
  (let [
        ;; Avattavien rivien indeksit päätellään loopilla.
        ;; Jos rivillä on lapsia, lisätään sen indeksi listaan ja inkrementoidaan seuraavaa indeksiä lasten määrällä.
        
        ;; Osamateriaali-rivejä on niin monta, kuin taulukolla on normaaleja materiaalikohtaisia rivejä.
        ;; Niiden sisällä on materiaali- ja hoitoluokkakohtaisia rivejä. Erona niillä on :luokka-arvo.
        ;; :luokka-arvo kertoo rivin hoitoluokan.
        
        avattavat-rivit (mapv (partial str "raportti_rivi_")
                          (loop [idx 0
                                 o osamateriaalit
                                 res []]
                            (if (empty? o)
                              res
                              ;; Uniikkien :luokka-arvojen lasku kertoo, kuinka hoittoluokkakohtaista riviä on.
                              (let [nykyiset-lapset-cnt (count (into #{} (keep :luokka (second (first o)))))]
                                (recur
                                  ;; Lisätään hoitoluokkakohtaisten rivien määrä indeksiin.
                                  ;; Jos niitä ei ole, siirrytään seuraavaan materiaaliin
                                  ;; Molemmissa tapauksissa inkrementoidaan indeksiä yhdellä, kun siirrytään seuraavaan
                                  ;; materiaaliin.
                                  (+ (inc idx) nykyiset-lapset-cnt)
                                  (rest o)
                                  (if (= 0 nykyiset-lapset-cnt)
                                    res
                                    ;; Ja jos materiaalilla on hoitoluokkakohtaisia rivejä, lisätään se avattavat-rivit-
                                    ;; vektoriin. Tämä on vektori, koska myöhemmin halutaan hakea siitä indeksin
                                    ;; perusteella tavaraa, esim. (get avattavat-rivit 0). 
                                    (conj res idx)))))))

        ;; Jokaisella taulukolla on omat isäntärivinsä. Eli rivit, joilla voi olla olla avattavia rivejä
        _ (reset! isantarivi-indeksi -1)]
   [:taulukko {:otsikko otsikko
               :oikealle-tasattavat-kentat (into #{} (range 2 (+ 5 (count kuukaudet))))
               :esta-tiivis-grid? true
               :sivuttain-rullattava? true
               :ensimmainen-sarake-sticky? true
               :samalle-sheetille? true
               ;; Tässä muutetaan vektori setiksi, koska se on kätevä gridissä. Ehkä voi muuttaa?
               :avattavat-rivit (into #{} avattavat-rivit)}
    (into []
      ;; Muodostetaan skeema taulukolle
      (concat
        [{:leveys "2%" :tyyppi :avattava-rivi}]
        (when urakoittain?
          [{:otsikko "Urakka" :leveys "10%"}])

        ;; Materiaalin nimi
        [{:otsikko "Materiaali" :leveys (if urakoittain? "10%" "14%")}]
        ;; Kaikki kuukaudet
        (map (fn [kk]
               {:otsikko kk
                :leveys (if urakoittain? "4.83%" "5%")
                :fmt :numero}) kuukaudet)

        [{:otsikko (str "Yhteensä" (when-not yksikot-soluissa? " (t)")) :leveys (if urakoittain? "7%" "8%") :fmt :numero :jos-tyhja "-"
          :excel [:summa-vasen (if urakoittain? 2 1)]}
         {:otsikko "Suunniteltu (t)"
          :leveys (if urakoittain? "7%" "8%") :fmt :numero :jos-tyhja "-"}
         {:otsikko "Tot-%" :leveys (if urakoittain? "6%" "7%") :fmt :prosentti :jos-tyhja "-"}]))

    (mapcat
      (fn [[{:keys [urakka materiaali]} rivit]]
        (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
              suunniteltu (when-not (empty? suunnitellut)
                            (reduce + suunnitellut))
              luokitellut (filter :luokka rivit)
              ;; Jos materiaalilla on hoitoluokkakohtaisia rivejä, nostetaan isantarivin-indeksiä yhdellä
              ;; koska materiaalit, joilla ei ole niitä, ei löydy myöskään avattavat-rivit-vektorista.
              _ (when (seq luokitellut) (reset! isantarivi-indeksi (+ @isantarivi-indeksi 1)))
              kk-arvot (kk-arvot (kk-rivit rivit) materiaali yksikot-soluissa?)
              yhteenvetorivi? (:yht-rivi materiaali)
              yhteensa-kentta (fn [arvot nayta-aina?]
                                (let [yht (yhteensa-arvo arvot)]
                                  (when (or (> yht 0) nayta-aina?)
                                    (if yksikot-soluissa?
                                      [:arvo-ja-yksikko-korostettu {:arvo yht
                                                                    :korosta-hennosti? true
                                                                    :yksikko (:yksikko materiaali)
                                                                    :desimaalien-maara 2}]
                                      [:arvo-ja-yksikko-korostettu {:arvo yht
                                                                    :korosta-hennosti? true
                                                                    :desimaalien-maara 2}]))))
              toteuma-prosentti (when (and kk-arvot (not (zero? (or suunniteltu 0))))
                                  (/ (* 100.0 (yhteensa-arvo (vals kk-arvot))) suunniteltu))]
          (concat
            ;; Normaali materiaalikohtainen rivi
            [(merge
               (when yhteenvetorivi?
                 {:korosta-hennosti? true
                  :lihavoi? true})
              {:lihavoi? yhteenvetorivi?
               :rivi (into []
                       (concat

                         ;; Vetolaatikkojuttuja
                         [" "]

                         ;; Urakan nimi, jos urakoittain jaottelu päällä
                         (when urakoittain?
                           [(:nimi urakka)])

                         ;; Materiaalin nimi
                         [[:arvo-ja-selite (materiaalin-nimi-ja-selite (:nimi materiaali))]]

                         ;; Kuukausittaiset määrät, viiva jos tyhjä.
                         (map #(or (kk-arvot %) "–") kuukaudet)

                         ;; Yhteensä, toteumaprosentti ja suunniteltumäärä
                         [(yhteensa-kentta (vals kk-arvot) true)
                          [:arvo-ja-yksikko-korostettu {:arvo suunniteltu
                                                        :yksikko (when suunniteltu (:yksikko materiaali))
                                                        :desimaalien-maara 2
                                                        :korosta-hennosti? false}]
                          [:arvo-ja-yksikko-korostettu {:arvo toteuma-prosentti
                                                        :yksikko (when suunniteltu "%")
                                                        :desimaalien-maara 2
                                                        :varoitus? (< 100 (or toteuma-prosentti 0))
                                                        :korosta-hennosti? false}]]))})]

             ;; Mahdolliset hoitoluokkakohtaiset rivit
             (mapv (fn [[luokka rivit]]
                     (let [rivit (if (or urakoittain? (= konteksti :urakka))
                                   rivit
                                   ;; Jos ei eritellä urakoittain, on laskettava eri urakoiden määrät yhteen
                                   (map
                                     #(assoc (first (val %)) :maara (reduce + 0 (keep :maara (val %))))
                                     (group-by :kk rivit)))
                           kk-arvot (into {}
                                      (map (juxt :kk #(if yksikot-soluissa?
                                                        [:arvo-ja-yksikko {:arvo (:maara %)
                                                                           :yksikko (:yksikko materiaali)
                                                                           :desimaalien-maara 2}]
                                                        (:maara %))))
                                      rivit)]
                       {:lihavoi? false
                        ;; Ja täällä haetaan isanta-rivin-id avattavat-rivit-vektorista isäntärivin indeksillä.
                        :isanta-rivin-id (nth avattavat-rivit @isantarivi-indeksi)
                        :rivi (into []
                                (concat
                                  [" "]
                                  (when urakoittain?
                                    [(:nimi urakka)])
                                  [(hoitoluokat/talvihoitoluokan-nimi luokka)]

                                  ;; Hoitoluokkakohtaiselle riville myös viiva jos ei arvoa.
                                  (map #(or (kk-arvot %) "–") kuukaudet)

                                  [(yhteensa-kentta (vals kk-arvot) true)
                                   nil nil]))}))
               (sort-by first (group-by :luokka luokitellut))))))
       osamateriaalit)]))

(defn summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan [urakoittain? materiaalit-kannasta materiaalityyppi]
  (let [ryhmittely-fn (if urakoittain? (juxt :kk :urakka) :kk)
        materiaalit (apply concat (map second materiaalit-kannasta))
        valitut-materiaalit (filter
                              (fn [rivi]
                                (and
                                  ;; summataan vain toteumat eli kun luokka on nil
                                  (nil? (:luokka rivi))
                                  (= (str materiaalityyppi) (str (get-in rivi [:materiaali :tyyppi])))))
                              materiaalit)]
    (group-by ryhmittely-fn valitut-materiaalit)))

(defn materiaalit-summattuna-ja-ryhmiteltyna [urakoittain? materiaalityyppi-ryhmiteltyna]
  (map (fn [[ryhmittelyavain rivit]]
         {(if urakoittain?
            ;; [kk urakka]
            [(first ryhmittelyavain) (second ryhmittelyavain)]
            ;; kk
            [ryhmittelyavain])
          (assoc (first rivit) :maara (reduce + (keep :maara rivit))
                               :materiaali materiaali-kaikki-talvisuola-yhteensa
                               :urakka (if urakoittain?
                                         (:urakka (first rivit))
                                         nil))})
    materiaalityyppi-ryhmiteltyna))

(defn koosta-yhteensa-rivi [tyypin-mukaan-jaotellut-materiaalit tyypin-yhteensa-materiaali]
  (if-not (empty? tyypin-mukaan-jaotellut-materiaalit)
    (mapv (fn [[urakka rivit]]
            [{:materiaali tyypin-yhteensa-materiaali
              :urakka urakka}
             rivit])
      tyypin-mukaan-jaotellut-materiaalit)
    (list [{:maara 0
            :luokka nil :kk nil :urakka nil
            :materiaali tyypin-yhteensa-materiaali}])))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakoittain? urakkatyyppi] :as parametrit}]
  (let [urakoittain? (if urakka-id false urakoittain?)
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        materiaalit-kannasta (if urakoittain?
                      (hae-raportti-urakoittain db alkupvm loppupvm hallintayksikko-id urakkatyyppi urakoittain?)
                      (hae-raportti db alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi urakoittain?))
        raportin-nimi "Ympäristöraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        talvisuola-toteumat-yhteensa-ryhmiteltyna (summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan urakoittain?
                                                  materiaalit-kannasta "talvisuola")
        formiaatti-toteumat-yhteensa-ryhmiteltyna (summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan urakoittain?
                                                  materiaalit-kannasta "formiaatti")
        kesasuola-toteumat-yhteensa-ryhmiteltyna (summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan urakoittain?
                                                    materiaalit-kannasta "kesasuola")
        murske-toteumat-yhteensa-ryhmiteltyna (summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan urakoittain?
                                                   materiaalit-kannasta "murske")

        talvisuola-toteumat-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? talvisuola-toteumat-yhteensa-ryhmiteltyna)
        kaikki-formiaatit-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? formiaatti-toteumat-yhteensa-ryhmiteltyna)
        kaikki-kesasuolat-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? kesasuola-toteumat-yhteensa-ryhmiteltyna)
        kaikki-murskeet-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? murske-toteumat-yhteensa-ryhmiteltyna)
        talvisuola-toteumat-ryhmiteltyna-ja-summattuna
        (group-by :urakka
                  (apply concat (map vals talvisuola-toteumat-ryhmiteltyna-ja-summattuna)))
        talvisuolatoteumat (mapv (fn [[urakka rivit]]
                                   [{:materiaali materiaali-kaikki-talvisuola-yhteensa
                                     :urakka urakka}
                                    rivit])
                             talvisuola-toteumat-ryhmiteltyna-ja-summattuna)

        kaikki-formiaatit-yhteensa-ryhmiteltyna-ja-summattuna
        (group-by :urakka
          (apply concat (map vals kaikki-formiaatit-yhteensa-ryhmiteltyna-ja-summattuna)))
        formiaatit-yhteensa-rivi (koosta-yhteensa-rivi
                                   kaikki-formiaatit-yhteensa-ryhmiteltyna-ja-summattuna
                                   materiaali-kaikki-formiaatit-yhteensa)

        kaikki-kesasuolat-yhteensa-ryhmiteltyna-ja-summattuna
        (group-by :urakka
          (apply concat (map vals kaikki-kesasuolat-yhteensa-ryhmiteltyna-ja-summattuna)))
        kesasuola-yhteensa-rivi (koosta-yhteensa-rivi
                                  kaikki-kesasuolat-yhteensa-ryhmiteltyna-ja-summattuna
                                  materiaali-kaikki-kesasuolat-yhteensa)
        kaikki-murskeet-yhteensa-ryhmiteltyna-ja-summattuna (group-by :urakka
                                                              (apply concat (map vals kaikki-murskeet-yhteensa-ryhmiteltyna-ja-summattuna)))
        murske-yhteensa-rivi (koosta-yhteensa-rivi
                               kaikki-murskeet-yhteensa-ryhmiteltyna-ja-summattuna
                               materiaali-kaikki-murskeet-yhteensa)

        kontekstin-urakka-idt (set (keep #(get-in % [:urakka :id]) (apply concat (vals materiaalit-kannasta))))

        ;; Haetaan tietokannasta kontekstin urakoiden talvisuolojen käyttörajat
        hoitokauden-alkuvuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm)))
        talvisuolan-maxmaarat (group-by :urakka
                                        (map
                                          konv/alaviiva->rakenne
                                          (suolasakko-q/hae-urakoiden-talvisuolarajat
                                            db
                                            {:urakka_idt kontekstin-urakka-idt
                                             :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})))


        ;; Lisätään suolasummiin talvisuolojen käyttörajat
        talvisuolat-yhteensa-rivi (if-not (empty? talvisuolatoteumat)
                                    (map (fn [[{materiaali :materiaali urakka :urakka :as avain} rivit]]
                                           [avain (conj rivit (urakan-talvisuolan-maxmaara talvisuolan-maxmaarat
                                                                urakka
                                                                urakoittain?))])
                                      talvisuolatoteumat)
                                    (list [{:maara 0
                                            :luokka nil :kk nil :urakka nil
                                            :materiaali materiaali-kaikki-talvisuola-yhteensa}]))

        materiaalit (sort #(materiaalien-comparator %2 %1) (concat materiaalit-kannasta talvisuolat-yhteensa-rivi formiaatit-yhteensa-rivi kesasuola-yhteensa-rivi murske-yhteensa-rivi))

        talvisuolan-toteutunut-maara (some->> materiaalit
                                              (filter (fn [[materiaali _]]
                                                        (and
                                                          (not= true (get-in materiaali [:materiaali :yht-rivi]))
                                                          (= "talvisuola" (get-in materiaali [:materiaali :tyyppi]))))) ;; vain talvisuolat
                                              (mapcat second)
                                              (filter #(nil? (:luokka %))) ;; luokka on nil toteumariveillä (lihavoidut raportissa)
                                              (map :maara)
                                              (reduce +))
        kuukaudet (yleinen/kuukaudet alkupvm loppupvm yleinen/kk-ja-vv-fmt)
        materiaalit-tyypin-mukaan (fn [materiaalityyppi]
                                    (keep (fn [rivi]
                                            (when (= materiaalityyppi (get-in (first rivi) [:materiaali :tyyppi]))
                                              rivi))
                                      materiaalit))
        taulukon-tiedot {:otsikko nil
                         :osamateriaalit nil
                         :konteksti konteksti
                         :kuukaudet kuukaudet
                         :urakoittain? urakoittain?
                         :yksikot-soluissa? false}]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:teksti otsikko]
     (when-not (empty? materiaalit)
       [:teksti (str "Kokonaisarvot ovat tarkkoja toteumamääriä, hoitoluokittainen jaottelu perustuu reittitietoon ja voi sisältää epätarkkuutta.")])
     [:teksti (str yleinen/materiaalitoteumien-paivitysinfo)]

     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Talvisuolat")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "talvisuola"))))
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Formiaatit")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "formiaatti"))))
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Kesäsuola")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "kesasuola"))))
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Hiekoitushiekka")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "hiekoitushiekka"))))
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Murskeet")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "murske"))))
     ;; TODO: Piilota kaksi viimeistä saraketta
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Muut materiaalit")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "muu"))
                        (assoc :yksikot-soluissa? true)))]))
