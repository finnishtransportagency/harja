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
  {:nimi "Kaikki talvisuola yhteensä"
   :yksikko "t"})

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
  [kk-rivit materiaali]
  (reduce-kv (fn [kk-arvot kk rivit]
                    (assoc kk-arvot kk [:arvo-ja-yksikko {:arvo (reduce + (keep :maara rivit))
                                                          :yksikko (:yksikko materiaali)
                                                          :desimaalien-maara 2}]))
                  {} kk-rivit))

(defn- yhteensa-arvo
  [arvot]
  (reduce + (remove nil? (map (comp :arvo second) arvot))))

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
        kk-lev (if urakoittain?
                 "4%" ; tehdään yksittäisestä kk:sta pienempi, jotta urakan nimi mahtuu
                 "5%")
        raportin-nimi "Ympäristöraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)

        talvisuolan-ryhmittely-fn (if urakoittain? (juxt :kk :urakka) :kk)
        kaikki-talvisuola-yhteensa-ryhmiteltyna (group-by talvisuolan-ryhmittely-fn
                                                          (filter #(and
                                                                     ;; summataan vain toteumat eli kun luokka on nil
                                                                     (nil? (:luokka %))
                                                                     (= "talvisuola" (get-in % [:materiaali :tyyppi])))
                                                                  (apply concat (map second materiaalit-kannasta))))

        kaikki-talvisuola-yhteensa-ryhmiteltyna-ja-summattuna
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
             kaikki-talvisuola-yhteensa-ryhmiteltyna)

        kaikki-talvisuola-yhteensa-ryhmiteltyna-ja-summattuna
        (group-by :urakka
                  (apply concat (map vals kaikki-talvisuola-yhteensa-ryhmiteltyna-ja-summattuna)))
        suolasummat-ilman-kayttorajoja (mapv (fn [[urakka rivit]]
                                               [{:materiaali materiaali-kaikki-talvisuola-yhteensa
                                                 :urakka urakka}
                                                rivit])
                                             kaikki-talvisuola-yhteensa-ryhmiteltyna-ja-summattuna)
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
        suolasummat (map (fn [[{materiaali :materiaali urakka :urakka :as avain} rivit]]
                           [avain (conj rivit (urakan-talvisuolan-maxmaara talvisuolan-maxmaarat
                                                                           urakka
                                                                           urakoittain?))])
                         suolasummat-ilman-kayttorajoja)
        materiaalit (sort #(materiaalien-comparator %2 %1) (concat materiaalit-kannasta suolasummat))

        talvisuolan-toteutunut-maara (some->> materiaalit
                                              (filter (fn [[materiaali _]]
                                                        (= "talvisuola" (get-in materiaali [:materiaali :tyyppi])))) ;; vain talvisuolat
                                              (mapcat second)
                                              (filter #(nil? (:luokka %))) ;; luokka on nil toteumariveillä (lihavoidut raportissa)
                                              (map :maara)
                                              (reduce +))
        listan-ensimmaisen-urakan-id (get-in (ffirst materiaalit) [:urakka :id])
        kuukaudet (yleinen/kuukaudet alkupvm loppupvm yleinen/kk-ja-vv-fmt)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:teksti (str "Erilaisia talvisuoloja käytetty valitulla aikavälillä: "
                   (fmt/desimaaliluku-opt talvisuolan-toteutunut-maara 2)
                   "t")]

     [:taulukko {:otsikko otsikko
                 :oikealle-tasattavat-kentat (into #{} (range 1 (+ 4 (count kuukaudet))))
                 :sheet-nimi raportin-nimi}
      (into []

            (concat
             (when urakoittain?
               [{:otsikko "Urakka" :leveys "10%"}])

             ;; Materiaalin nimi
             [{:otsikko "Materiaali" :leveys "16%"}]
             ;; Kaikki kuukaudet
             (map (fn [kk]
                    {:otsikko kk
                     :leveys kk-lev
                     :fmt :numero}) kuukaudet)

             [{:otsikko "Määrä yhteensä" :leveys "8%" :fmt :numero :jos-tyhja "-"
               :excel [:summa-vasen (if urakoittain? 2 1)]}
              {:otsikko "Tot-%" :leveys "8%" :fmt :prosentti :jos-tyhja "-"}
              {:otsikko "Suunniteltu määrä / talvisuolan max-määrä" :leveys "8%" :fmt :numero :jos-tyhja "-"}]))

      (mapcat
       (fn [[{:keys [urakka materiaali]} rivit]]
         (let [suunnitellut (keep :maara (filter #(nil? (:kk %)) rivit))
               suunniteltu (when-not (empty? suunnitellut)
                         (reduce + suunnitellut))
               luokitellut (filter :luokka rivit)
               kk-arvot (kk-arvot (kk-rivit rivit) materiaali)
               yhteensa-kentta (fn [arvot nayta-aina?]
                                 (let [yht (yhteensa-arvo arvot)]
                                   (when (or (> yht 0) nayta-aina?)
                                     [:arvo-ja-yksikko {:arvo yht
                                                        :yksikko (:yksikko materiaali)
                                                        :desimaalien-maara 2}])))]
           (concat
             ;; Talvisuolat-väliotsikko
             (when (and (= listan-ensimmaisen-urakan-id (:id urakka))
                     (= "Talvisuola" (:nimi materiaali)))
                      [{:otsikko "Talvisuolat"}])

             ;; Muut materiaalit -väliotsikko, pakko käyttää nimeä, perustuu järjestykseen domain.materiaali:ssa
             (when (and (= listan-ensimmaisen-urakan-id (:id urakka))
                        (= "Kaliumformiaatti" (:nimi materiaali)))
               [{:otsikko "Muut materiaalit"}])
             
             ;; Normaali materiaalikohtainen rivi
            [{:lihavoi? true
              :rivi (into []
                          (concat

                            ;; Urakan nimi, jos urakoittain jaottelu päällä
                            (when urakoittain?
                              [(:nimi urakka)])

                            ;; Materiaalin nimi
                            [(materiaalin-nimi (:nimi materiaali))]

                            ;; Kuukausittaiset määrät
                            (map kk-arvot kuukaudet)

                            ;; Yhteensä, toteumaprosentti ja suunniteltumäärä
                            [(yhteensa-kentta (vals kk-arvot) false)
                             (when suunniteltu [:arvo-ja-yksikko {:arvo (/ (* 100.0 (yhteensa-arvo (vals kk-arvot))) suunniteltu)
                                                                  :yksikko "%"
                                                                  :desimaalien-maara 2}])
                             (when suunniteltu [:arvo-ja-yksikko {:arvo suunniteltu
                                                                  :yksikko (:yksikko materiaali)
                                                                  :desimaalien-maara 2}])]))}]

             ;; Mahdolliset hoitoluokkakohtaiset rivit
            (map (fn [[luokka rivit]]
                   (let [rivit (if (or urakoittain? (= konteksti :urakka))
                                 rivit
                                 ;; Jos ei eritellä urakoittain, on laskettava eri urakoiden määrät yhteen
                                 (map
                                   #(assoc (first (val %)) :maara (reduce + 0 (keep :maara (val %))))
                                   (group-by :kk rivit)))
                         kk-arvot (into {}
                                        (map (juxt :kk #(do
                                                          [:arvo-ja-yksikko {:arvo (:maara %)
                                                                               :yksikko (:yksikko materiaali)
                                                                               :desimaalien-maara 2}])))
                                        rivit)]
                     (into []
                           (concat
                            (when urakoittain?
                              [(:nimi urakka)])
                            [(str " - "
                                  (hoitoluokat/talvihoitoluokan-nimi luokka))]

                            (map kk-arvot kuukaudet)

                            [(yhteensa-kentta (vals kk-arvot) false)
                             nil nil]))))
                 (sort-by first (group-by :luokka luokitellut))))))

       materiaalit)]
     (when-not (empty? materiaalit)
       [:teksti (str "Tummennetut arvot ovat tarkkoja toteumamääriä, hoitoluokittainen jaottelu perustuu reittitietoon ja voi sisältää epätarkkuutta. "
                     yleinen/materiaalitoteumien-paivitysinfo)])]))

