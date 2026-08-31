(ns harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara
  (:require [harja.pvm :as pvm]
            [harja.fmt :as fmt]
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
     [:arvo {:arvo (:keskilampotila-jaksolla rivi)
             :desimaalien-maara 1}]
     [:arvo {:arvo "Ei vielä saatavilla"}])
   (if (:keskilampotila-pitkalla-aikavalilla rivi)
     [:arvo {:arvo (:keskilampotila-pitkalla-aikavalilla rivi)
             :desimaalien-maara 1}]
     [:arvo {:arvo "Ei vielä saatavilla"}])
   (if (some? (:erotus-celcius rivi))
     [:arvo {:arvo (:erotus-celcius rivi)
             :desimaalien-maara 1}]
     [:arvo {:arvo "-"}])
   (:lampotilan-vaikutus rivi)
   (if (:kayttoraja rivi)
     [:arvo {:arvo (:kayttoraja rivi)
             :desimaalien-maara 2}]
     [:arvo {:arvo "Tieto puuttuu"}])
   (if (:kohtuull-kayttoraja rivi)
     [:arvo {:arvo (:kohtuull-kayttoraja rivi)
             :desimaalien-maara 2}]
     [:arvo {:arvo "-"}])
   (if (:toteuma rivi)
     [:arvo {:arvo (:toteuma rivi)
             :desimaalien-maara 2}]
     [:arvo {:arvo "-"}])])

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
            [:arvo {:arvo (:kayttoraja-yhteensa rivi)
                    :desimaalien-maara 2}])
          (if (= 0 (:kohtuull-kayttoraja-yhteensa rivi))
            [:arvo {:arvo nil :jos-tyhja "-"}]
            [:arvo {:arvo (:kohtuull-kayttoraja-yhteensa rivi)
                    :desimaalien-maara 2}])
          (if (= 0 (:toteuma-yhteensa rivi))
            [:arvo {:arvo nil :jos-tyhja "-"}]
            [:arvo {:arvo (:toteuma-yhteensa rivi)
                    :desimaalien-maara 2}])]})

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
  "Aina ei voida näyttää koko hoitokautta tai kaikkia esim viittä vuotta, koska urakka on kesken ja
  lämpötilatietoja ei välttämättä ole vielä syötetty.
  Päätellään tässä, että mikä on viimeinen valmistunut hoitokausi tai viimeinen vuosi, jolle lämpötilatiedot on.
  Näytetään aikaisintaan 1.3. kuluvan hoitokauden tiedot, edellyttäen, että lämpötilatiedot on jo syötetty. "
  [urakan-loppupvm lampotilat]
  (let [nyt (pvm/nyt)
        nyt-vuosi (pvm/vuosi nyt)
        nyt-kuukausi (pvm/kuukausi nyt)
        ;; Hoitokausi on valmis vain jos olemme lokakuussa tai myöhemmin
        hoitokausi-valmis? (>= nyt-kuukausi 10)
        ;; maalis - syyskuu = 3 - 9
        maalis-syyskuu? (>= nyt-kuukausi 3)]
    (cond
      ;; Jos urakka on päättynyt, palautetaan päättymispäivän vuosi
      (pvm/ennen? (pvm/paivan-lopussa urakan-loppupvm) nyt)
      (pvm/vuosi urakan-loppupvm)

      ;; Jos ollaan lokakuussa tai myöhemmin, nykyinen hoitokausi on valmis
      hoitokausi-valmis?
      nyt-vuosi

      ;; Jos ollaan maalis-syyskuussa, palautetaan viimeisin lämpötiladatavuosi, mutta ei tulevaisuuden vuosia, vaikka lämpötildataa vahingossa sinne olisi voinut syöttää
      :else
      (if (and maalis-syyskuu? (last lampotilat))
        (min nyt-vuosi (pvm/vuosi (:loppupvm (last lampotilat))))
        (dec nyt-vuosi)))))

(defn jasenna-raportin-otsikko [urakan-tiedot hoitovuodet]
  (if hoitovuodet
    (str "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu " (pvm/pvm (:alkupvm urakan-tiedot)) " - " (str "30.09." (inc (last hoitovuodet))))
    (str "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu - Ei valmistuneita hoitovuosia")))

