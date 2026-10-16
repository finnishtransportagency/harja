(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-tyomaa
  "Työmaakokous laskutusyhteenveto raportin taulukko rakenteet"
  (:require [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-yhteiset :refer [taulukko-rivi]]))


(def ^:private hankinnat-rivit
  [{:lihavoi? false
    :otsikko "Talvihoito"
    :avain_yht :talvihoito_val_aika_yht
    :avain_hoitokausi :talvihoito_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Liikenneympäristön hoito"
    :avain_yht :lyh_val_aika_yht
    :avain_hoitokausi :lyh_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Sorateiden hoito"
    :avain_yht :sora_val_aika_yht
    :avain_hoitokausi :sora_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Päällystepaikkaukset"
    :avain_yht :paallyste_val_aika_yht
    :avain_hoitokausi :paallyste_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "MHU ylläpito"
    :avain_yht :yllapito_val_aika_yht
    :avain_hoitokausi :yllapito_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "MHU korvausinvestointi"
    :avain_yht :korvausinv_val_aika_yht
    :avain_hoitokausi :korvausinv_hoitokausi_yht}

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :hankinnat_val_aika_yht
    :avain_hoitokausi :hankinnat_hoitokausi_yht}])


(def ^:private hoidonjohto-rivit
  [{:lihavoi? false
    :otsikko "Johto- ja hallintokorvaukset"
    :avain_yht :johtojahallinto_val_aika_yht
    :avain_hoitokausi :johtojahallinto_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Erillishankinnat"
    :avain_yht :erillishankinnat_val_aika_yht
    :avain_hoitokausi :erillishankinnat_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Hoidonjohtopalkkio"
    :avain_yht :hjpalkkio_val_aika_yht
    :avain_hoitokausi :hjpalkkio_hoitokausi_yht}

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :hoidonjohto_val_aika_yht
    :avain_hoitokausi :hoidonjohto_hoitokausi_yht}])


(def ^:private muutokset-rivit
  [{:lihavoi? false
    :otsikko "Muutostyöt (erillisrahoitetut)"
    :avain_yht :muutos_erillis_val_aika_yht
    :avain_hoitokausi :muutos_erillis_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Johto-ja hallintokorvauksen muutokset"
    :avain_yht :jjh_muutos_val_aika_yht
    :avain_hoitokausi :jjh_muutos_hoitokausi_yht}

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :muutostyo_val_aika_yht
    :avain_hoitokausi :muutostyo_hoitokausi_yht}])


(def
  ^{:doc "Poistunut speksistä, näytetään nykyään könttänä ei-tavoitehintaiset-rivit alla."}
  ^:private _lisatyot-rivit
  [{:lihavoi? false
    :otsikko "Lisätyöt (talvihoito)"
    :avain_yht :lisatyo_talvihoito_val_aika_yht
    :avain_hoitokausi :lisatyo_talvihoito_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (liikenneympäristön hoito)"
    :avain_yht :lisatyo_lyh_val_aika_yht
    :avain_hoitokausi :lisatyo_lyh_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (sorateiden hoito)"
    :avain_yht :lisatyo_sora_val_aika_yht
    :avain_hoitokausi :lisatyo_sora_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (päällystepaikkaukset)"
    :avain_yht :lisatyo_paallyste_val_aika_yht
    :avain_hoitokausi :lisatyo_paallyste_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (MHU ylläpito)"
    :avain_yht :lisatyo_yllapito_val_aika_yht
    :avain_hoitokausi :lisatyo_yllapito_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (MHU korvausinvestointi)"
    :avain_yht :lisatyo_korvausinv_val_aika_yht
    :avain_hoitokausi :lisatyo_korvausinv_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Lisätyöt (MHU hoidonjohto)"
    :avain_yht :lisatyo_hoidonjohto_val_aika_yht
    :avain_hoitokausi :lisatyo_hoidonjohto_hoitokausi_yht}

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :lisatyot_val_aika_yht
    :avain_hoitokausi :lisatyot_hoitokausi_yht}])


