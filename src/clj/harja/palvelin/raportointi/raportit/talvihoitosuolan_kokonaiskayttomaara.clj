(ns harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.lampotilat :as lampotilat-kyselyt]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]))

(defn- jasenna-datarivi
  "Raporteille pitää antaa tiedot hyvin spesifissä muodossa.
  Muodostetaan tässä tietyille elementeille tarkasti määritellyt asiat, jotta ne näkyy raportilla oikein.
  Defaulttina raporteille riittää, että kolumnien datat on määritelty otsikko-elementeissä, mutta
  näyttää siltä, että yhden otsikko-elementin alle pitää pystyä laittamaan monellaista dataa.

  Tästä syystä tässä on jouduttu iffittelemään paljon asioita.
  Esim. puuttuvien tietojen kohdalla annetaan erillinen :arvo elementti, joka väritetään poikkeavasti jne."
  [rivi]
  [[:arvo {:arvo (:hoitovuosi rivi)}]
   (if (:keskilampotila-jaksolla rivi)
     (:keskilampotila-jaksolla rivi)
     [:arvo {:arvo "Lämpötilatieto puuttuu"
             :huomio? true}])
   (if (:keskilampotila-pitkalla-aikavalilla rivi)
     (:keskilampotila-pitkalla-aikavalilla rivi)
     [:arvo {:arvo "Lämpötilatieto puuttuu"
             :huomio? true}])
   (:erotus-celcius rivi)
   (:lampotilan-vaikutus rivi)
   (if (:kayttoraja rivi)
     (:kayttoraja rivi)
     [:arvo {:arvo "Käyttöraja puuttuu"
             :huomio? true}])
   (if (:kohtuull-kayttoraja rivi) (:kohtuull-kayttoraja rivi) [:arvo {:arvo "-"}])
   (if (:toteuma rivi) (:toteuma rivi) [:arvo {:arvo "-"}])
   [:arvo
    (merge
      {:arvo (:erotus-toteuma rivi)
       :jos-tyhja "-"
       :desimaalien-maara 2
       :ryhmitelty? true}
      (if (> (:erotus-toteuma rivi) 0)
        {:varoitus? true}
        {:korosta-hennosti? true}))]])

(defn- yhteenvetorivi [rivi]
  {:lihavoi? true
   :korosta-hennosti? true
   :rivi [[:arvo {:arvo "Yhteensä"}]
          nil
          nil
          nil
          nil
          (if (= 0 (:kayttoraja-yhteensa rivi))
            [:arvo {:arvo nil :jos-tyhja "-"}]
            (:kayttoraja-yhteensa rivi))
          (if (= 0 (:kohtuull-kayttoraja-yhteensa rivi))
            [:arvo {:arvo nil :jos-tyhja "-"}]
            (:kohtuull-kayttoraja-yhteensa rivi))
          (:toteuma-yhteensa rivi)
          [:arvo
           (merge
             {:arvo (:erotus-toteuma-yhteensa rivi)
              :jos-tyhja "-"
              :desimaalien-maara 2
              :ryhmitelty? true}
             (if (> (:erotus-toteuma-yhteensa rivi) 0)
               {:varoitus? true}
               {:korosta-hennosti? true}))]]})

(defn lampotilan-vaikutus-suolan-kulutukseen
  "Joulu-, tammi, ja helmikuun keskilämpötilojen keskiarvo korkempi kuin ns. pitkän aikavälin (30v)
  k. kuukausien keskiarvopämpötilojen keskiarvo.

  < 2.0 c korkeampi - Ei korotusta
  < 3.0 c korkeampi - 10% korotusta
  < 4.0 c korkeampi - 20% korotusta
  >= 4.0 c korkeampi - 30% korotusta
  "
  [erotus]
  (when erotus
    (cond
      (< erotus 2.0) 0
      (< erotus 3.0) 10
      (< erotus 4.0) 20
      (>= erotus 4.0) 30
      ;; Kaikissa virhetilanteissa palauta nolla
      :else 0)))

(defn kohtuullistettu-kayttoraja
  "Käyttöraja on suolaa tonneina.
  Vaikutus on arvo 0 - 30, joka kertoo prosenteista. 10 = 10%."
  [kayttoraja vaikutus]
  ;; Varmista, että molemmat arvot ovat annettu
  (when (and kayttoraja vaikutus (<= vaikutus 30) (>= vaikutus 0))
    (* (float kayttoraja) (+ (/ vaikutus 100) 1))))