(defn yhteenvetolaatikko [kasittelija yhteevetodata ]
  (if (= kasittelija :excel)
    [:taulukko {:otsikko "Koko urakka-ajan yhteenveto (kuivatonneina)"
                :viimeinen-rivi-yhteenveto? false
                :sheet-nimi "Talvihoitosuolat"
                :samalle-sheetille? true}
     [{:leveys 20 :otsikko ""}
      {:leveys 5 :otsikko "" :fmt :numero :tasaa :oikea}]
     [["Tehtävä- ja määräluettelon mukainen käyttöraja"
       (if (= 0 (:kayttoraja-yhteensa yhteevetodata))
         "Tieto puuttuu"
         [:arvo {:arvo (:kayttoraja-yhteensa yhteevetodata)
                 :desimaalien-maara 2}])]
      ["Kohtuullistettu käyttöraja"
       (if (= 0 (:kohtuull-kayttoraja-yhteensa yhteevetodata))
         "-"
         [:arvo {:arvo (:kohtuull-kayttoraja-yhteensa yhteevetodata)
                 :desimaalien-maara 2}])]
      ["Suurin urakassa sallittu käyttömäärä + 5 %"
       (if (= 0 (:kohtuull-kayttoraja-yhteensa yhteevetodata))
         "-"
         [:arvo {:arvo (* 1.05 (:kohtuull-kayttoraja-yhteensa yhteevetodata))
                 :desimaalien-maara 2}])]
      ["Toteuma koko urakka-ajalta"
       (if (= 0 (:toteuma-yhteensa yhteevetodata))
         "-"
         [:arvo {:arvo (:toteuma-yhteensa yhteevetodata)
                 :desimaalien-maara 2}])]
      ["josta sallitun käyttömäärän ylittävä, sanktioon johtava toteuma"
       (if (> (:toteuma-yhteensa yhteevetodata) (* 1,05 (:kohtuull-kayttoraja-yhteensa yhteevetodata)))
         [:arvo {:arvo (- (:toteuma-yhteensa yhteevetodata) (* 1,05 (:kohtuull-kayttoraja-yhteensa yhteevetodata)))
                 :desimaalien-maara 2}]
         "Ei ylitystä")]]]

    [:yhteenveto-laatikko {:otsikko "Koko urakka-ajan yhteenveto (kuivatonneina)"}
     [(if (= 0 (:kayttoraja-yhteensa yhteevetodata))
        {:infolaatikko? true
         :teksti "Tehtävä- ja määräluettelon mukainen käyttöraja"
         :toissijainen-viesti "Tieto puuttuu"
         :tyyppi :vahva-ilmoitus
         :ikoni :harja-icon-status-alert}
        {:avain "Tehtävä- ja määräluettelon mukainen käyttöraja"
         :arvo (fmt/yksikolla "t" (fmt/desimaaliluku-opt (:kayttoraja-yhteensa yhteevetodata) 2 2 true))})
      {:avain "Kohtuullistettu käyttöraja"
       :arvo (if (= 0 (:kohtuull-kayttoraja-yhteensa yhteevetodata))
               "-"
               (fmt/yksikolla "t" (fmt/desimaaliluku-opt (:kohtuull-kayttoraja-yhteensa yhteevetodata) 2 2 true)))}
      {:avain "Suurin urakassa sallittu käyttömäärä + 5 %"
       :arvo (if (= 0 (:kohtuull-kayttoraja-yhteensa yhteevetodata))
               "-"
               (fmt/yksikolla "t" (fmt/desimaaliluku-opt (* 1,05 (:kohtuull-kayttoraja-yhteensa yhteevetodata)) 2 2 true)))}
      {:avain "Toteuma koko urakka-ajalta"
       :arvo (if (= 0 (:toteuma-yhteensa yhteevetodata))
               "-"
               (fmt/yksikolla "t" (fmt/desimaaliluku-opt (:toteuma-yhteensa yhteevetodata) 2 2 true)))
       :lihavoi? true}
      {:avain "josta sallitun käyttömäärän ylittävä, sanktioon johtava toteuma"
       :arvo (if (> (:toteuma-yhteensa yhteevetodata) (* 1,05 (:kohtuull-kayttoraja-yhteensa yhteevetodata)))
               (fmt/yksikolla "t" (fmt/desimaaliluku-opt (- (:toteuma-yhteensa yhteevetodata) (* 1,05 (:kohtuull-kayttoraja-yhteensa yhteevetodata))) 2 2 true))
               "Ei ylitystä")
       :lihavoi? true}]]))