(defn- tavoitehintaan-vaikuttavat-rivit [hoitokauden-alkuvuosi urakan-alkuvuosi]
  [{:lihavoi? false
    :otsikko "Muut tavoitehintaan vaikuttavat kulut"
    :avain_yht :muut_kulut_val_aika
    :avain_hoitokausi :muut_kulut_hoitokausi}

   ;; Saadaan näyttää, jos urakka alkaa 2025 tai aiemmillekin urakoille, mutta vasta -26 hoitovuonna
   (when (or (>= urakan-alkuvuosi 2025) (>= hoitokauden-alkuvuosi 2026))
     {:lihavoi? false
      :otsikko "Arvonvähennykset"
      :avain_yht :arvonvahennykset_val_aika_yht
      :avain_hoitokausi :arvonvahennykset_hoitokausi_yht})

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :muut_kulut_val_aika_yht
    :avain_hoitokausi :muut_kulut_hoitokausi_yht}])


(defn- ei-tavoitehintaiset-rivit [data]
  [{:lihavoi? false
    :otsikko "Lisätyöt"
    :avain_yht :lisatyot_val_aika_yht
    :avain_hoitokausi :lisatyot_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Bonukset"
    :avain_yht :bonukset_val_aika_yht
    :avain_hoitokausi :bonukset_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Sanktiot"
    :avain_yht :sanktiot_val_aika_yht
    :avain_hoitokausi :sanktiot_hoitokausi_yht}

   {:lihavoi? false
    :otsikko "Muut tavoitehinnan ulkopuoliset kulut"
    :avain_yht :muut_kulut_ei_tavoite_val_aika
    :avain_hoitokausi :muut_kulut_ei_tavoite_hoitokausi}

   ;; Näytetään päätökset vain jos ne on olemassa
   (when (yhteiset/raha-arvo-olemassa? (:paatos_kattoh_ylitys_hoitokausi_yht data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päätös / Urakoitsija maksaa kattohinnan ylityksestä"
      :avain_yht :paatos_kattoh_ylitys_val_aika_yht
      :avain_hoitokausi :paatos_kattoh_ylitys_hoitokausi_yht})

   (when (yhteiset/raha-arvo-olemassa? (:paatos_tavoiteh_ylitys_hoitokausi_yht data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päätös / Urakoitsija maksaa tavoitehinnan ylityksestä"
      :avain_yht :paatos_tavoiteh_ylitys_hoitokausi_yht
      :avain_hoitokausi :paatos_tavoiteh_ylitys_hoitokausi_yht})

   (when (yhteiset/raha-arvo-olemassa? (:paatos_tavoitepalkkio_hoitokausi_yht data))
     {:lihavoi? false
      :otsikko "Tavoitepalkkio"
      :avain_yht :paatos_tavoitepalkkio_hoitokausi_yht
      :avain_hoitokausi :paatos_tavoitepalkkio_hoitokausi_yht})

   (when (yhteiset/raha-arvo-olemassa? (:paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päätös / Hoidonjohtopalkkion muutos"
      :avain_yht :paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht
      :avain_hoitokausi :paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht})

   {:lihavoi? true
    :yhteensa? true
    :otsikko "Yhteensä"
    :avain_yht :muut_kulut_ei_tavoite_val_aika_yht
    :avain_hoitokausi :muut_kulut_ei_tavoite_hoitokausi_yht}])


(defn- tee-taulukko-rivi
  [data kyseessa-kk-vali? {:keys [otsikko avain_hoitokausi avain_yht lihavoi? yhteensa?]}]
  (taulukko-rivi data otsikko
    {:lihavoi? (boolean lihavoi?)
     :avain_yht avain_yht
     :yhteensa? yhteensa?
     :avain_hoitokausi avain_hoitokausi
     :kyseessa-kk-vali? kyseessa-kk-vali?}))


(defn- tee-taulukko-rivit
  [data kyseessa-kk-vali? rivit]
  (into []
    (comp
      (remove nil?)
      (map #(tee-taulukko-rivi data kyseessa-kk-vali? %)))
    rivit))


(defn- rahavaraus-rivit
  [data
   avain-yhteensa-hoitokausi avain-yhteensa-valittu
   kyseessa-kk-vali?
   rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika]

  (let [;; Tietokannasta palautetut arvot 
        yhteensa-hoitokausi (avain-yhteensa-hoitokausi data)
        yhteensa-valittu (avain-yhteensa-valittu data)

        ;; Kaikki taulukon rivit tässä
        rivit (map (fn [nimi hoitokausi val-aika]
                     ;; Näytä rahavarausrivi aina, vaikka arvo on 0
                     ;; Jos mitään arvoja ei ole olemassa, Rahavarausten alla tulee lukemaan "Ei tietoja."
                     (taulukko-rivi
                       {:val-aika val-aika
                        :hoitokausi hoitokausi}
                       nimi
                       {:avain_yht :val-aika
                        :avain_hoitokausi :hoitokausi
                        :kyseessa-kk-vali? kyseessa-kk-vali?}))
                rahavaraukset-nimet
                rahavaraukset-hoitokausi
                rahavaraukset-val-aika)]

    ;; Lisää yhteensä-arvot rivien päätteeksi
    (concat rivit
      [(taulukko-rivi
         {:valittu yhteensa-valittu
          :hoitokausi yhteensa-hoitokausi}
         "Yhteensä"
         {:lihavoi? true
          :yhteensa? true
          :avain_yht :valittu
          :avain_hoitokausi :hoitokausi
          :kyseessa-kk-vali? kyseessa-kk-vali?})])))


(defn taulukko-tyomaakokous
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
           kyseessa-kk-vali? sheet-nimi tavoitehintainen?
           hoitokauden-alkuvuosi urakan-alkuvuosi]}]
  (let [rahavaraukset-nimet (konversio/pgarray->vector (:rahavaraus_nimet data))
        rahavaraukset-val-aika (konversio/pgarray->vector (:val_aika_yht_array data))
        rahavaraukset-hoitokausi (konversio/pgarray->vector (:hoitokausi_yht_array data))

        rivit (cond
                (= "Hankinnat" otsikko)
                (tee-taulukko-rivit data kyseessa-kk-vali? hankinnat-rivit)

                (= "Hoidonjohto" otsikko)
                (tee-taulukko-rivit data kyseessa-kk-vali? hoidonjohto-rivit)

                (and
                  (ominaisuus-kaytossa? :mhu-muutokset)
                  (= "Muutokset" otsikko))
                (tee-taulukko-rivit data kyseessa-kk-vali? muutokset-rivit)

                (= "Rahavaraukset" otsikko)
                (into []
                  (remove nil?)
                  (rahavaraus-rivit data
                    :kaikki_rahavaraukset_hoitokausi_yht
                    :kaikki_rahavaraukset_val_yht
                    kyseessa-kk-vali?
                    rahavaraukset-nimet
                    rahavaraukset-hoitokausi
                    rahavaraukset-val-aika))

                ;; Lisätyöt, muut
                (= "Kustannus" otsikko)
                (tee-taulukko-rivit data kyseessa-kk-vali? (ei-tavoitehintaiset-rivit data))

                ;; Tavoitehintaan vaikuttavat
                (and
                  tavoitehintainen?
                  (= "Muut tavoitehintaan vaikuttavat kulut" otsikko))
                (tee-taulukko-rivit data kyseessa-kk-vali? (tavoitehintaan-vaikuttavat-rivit hoitokauden-alkuvuosi urakan-alkuvuosi)))]

    [:taulukko {:sheet-nimi sheet-nimi
                :viimeinen-rivi-yhteenveto? false
                :oikealle-tasattavat-kentat #{1 2}}
     (rivi
       {:otsikko otsikko :leveys 36}
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))
     rivit]))


(defn- yhdista-rivimaaritykset
  "Yhdistää useista vuosikohtaisista lähteistä tulevat rivimääritykset yhdeksi listaksi.

  Kutsu rivilistoilla, joiden alkiot ovat rivimäppejä tai nil-arvoja.
  Saman otsikon ja :avain_hoitokausi-avaimen rivit säilytetään vain kerran;
  Yhteensä-rivit siirretään listan loppuun. Palauttaa rivimääritykset vektorina."
  [rivilistat]
  (let [rivit (reduce (fn [rivit uusi-rivi]
                        (if (or (nil? uusi-rivi)
                              (some #(= [(:otsikko %) (:avain_hoitokausi %)]
                                      [(:otsikko uusi-rivi) (:avain_hoitokausi uusi-rivi)])
                                rivit))
                          rivit
                          (conj rivit uusi-rivi)))
                []
                (mapcat identity rivilistat))]
    (concat (remove :yhteensa? rivit)
      (filter :yhteensa? rivit))))


(defn- vuosittainen-rivi
  "Muodostaa yhdestä rivimäärityksestä arvot sisältävän vuosittaisen raporttirivin.

  Vuosidata on vuosijärjestyksessä oleva vektori vuosittaisista mäpeistä, joissa arvot
  löytyvät :data-mäpistä. Rivimääritys sisältää :otsikko- ja
  :avain_hoitokausi-avaimet; vaihtoehtoisesti :arvo-fn saa koko vuosimäpin.
  Puuttuvat arvot tulkitaan nollaksi. Tavallinen rivi palautuu vektorina, ja
  :yhteensa?-rivi mäppinä, jossa arvot sisältävä vektori on :rivi-avaimessa."
  [vuosidata {:keys [otsikko avain_hoitokausi arvo-fn yhteensa?]}]
  (let [arvot (mapv (fn [{:keys [data] :as vuosi}]
                      (or (if arvo-fn
                            (arvo-fn vuosi)
                            (get data avain_hoitokausi))
                        0M))
                vuosidata)
        rivi (into [otsikko] (conj arvot (reduce + 0M arvot)))]
    (if yhteensa?
      {:lihavoi? true
       :korosta-hennosti? true
       :rivi rivi}
      rivi)))


(defn- rahavaraus-taulukon-tiedot
  "Valmistelee rahavarausten vuosidatan ja sitä vastaavat rivimääritykset.

  Kutsu vuosidatalla, jonka kunkin alkion :data-mäpissä ovat PostgreSQL-arrayt
  :rahavaraus_nimet ja :hoitokausi_yht_array. Palauttaa vektorin
  [rahavaraus-vuosidata rivimääritykset]: vuosidata sisältää arrayista
  muodostetut :rahavaraus-nimet- ja :rahavaraus-arvot-avaimet, ja rivimääritykset
  sisältävät kaikki eri rahavarausnimet sekä yhteensä-rivin. Valmisteltu vuosidata
  annetaan taulukon muodostajalle, jotta rahavarausrivien :arvo-fn löytää arvonsa."
  [vuosidata]
  (let [rahavarausdata (mapv (fn [{:keys [data] :as vuosi}]
                          (let [nimet (konversio/pgarray->vector (:rahavaraus_nimet data))
                                arvot (konversio/pgarray->vector (:hoitokausi_yht_array data))]
                            (assoc vuosi
                              :rahavaraus-nimet nimet
                              :rahavaraus-arvot (zipmap nimet arvot))))
                    vuosidata)
        rahavarausnimet (into []
                          (distinct
                            (mapcat :rahavaraus-nimet rahavarausdata)))
        rahavarausrivit (mapv (fn [nimi]
                                {:otsikko nimi
                                 :arvo-fn #(get-in % [:rahavaraus-arvot nimi])})
                          rahavarausnimet)

        yhteensa {:lihavoi? true, :yhteensa? true, :otsikko "Yhteensä", :avain_yht :kaikki_rahavaraukset_val_yht, :avain_hoitokausi :kaikki_rahavaraukset_hoitokausi_yht}
        taulukon-tiedot (conj rahavarausrivit yhteensa)]
    [rahavarausdata taulukon-tiedot]))


(defn- tee-vuosittainen-taulukko
  "Rakentaa yhden laskutusyhteenvetoraportin vuosittaisen taulukon.

  Kutsu vuosijärjestyksessä olevalla vuosidatalla, otsikolla ja rivimääritysten
  sekvenssillä. Vuosimäpeissä käytetään sarakeotsikkona :otsikko-avainta ja
  rivien arvot luetaan :data-mäpistä tai rivimäärityksen :arvo-fn-funktiolla.
  Palauttaa raportin :taulukko-rakenteen sarakkeineen ja muodostettuine riveineen."
  [vuosidata otsikko rivimaaritykset]
  (let [sarakkeet (into [{:otsikko otsikko :leveys 36}]
                    (concat
                      (map (fn [{:keys [otsikko]}]
                             {:otsikko otsikko :leveys 16 :fmt :raha})
                        vuosidata)
                      [{:otsikko "Yhteensä" :leveys 16 :fmt :raha}]))
        rivit (mapv #(vuosittainen-rivi vuosidata %) rivimaaritykset)]
    [:taulukko {:sheet-nimi "Laskutusyhteenveto"
                :viimeinen-rivi-yhteenveto? true
                :pdf-optiot {:skaalaa-teksti? true}
                :oikealle-tasattavat-kentat (set (range 1 (+ 2 (count vuosidata))))}
     sarakkeet
     rivit]))


(defn taulukot-tyomaakokous-vuosittain
  "Muodostaa työmaakokouksen laskutusyhteenvetoraportin vuosittaiset taulukot.

  Vuosidata on vuosijärjestyksessä oleva sekvenssi mäpeistä, joissa on
  :hoitovuosi, :otsikko ja raportin luvut sisältävä :data-mäp.
  Urakan-alkuvuosi on vuosiluku, jota käytetään tavoitehintarivien valintaan.
  Palauttaa raportin taulukko- ja otsikkorakenteet; tyhjällä vuosidatalla
  palauttaa Ei hoitovuosia -tekstin sisältävän rakenteen."
  [vuosidata urakan-alkuvuosi]
  (if (empty? vuosidata)
    [[:teksti "Ei hoitovuosia."]]
    (let [[rahavaraus-vuosidata rahavarausrivit] (rahavaraus-taulukon-tiedot vuosidata)
          alkutaulukot (vec
                         (concat
                           [(tee-vuosittainen-taulukko vuosidata "Hankinnat" hankinnat-rivit)
                            (tee-vuosittainen-taulukko vuosidata "Hoidonjohto" hoidonjohto-rivit)]
                           (when (ominaisuus-kaytossa? :mhu-muutokset)
                             [(tee-vuosittainen-taulukko vuosidata "Muutokset" muutokset-rivit)])
                           [(tee-vuosittainen-taulukko rahavaraus-vuosidata "Rahavaraukset" rahavarausrivit)
                            (tee-vuosittainen-taulukko vuosidata "Muut tavoitehintaan vaikuttavat kulut"
                              (yhdista-rivimaaritykset
                                (map #(tavoitehintaan-vaikuttavat-rivit (:hoitovuosi %) urakan-alkuvuosi)
                                  vuosidata)))]))
          ei-tavoitehintaiset-taulukko
          (tee-vuosittainen-taulukko vuosidata "Kustannus"
            (yhdista-rivimaaritykset
              (map #(ei-tavoitehintaiset-rivit (:data %)) vuosidata)))
          taulukot (map-indexed (fn [indeksi taulukko]
                                  (assoc-in taulukko [1 :samalle-sheetille?] (pos? indeksi)))
                    (conj alkutaulukot ei-tavoitehintaiset-taulukko))
          [ennen ulkopuoliset] (split-at (dec (count taulukot)) taulukot)]
      (concat ennen
        [[:otsikko "Tavoitehinnan ulkopuoliset kustannukset"]]
        ulkopuoliset))))
