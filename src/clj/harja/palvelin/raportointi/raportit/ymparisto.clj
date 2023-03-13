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

(def talvisuola-yht-rivi-materiaali "Talvisuolat yhteensä")
(def materiaali-kaikki-talvisuola-yhteensa
  {:id 999
   :nimi talvisuola-yht-rivi-materiaali
   :yksikko "t"
   :yht-rivi true
   :tyyppi "talvisuola"})

(def materiaali-kaikki-formiaatit-yhteensa
  {:id 999
   :nimi "Formiaatit yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "formiaatti"})

(def materiaali-kaikki-kesasuolat-yhteensa
  {:id 999
   :nimi "Kesäsuola yhteensä"
   :yksikko "t"
   :yht-rivi true
   :tyyppi "kesasuola"})

(def materiaali-kaikki-murskeet-yhteensa
  {:id 999
   :nimi "Murskeet yhteensä"
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
    "Talvisuola, rakeinen NaCl" {:arvo "Talvisuola, rakeinen" :selite "NaCl"}
    "Talvisuolaliuos CaCl2" {:arvo "Talvisuolaliuos" :selite "CaCl2"}
    "Talvisuolaliuos NaCl" {:arvo "Talvisuolaliuos" :selite "NaCl"}
    "Talvisuolat yhteensä" {:arvo "Talvisuolat yhteensä" :selite "100% kuivatonnia"}
    "Formiaatit yhteensä" {:arvo "Formiaatit yhteensä" :selite "50% kuivatonnia"}
    "Kesäsuola sorateiden pölynsidonta" {:arvo "Kesäsuola" :selite "sorateiden pölynsidonta"}
    "Kesäsuola sorateiden kevätkunnostus" {:arvo "Kesäsuola" :selite "sorateiden kevätkunnostus"}
    "Kesäsuola päällystettyjen teiden pölynsidonta" {:arvo "Kesäsuola" :selite "päällystettyjen teiden pölynsidonta"}
    "Kesäsuola yhteensä" {:arvo "Kesäsuola yhteensä" :selite "100% kuivatonnia"}
    "Murskeet yhteensä" {:arvo "Murskeet yhteensä" :selite "tonnia"}

    ;; default
    {:arvo nimi}))

(defn- kk-rivit
  "Kk-rivit toteumille eli tummmennetut rivit - ei hoitoluokkakohtaiset"
  [rivit]
  (group-by :kk (filter
                  #(and (not (:talvitieluokka %)) (not (:soratieluokka %)))
                  (remove #(nil? (:kk %)) rivit))))

(defn- kk-arvot
  "Funktio joka palauttaa kk-arvot raportin ymmärtämässä muodossa ja oikeassa järjestyksessä"
  [kk-rivit materiaali yksikot-soluissa?]
  (reduce-kv (fn [kk-arvot kk rivit]
                    (assoc kk-arvot kk
                                    (if yksikot-soluissa?
                                      [:arvo-ja-yksikko {:arvo (reduce + (keep :maara rivit))
                                                         :yksikko (:yksikko materiaali)
                                                         :desimaalien-maara 2
                                                         :ryhmitelty? true}]
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
  (let [c (compare (materiaalidomain/materiaalien-jarjestys
                     (get-in (first y) [:materiaali :nimi]))
                   (materiaalidomain/materiaalien-jarjestys
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
     :talvitieluokka nil :kk nil :urakka (when urakoittain? urakka)
     :materiaali materiaali-kaikki-talvisuola-yhteensa}))

(def poikkeama-info
  [:p [:b "Poikkeamalla "] "tarkoitetaan hoitoluokille kohdistettujen materiaalimäärien ja urakoitsijan 
ilmoittaman kokonaismateriaalimäärän erotusta.\n\nPoikkeamaa on yleensä aina jonkin verran, sillä
reittitiedot ja kokonaismateriaalimäärät raportoidaan eri tavalla."])

(def hoitoluokka-puuttuu-info
  [:p [:b "Hoitoluokka puuttuu:\n\n"] "Materiaalimäärä, johon sidottuja reittipisteitä ei ole voitu kohdistaa millekään talvihoitoluokitetulle tielle"])

;; Prosentti, jonka yli menevät poikkeamat korostetaan punaisella.
(def poikkeama-varoitus-raja 5)

(defn- hoitoluokalle-nimi [luokka otsikko]
  (if (or (= "Talvisuolat" otsikko)
        (= "Formiaatit" otsikko))
    (hoitoluokat/talvihoitoluokan-nimi luokka)
    (hoitoluokat/soratieluokan-nimi luokka)))

(defn koosta-taulukko [{:keys [otsikko konteksti kuukaudet urakoittain? osamateriaalit yksikot-soluissa? nayta-suunnittelu?] :as taulukon-tiedot}]
  (let [isantarivi-indeksi (atom -1)
        ;; Avattavien rivien indeksit päätellään loopilla.
        ;; Jos rivillä on lapsia, lisätään sen indeksi listaan ja inkrementoidaan seuraavaa indeksiä lasten määrällä.

        ;; Osamateriaali-rivejä on niin monta, kuin taulukolla on normaaleja materiaalikohtaisia rivejä.
        ;; Niiden sisällä on materiaali- ja hoitoluokkakohtaisia rivejä. Erona niillä on :talvitieluokka -arvo ja :soratieluokka -arvo.
        ;; :talvitieluokka/:soratieluokka -arvo kertoo rivin hoitoluokan.
        avattavat-rivit
        (mapv (partial str "raportti_rivi_")
          (loop [idx 0
                 o osamateriaalit
                 res []]
            (if (empty? o)
              res
              ;; Uniikkien :talvitieluokka/:soratieluokka -arvojen lasku kertoo, kuinka monta hoitoluokkakohtaista riviä on.
              (let [materiaali-rivit (second (first o))
                    nykyiset-lapset-cnt (count (into #{}
                                                 (keep
                                                   #(or (:talvitieluokka %) (:soratieluokka %))
                                                   materiaali-rivit)))
                    materiaali (:materiaali (ffirst o))
                    ;; Näytetään poikkeamarivi, talvisuolalle ja formiaatille vaikka ei olisi hoitoluokkakohtaisia rivejä
                    ;; kunhan materiaalilla on yhtään toteumaa, kunhan ei olla yhteenvetorivillä.
                    avattava-rivi? (and
                                     (not (:yht-rivi materiaali))
                                     (seq materiaali-rivit)
                                     (or (not (zero? nykyiset-lapset-cnt))
                                          (#{"talvisuola" "formiaatti"} (:tyyppi materiaali))))]
                (recur
                  ;; Lisätään hoitoluokkakohtaisten rivien määrä indeksiin ja poikkeamarivin takia yksi lisärivi
                  ;; Jos niitä ei ole, siirrytään seuraavaan materiaaliin
                  ;; Molemmissa tapauksissa inkrementoidaan indeksiä yhdellä, kun siirrytään seuraavaan
                  ;; materiaaliin.
                  (+ (inc idx)
                    (if avattava-rivi?
                      (inc nykyiset-lapset-cnt)
                      0))
                  (rest o)
                  (if avattava-rivi?
                    ;; jos rivillä on avattavia rivejä, lisätään se avattavat-rivit-vektoriin.
                    ;; Tämä on vektori, koska myöhemmin halutaan hakea siitä indeksin
                    ;; perusteella tavaraa, esim. (get avattavat-rivit 0).
                    (conj res idx)
                    res))))))

        ;; Jokaisella taulukolla on omat isäntärivinsä. Eli rivit, joilla voi olla olla avattavia rivejä
        _ (reset! isantarivi-indeksi -1)]
   [:taulukko {:otsikko otsikko
               :oikealle-tasattavat-kentat (into #{} (range (if urakoittain? 3 2) (+ (if nayta-suunnittelu? 5 3) (count kuukaudet))))
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
        (if nayta-suunnittelu?
          [{:otsikko (str "Yhteensä" (when-not yksikot-soluissa? " (t)")) :leveys (if urakoittain? "7%" "8%") :fmt :numero :jos-tyhja "-"}
           {:otsikko "Suunniteltu (t)" :leveys (if urakoittain? "7%" "8%") :fmt :numero :jos-tyhja "-"}
           {:otsikko "Tot-%" :leveys (if urakoittain? "6%" "7%") :fmt :prosentti :jos-tyhja "-"}]
          [{:otsikko (str "Yhteensä" (when-not yksikot-soluissa? " (t)")) :leveys (if urakoittain? "7%" "8%") :fmt :numero :jos-tyhja "-"}])))

    (mapcat
      (fn [[{:keys [urakka materiaali]} rivit]]
        (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
              suunniteltu (when-not (empty? suunnitellut)
                            (reduce + suunnitellut))
              luokitellut (filter #(or (:talvitieluokka %) (:soratieluokka %)) rivit)
              yhteenvetorivi? (:yht-rivi materiaali)
              talvisuola-tai-formiaatti? (#{"talvisuola" "formiaatti"} (:tyyppi materiaali))
              avattava? (and
                          (not yhteenvetorivi?)
                          (seq rivit)
                          (or (seq luokitellut)
                           talvisuola-tai-formiaatti?))
              ;; Jos materiaalilla on avattavia rivejä, nostetaan isantarivin-indeksiä yhdellä
              ;; koska materiaalit, joilla ei ole niitä, ei löydy myöskään avattavat-rivit-vektorista.
              _ (when avattava? (swap! isantarivi-indeksi inc))
              kk-rivit (kk-rivit rivit)
              kk-arvot (kk-arvot kk-rivit materiaali yksikot-soluissa?)
              kk-arvot-yht (yhteensa-arvo (vals kk-arvot))
              poikkeamat (into {} (map (fn [[kk materiaalit]]
                                         (let [materiaalit (filter #(or (:talvitieluokka %) (:soratieluokka %)) materiaalit)
                                               kk-arvo (apply + (map :maara (kk-rivit kk)))
                                               erotus (- (apply + (map :maara materiaalit)) kk-arvo)
                                               prosentti (if (zero? kk-arvo)
                                                           0
                                                           (with-precision 2 (* 100 (/ erotus kk-arvo))))]
                                           {kk [:erotus-ja-prosentti
                                                {:arvo erotus
                                                 :prosentti prosentti
                                                 :desimaalien-maara 2
                                                 :ryhmitelty? true
                                                 :varoitus? (< poikkeama-varoitus-raja (Math/abs (float prosentti)))}]}))
                                    (group-by :kk rivit)))
              yhteensa-kentta (fn [arvot nayta-aina?]
                                (let [yht (yhteensa-arvo arvot)]
                                  (when (or (> yht 0) nayta-aina?)
                                    (if yksikot-soluissa?
                                      [:arvo-ja-yksikko-korostettu {:arvo yht
                                                                    :korosta-hennosti? true
                                                                    :yksikko (:yksikko materiaali)
                                                                    :desimaalien-maara 2
                                                                    :ryhmitelty? true}]
                                      [:arvo-ja-yksikko-korostettu {:arvo yht
                                                                    :korosta-hennosti? true
                                                                    :desimaalien-maara 2
                                                                    :ryhmitelty? true}]))))
              toteuma-prosentti (when (and kk-arvot (not (zero? (or suunniteltu 0))))
                                  (/ (* 100.0 kk-arvot-yht) suunniteltu))]
          (concat
            ;; Normaali materiaalikohtainen rivi
            [(merge
               (when yhteenvetorivi?
                 {:korosta-hennosti? true
                  :lihavoi? true})
              {:lihavoi? yhteenvetorivi?
               :rivi (into []
                       (concat

                         ;; Avattaviin riveihin jätetään ensimmäinen kolumni tyhjäksi.
                         [" "]

                         ;; Urakan nimi, jos urakoittain jaottelu päällä
                         (when urakoittain?
                           [(:nimi urakka)])

                         ;; Materiaalin nimi
                         [[:arvo-ja-selite (materiaalin-nimi-ja-selite (:nimi materiaali))]]

                         ;; Kuukausittaiset määrät, viiva jos tyhjä.
                         (map #(or (kk-arvot %) "–") kuukaudet)

                          ;; Yhteensä, toteumaprosentti ja suunniteltumäärä
                          (if nayta-suunnittelu?
                            [(yhteensa-kentta (vals kk-arvot) true)
                             (if (nil? suunniteltu)
                               [:arvo-ja-yksikko-korostettu {:arvo nil
                                                             :yksikko nil
                                                             :desimaalien-maara 2
                                                             :lihavoi? false
                                                             :ala-korosta? true
                                                             :ryhmitelty? true}]
                               [:arvo-ja-yksikko-korostettu {:arvo suunniteltu
                                                             :yksikko (when suunniteltu (:yksikko materiaali))
                                                             :desimaalien-maara 2
                                                             :lihavoi? false
                                                             :ala-korosta? true
                                                             :ryhmitelty? true}])
                             (if (nil? toteuma-prosentti)
                               [:arvo-ja-yksikko-korostettu {:arvo nil
                                                             :yksikko nil
                                                             :desimaalien-maara 2
                                                             :varoitus? (< 100 (or toteuma-prosentti 0))
                                                             :ala-korosta? true
                                                             :ryhmitelty? true}]
                               [:arvo-ja-yksikko-korostettu {:arvo toteuma-prosentti
                                                             :yksikko (when suunniteltu "%")
                                                             :desimaalien-maara 2
                                                             :varoitus? (< 100 (or toteuma-prosentti 0))
                                                             :ala-korosta? true
                                                             :ryhmitelty? true}])]
                            [(yhteensa-kentta (vals kk-arvot) true)])))})]

            ;; Mahdolliset hoitoluokkakohtaiset rivit - Hoitoluokat ovat valmiina vain talvisuolalle ja formiaateille
            ;; Jätetään soratieluokat myöhempää aikaa varten
            (concat (mapv (fn [[luokka rivit]]
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
                                                                                  :desimaalien-maara 2
                                                                                  :ryhmitelty? true}]
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
                                         (if (= luokka 100)
                                           [[:teksti-ja-info {:arvo (hoitoluokalle-nimi luokka otsikko)
                                                              :info hoitoluokka-puuttuu-info}]]
                                           [(hoitoluokalle-nimi luokka otsikko)])

                                 ;; Hoitoluokkakohtaiselle riville myös viiva jos ei arvoa.
                                 (map #(or (kk-arvot %) "–") kuukaudet)

                                 (if nayta-suunnittelu?
                                   [(yhteensa-kentta (vals kk-arvot) true)
                                    nil nil]
                                   [(yhteensa-kentta (vals kk-arvot) true)])))}))
              (sort-by first (if talvisuola-tai-formiaatti?
                               (group-by :talvitieluokka luokitellut)
                               (group-by :soratieluokka luokitellut))))
              ;; Ja loppuun erotusrivi
              (when avattava?
                [{:lihavoi? false
                  :isanta-rivin-id (nth avattavat-rivit @isantarivi-indeksi)
                  :rivi (into []
                          (concat
                            [" "]
                            (when urakoittain?
                              [(:nimi urakka)])
                            [[:teksti-ja-info {:arvo "Poikkeama (+/-)"
                                               :info poikkeama-info}]]

                            ;; Hoitoluokkakohtaiselle riville myös viiva jos ei arvoa.
                            (map #(or (poikkeamat %) "–") kuukaudet)

                            (let [arvo (yhteensa-arvo (vals poikkeamat))]
                              (concat
                                [[:erotus-ja-prosentti
                                  {:arvo arvo
                                   :prosentti (if (zero? kk-arvot-yht)
                                                0
                                                (with-precision 2 (* 100 (/ arvo kk-arvot-yht))))
                                   :desimaalien-maara 2
                                   :korosta-hennosti? true
                                   :ryhmitelty? true}]]
                                (when nayta-suunnittelu? [nil nil])))))}])))))
     osamateriaalit)]))

(defn summaa-toteumat-ja-ryhmittele-materiaalityypin-mukaan [urakoittain? materiaalit-kannasta materiaalityyppi]
  (let [ryhmittely-fn (if urakoittain? (juxt :kk :urakka) :kk)
        materiaalit (apply concat (map second materiaalit-kannasta))
        valitut-materiaalit (filter
                              (fn [rivi]
                                (and
                                  ;; summataan vain toteumat eli kun luokka (viittaa talvihoitoluokkaan) ja soratieluokka on nil
                                  (and (nil? (:talvitieluokka rivi)) (nil? (:soratieluokka rivi)) )
                                  (= (str materiaalityyppi) (str (get-in rivi [:materiaali :tyyppi])))))
                              materiaalit)]
    (group-by ryhmittely-fn valitut-materiaalit)))

(defn materiaalit-summattuna-ja-ryhmiteltyna [urakoittain? materiaalityyppi-ryhmiteltyna yht-rivi-materiaali]
  (map (fn [[ryhmittelyavain rivit]]
         {(if urakoittain?
            ;; [kk urakka]
            [(first ryhmittelyavain) (second ryhmittelyavain)]
            ;; kk
            [ryhmittelyavain])
          (assoc (first rivit) :maara (reduce + (keep :maara rivit))
                               :materiaali yht-rivi-materiaali
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
            :talvitieluokka nil :soratieluokka nil :kk nil :urakka nil
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
                    :urakka (let [{nimi :nimi urakkanro :alueurakkanumero} (first (urakat-q/hae-urakka db urakka-id))]
                              (if urakkanro
                                (format "%s (%s)" nimi urakkanro)
                                nimi))
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
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? talvisuola-toteumat-yhteensa-ryhmiteltyna materiaali-kaikki-talvisuola-yhteensa)
        kaikki-formiaatit-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? formiaatti-toteumat-yhteensa-ryhmiteltyna materiaali-kaikki-formiaatit-yhteensa)
        kaikki-kesasuolat-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? kesasuola-toteumat-yhteensa-ryhmiteltyna materiaali-kaikki-kesasuolat-yhteensa)
        kaikki-murskeet-yhteensa-ryhmiteltyna-ja-summattuna
        (materiaalit-summattuna-ja-ryhmiteltyna urakoittain? murske-toteumat-yhteensa-ryhmiteltyna materiaali-kaikki-murskeet-yhteensa)

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
        talvisuolarajat (suolasakko-q/hae-urakoiden-talvisuolarajat db
                          {:urakka_idt kontekstin-urakka-idt
                           :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        talvisuolan-maxmaarat (group-by :urakka (map konv/alaviiva->rakenne talvisuolarajat))
        talvisuolaa-suunniteltu-yhteensa (apply + (keep :talvisuolaraja talvisuolarajat))

        ;; Lisätään suolasummiin talvisuolojen käyttörajat
        talvisuolat-yhteensa-rivi (if-not (empty? talvisuolatoteumat)
                                    (map (fn [[{materiaali :materiaali urakka :urakka :as avain} rivit]]
                                           [avain (conj rivit (urakan-talvisuolan-maxmaara talvisuolan-maxmaarat
                                                                urakka
                                                                urakoittain?))])
                                      talvisuolatoteumat)
                                    (list [{:maara 0 :talvitieluokka nil :soratieluokka nil :kk nil :urakka nil
                                            :materiaali materiaali-kaikki-talvisuola-yhteensa}
                                           [{:kk nil :maara talvisuolaa-suunniteltu-yhteensa}]]))

        materiaalit (sort #(materiaalien-comparator %2 %1) (concat materiaalit-kannasta talvisuolat-yhteensa-rivi formiaatit-yhteensa-rivi kesasuola-yhteensa-rivi murske-yhteensa-rivi))

        kuukaudet (yleinen/kuukaudet alkupvm loppupvm yleinen/kk-ja-vv-fmt)
        materiaalit-tyypin-mukaan (fn [materiaalityyppi]
                                    (keep (fn [rivi]
                                            (when (and
                                                    ;; Jätä MH-urakoille tulevat tehtävät ja määrät luettelon suunnitellut rivit pois
                                                    ;; Ihan niin suoraa ne eivät tule, mutta ne on materiaaleja, joilla ei ole id:tä.
                                                    ;; Eli ne on yhteenveto tietoja, joita kanta palauttaa
                                                    (not (nil? (get-in (first rivi) [:materiaali :id])))
                                                    (= materiaalityyppi (get-in (first rivi) [:materiaali :tyyppi])))
                                              rivi))
                                      materiaalit))
        taulukon-tiedot {:otsikko nil
                         :osamateriaalit nil
                         :konteksti konteksti
                         :kuukaudet kuukaudet
                         :urakoittain? urakoittain?
                         :yksikot-soluissa? false
                         :nayta-suunnittelu? (if (pvm/onko-hoitokausi? alkupvm loppupvm)
                                               true
                                               false)}]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:teksti-paksu otsikko]
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
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Paikkausmateriaalit")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "paikkausmateriaali"))
                        (assoc :yksikot-soluissa? false)
                        (assoc :nayta-suunnittelu? true)))
     (koosta-taulukko (-> taulukon-tiedot
                        (assoc :otsikko "Muut materiaalit")
                        (assoc :osamateriaalit (materiaalit-tyypin-mukaan "muu"))
                        (assoc :yksikot-soluissa? true)
                        (assoc :nayta-suunnittelu? false)))]))