(defn suorita [db _ {:keys [urakka-id elinvoimakeskus-id kasittelija] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                    elinvoimakeskus-id :elinvoimakeskus
                    :default :koko-maa)

        ;; Haetaan hoitovuodelle keskilämpötilojen keskiarvo tarkastelujaksolla
        urakan-lampotilat (lampotilat-kyselyt/hae-urakan-lampotilat db {:urakka urakka-id})

        ;; Haetaan tiedot hoitokausittain - ja siihen tarvitaan urakan kesto
        urakan-tiedot (first (urakat-kyselyt/hae-yksittainen-urakka db {:urakka_id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
        hoitovuodet (range urakan-alkuvuosi urakan-loppuvuosi)

        ;; Koostetaan data map tyyppiseen rakenteeseen
        data (when hoitovuodet
               (reduce (fn [d vuosi]
                         (let [lampotila-vuodelle (some (fn [rivi]
                                                          (when (= vuosi (pvm/vuosi (:alkupvm rivi)))
                                                            rivi))
                                                    urakan-lampotilat)
                               ;; Päätellään pitkän aikajakson keskilämpötila urakan alkuvuodesta
                               keskilampo-pitka (paattele-kaytettava-keskilampotilajakso urakan-alkuvuosi lampotila-vuodelle)
                               keskilampo (:keskilampotila lampotila-vuodelle)
                               ;; Lämpötilojen erotus celciuksena
                               erotus-c (if (and (not (nil? keskilampo)) (not (nil? keskilampo-pitka)))
                                          (- keskilampo keskilampo-pitka)
                                           nil)
                               lampotilan-vaikutus (when (number? erotus-c) (lampotilan-vaikutus-suolan-kulutukseen erotus-c))

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
                                    :lampotilan-vaikutus (if (number? lampotilan-vaikutus)
                                                           (str (when (> lampotilan-vaikutus 0) "+") lampotilan-vaikutus " %")
                                                           (str "-"))
                                    :kayttoraja (:talvisuolan_kayttoraja suolarajoitukset)
                                    :kohtuull-kayttoraja kohtuullistettu-kayttoraja
                                    :toteuma toteuma
                                    :erotus-toteuma erotus-toteuma})))
                 [] hoitovuodet))

        ;; Konteksti on tätä kirjoittaessa rajoitettu urakkaan, mutta jos myöhemmin huomataan, että
        ;; ely tai koko maan taso halutaan, niin raportin asetuksista pitää määritellä kontekstiin puuttuvat tiedot
        ;; ja lisätä aikarajauksen valinta raportille.
        raportin-konteksti (when (or (= :excel (:kasittelija parametrit)) (= :pdf (:kasittelija parametrit)))
                              (str ", "
                                (case konteksti
                                  :urakka (:nimi urakan-tiedot)
                                  :elinvoimakeskus (:nimi (first (hallintayksikot-q/hae-organisaatio db elinvoimakeskus-id)))
                                  :koko-maa "KOKO MAA")))
        raportin-nimi (str "Talvisuolan kokonaiskäyttö" raportin-konteksti)

        otsikkorivit [{:otsikko "Hoitovuosi" :leveys 1 :fmt :kokonaisluku :tasaa :vasen}
                      {:otsikko "Keskilämpötilojen keskiarvo tarkastelujaksolla (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Keskilämpötilojen keskiarvo pitkällä aikavälillä (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Erotus (°C)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Lämpötilan vaikutus käyttörajaan" :leveys 1 :fmt :teksti :tasaa :oikea}
                      {:otsikko "Käyttöraja tehtävä- ja määräluettelossa (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Kohtuullistettu käyttöraja (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}
                      {:otsikko "Toteuma (kuivatonnia)" :leveys 1 :fmt :numero :tasaa :oikea}]
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
     [:otsikko-heading-small (str (:nimi urakan-tiedot))]

     [:infolaatikko
      [:span.talvisuola-info
       "Mahdollinen talvisuolan kokonaiskäyttöön liittyvä sanktio määrätään vasta urakan päättyessä vastaanottotarkastuksessa. Sanktio kirjataan "
       [:a {:href (str "/#urakat/laadunseuranta/sanktiot?&hy=" (:elinvoimakeskus_id urakan-tiedot) "&u=" urakka-id)
            :target "_blank" :rel "noopener noreferrer"}
        "Sanktiot ja bonukset"]" -välilehdellä."]
      {:tyyppi :neutraali
       :toissijainen-viesti ""
       :leveys 800
       :rivita? false}]

     (yhteenvetolaatikko kasittelija yhteevetodata)

     [:taulukko {:otsikko "Erittely hoitovuosittain"
                 :tyhja (when (empty? data) "Ei raportoitavia tietoja.")
                 :sheet-nimi "Talvihoitosuolat"
                 :samalle-sheetille? true}
      otsikkorivit
      datarivit]]))