(defn paattele-kaytettava-keskilampotilajakso
  "Päätellään pitkän aikajakson keskilämpötila urakan alkuvuodesta"
  [urakan_alkuvuosi lampotila-vuodelle]
  (when (and urakan_alkuvuosi (number? urakan_alkuvuosi))
    (let [lampotila-avain (cond
                            (<= urakan_alkuvuosi 2014) :keskilampotila-1971-2000
                            (<= urakan_alkuvuosi 2022) :keskilampotila-1981-2010
                            :else :keskilampotila-1991-2020)
          keskilampo-pitka (lampotila-avain lampotila-vuodelle)]
      keskilampo-pitka)))

(defn paattele-raportin-viimeinen-hoitovuosi
  "Aina ei voida näyttää koko hoitokautta tai kaikkia esim viittä vuotta, koska urakka on kesken.
  Päätellään tässä, että mikä on viimeinen valmistunut hoitokausi."
  [urakan-loppupvm]
  (let [nyt-kuukausi (pvm/kuukausi (pvm/nyt))
        vuoden-loppu? (case nyt-kuukausi
                        10 true
                        11 true
                        12 true
                        false)]
    (if (pvm/ennen? urakan-loppupvm (pvm/nyt))
      (pvm/vuosi urakan-loppupvm)
      ;; Jos kuukausi on 10,11 tai 12, niin sama vuosi riittää,
      ;; muuten riittää tarkastukseksi että onko edellinen vuosi
      (if vuoden-loppu?
        (pvm/vuosi (pvm/nyt))
        ;; Jos on vuoden alku, niin otetaan nykypäivästä yksi vuosi pois
        (dec (pvm/vuosi (pvm/nyt)))))))

(defn jasenna-raportin-otsikko [urakan-tiedot hoitovuodet]
  (if hoitovuodet
    (str "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu " (pvm/pvm (:alkupvm urakan-tiedot)) " - " (str "30.09." (inc (last hoitovuodet))))
    (str "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu - Ei valmistuneita hoitovuosia")))

(defn suorita [db _ {:keys [urakka-id hallintayksikko-id] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                    hallintayksikko-id :hallintayksikko
                    :default :koko-maa)
        ;; Haetaan tiedot hoitokausittain - ja siihen tarvitaan urakan kesto
        urakan-tiedot (first (urakat-kyselyt/hae-yksittainen-urakka db {:urakka_id urakka-id}))
        urakan_alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        viimeinen-mahdollinen-vuosi (paattele-raportin-viimeinen-hoitovuosi (:loppupvm urakan-tiedot))
        ;; Jos urakka on vasta alkanut ja valmistuneita hoitokausia ei ole, niin ei haeta tietoja
        hoitovuodet (if (not= viimeinen-mahdollinen-vuosi (pvm/vuosi (:alkupvm urakan-tiedot)))
                      (range
                        (pvm/vuosi (:alkupvm urakan-tiedot))
                        ;; Näytetään vain päättyneiltä hoitokausilta
                        viimeinen-mahdollinen-vuosi)
                      nil)

        ;; Haetaan hoitovuodelle keskilämpötilojen keskiarvo tarkastelujaksolla
        urakan-lampotilat (lampotilat-kyselyt/hae-urakan-lampotilat db {:urakka urakka-id})

        ;; Koostetaan data map tyyppiseen rakenteeseen
        data (when hoitovuodet
               (reduce (fn [d vuosi]
                         (let [lampotila-vuodelle (some (fn [rivi]
                                                          (when (= vuosi (pvm/vuosi (:alkupvm rivi)))
                                                            rivi))
                                                    urakan-lampotilat)
                               ;; Päätellään pitkän aikajakson keskilämpötila urakan alkuvuodesta
                               keskilampo-pitka (paattele-kaytettava-keskilampotilajakso urakan_alkuvuosi lampotila-vuodelle)
                               keskilampo (:keskilampotila lampotila-vuodelle)
                               ;; Lämpötilojen erotus celciuksena
                               erotus-c (if (and (not (nil? keskilampo)) (not (nil? keskilampo-pitka)))
                                          (- keskilampo keskilampo-pitka)
                                          0)
                               lampotilan-vaikutus (lampotilan-vaikutus-suolan-kulutukseen erotus-c)

                               ;; HAetaan suolarajoitukset
                               suolarajoitukset (first (suolarajoitus-kyselyt/hae-talvisuolan-kokonaiskayttoraja db
                                                         {:urakka-id urakka-id
                                                          :hoitokauden-alkuvuosi vuosi}))
                               suolan-kokonaismaara (first (materiaalit-kyselyt/hae-talvisuolan-hoitovuoden-kokonaismaara db
                                                             {:urakka-id urakka-id
                                                              :alkupvm (pvm/hoitokauden-alkupvm vuosi)
                                                              :loppupvm (pvm/hoitokauden-loppupvm (inc vuosi))}))
                               toteuma (:kokonaismaara suolan-kokonaismaara)
                               kohtuullistettu-kayttoraja (kohtuullistettu-kayttoraja (:talvisuolan_kayttoraja suolarajoitukset) lampotilan-vaikutus)
                               erotus-toteuma (if (and
                                                    (not (nil? kohtuullistettu-kayttoraja))
                                                    (not (nil? toteuma)))
                                                (- toteuma kohtuullistettu-kayttoraja)
                                                0)]
                           (conj d {:hoitovuosi (str vuosi "-" (inc vuosi))
                                    :keskilampotila-jaksolla keskilampo
                                    :keskilampotila-pitkalla-aikavalilla keskilampo-pitka
                                    :erotus-celcius erotus-c
                                    :lampotilan-vaikutus (str (when (> lampotilan-vaikutus 0) "+") lampotilan-vaikutus " %")
                                    :kayttoraja (:talvisuolan_kayttoraja suolarajoitukset)
                                    :kohtuull-kayttoraja kohtuullistettu-kayttoraja
                                    :toteuma toteuma
                                    :erotus-toteuma erotus-toteuma})))
                 [] hoitovuodet))

        ;; Konteksti on tätä kirjoittaessa rajoitettu urakkaan, mutta jos myöhemmin huomataan, että
        ;; ely tai koko maan taso halutaan, niin riittää, että raportin asetuksista määritellään kontekstiin puuttuvat tiedot
        ;; Raportin nimeen se ei tule vaikuttamaan, koska se on jo toteuteuttu tässä.
        nimi (if (nil? (:kasittelija parametrit))
               ;; HTML raportille raportin nimi muodostetaan kontekstista
               (case konteksti
                 :urakka (:nimi urakan-tiedot)
                 :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                 :koko-maa "KOKO MAA")
               ;; PDF ja Excel raportteihin laitetaan raportille eri nimi, joka tulee tiedoston nimeksi
               (str "Talvisuolan lämpötilaraportti - " (:nimi urakan-tiedot)))
        raportin-nimi nimi
        otsikko (jasenna-raportin-otsikko urakan-tiedot hoitovuodet)

        otsikkorivit [{:otsikko "Hoitovuosi" :leveys 1 :fmt :kokonaisluku :tasaa :vasen}
                      {:otsikko "Keskilämpötilojen keskiarvo tarkastelujaksolla (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Keskilämpötilojen keskiarvo pitkällä aikavälillä (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Erotus (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Lämpötilan vaikutus käyttörajaan" :leveys 1 :fmt :teksti :tasaa :oikea}
                      {:otsikko "Käyttöraja (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Kohtuullistettu käyttöraja (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Toteuma (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Erotus (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}]
        datarivit (into [] (map jasenna-datarivi data))
        kohtuullistettu-kayttoraja-yhteensa (apply + (map (fn [rivi]
                                                            (if (:kohtuull-kayttoraja rivi)
                                                              (:kohtuull-kayttoraja rivi)
                                                              0)) data))
        toteuma-yhteensa (apply + (map (fn [rivi]
                                         (if (:toteuma rivi)
                                           (:toteuma rivi)
                                           0)) data))

        yhteevetodata {:kayttoraja-yhteensa (apply + (map (fn [rivi]
                                                            (if (:kayttoraja rivi)
                                                              (:kayttoraja rivi)
                                                              0))
                                                       data))
                       :kohtuull-kayttoraja-yhteensa kohtuullistettu-kayttoraja-yhteensa
                       :toteuma-yhteensa toteuma-yhteensa
                       :erotus-toteuma-yhteensa (if (and
                                                      (and
                                                        (not (nil? kohtuullistettu-kayttoraja-yhteensa))
                                                        (not (= 0 kohtuullistettu-kayttoraja-yhteensa)))
                                                      (not (nil? toteuma-yhteensa)))
                                                  (- toteuma-yhteensa kohtuullistettu-kayttoraja-yhteensa)
                                                  0)}
        yhteenvetorivi (yhteenvetorivi yhteevetodata)
        datarivit (conj datarivit yhteenvetorivi)]

    ;; Tehdään raportti täyttämällä raporttipohja aiemmin luoduilla tiedoilla
    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? data) "Ei raportoitavia tietoja.")
                 :sheet-nimi "Talvihoitosuolat"}
      otsikkorivit
      datarivit]]))
