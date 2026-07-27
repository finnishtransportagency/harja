(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-tuotekohtainen
  "Tuotekohtaisen laskutusyhteenveto raportin taulukko rakenteet"
  (:require [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-yhteiset :refer [taulukko-rivi]]))


(defn- tuotekohtainen-rivit [data hoitokauden-alkuvuosi urakan-alkuvuosi av-validointi?]
  [{:lihavoi? false
    :otsikko "Hankinnat"
    :avain_yht :hankinnat_laskutetaan
    :avain_hoitokausi :hankinnat_laskutettu}

   ;; Saadaan näyttää, jos urakka alkaa 2025 tai aiemmillekin urakoille, mutta vasta -26 hoitovuonna, tai jos validointi on pois käytöstä
   (when (or (>= urakan-alkuvuosi 2025) (>= hoitokauden-alkuvuosi 2026) (not av-validointi?))
     {:lihavoi? false
      :otsikko "Arvonvähennykset"
      :avain_yht :arvonvahennykset_laskutetaan
      :avain_hoitokausi :arvonvahennykset_laskutettu})

   {:lihavoi? false
    :otsikko "Lisätyöt"
    :avain_yht :lisatyot_laskutetaan
    :avain_hoitokausi :lisatyot_laskutettu}

   ;; MHU25 urakoille ei näytetä bonuksia & sanktioita 
   (when-not (:onko_laskutusraja_kaytossa data)
     {:lihavoi? false
      :otsikko "Sanktiot"
      :avain_yht :sakot_laskutetaan
      :avain_hoitokausi :sakot_laskutettu})

   (when (yhteiset/raha-arvo-olemassa? (:jjh_muutokset_laskutettu data))
     {:lihavoi? false
      :otsikko "Johto-ja hallintokorvauksen muutokset"
      :avain_yht :jjh_muutokset_laskutetaan
      :avain_hoitokausi :jjh_muutokset_laskutettu})])


(defn- toteutuneet-rivit
  [data kyseessa-vapaa-aikavali?]
  [{:lihavoi? true
    :otsikko "Toteutuneet kustannukset yhteensä"
    :avain_yht :kaikki-yhteensa-laskutetaan
    :avain_hoitokausi :kaikki-yhteensa-laskutettu}

   {:lihavoi? true
    :otsikko "Tavoitehintaan vaikuttavat kustannukset yhteensä"
    :avain_yht :kaikki-tavoitehintaiset-laskutetaan
    :avain_hoitokausi :kaikki-tavoitehintaiset-laskutettu}

   (when-not kyseessa-vapaa-aikavali?
     {:lihavoi? true
      :otsikko "Hoitovuoden alun indeksikorjattu tavoitehinta"
      :avain_yht nil
      :avain_hoitokausi :hoitokauden-alun-indeksikorjattu-tavoitehinta})

   ;;   19-24 urakoilla on tavoitehinnan oikaisuja
   (when (and (not kyseessa-vapaa-aikavali?)
           (yhteiset/raha-arvo-olemassa? (:oikaisujen-maara data)))
     {:lihavoi? true
      :otsikko "Tavoitehinnan muutokset"
      :avain_yht nil
      :avain_hoitokausi :oikaisujen-maara})

   ;;   -25 urakoilla on kirjallisesti sovittuja pysyviä muutoksia
   (when (and (yhteiset/raha-arvo-olemassa? (:kirjallisesti-sovitut-muutokset data))
           (not kyseessa-vapaa-aikavali?))
     {:lihavoi? true
      :otsikko "Kirjallisesti sovitut muutokset"
      :avain_yht nil
      :avain_hoitokausi :kirjallisesti-sovitut-muutokset})

   (when-not kyseessa-vapaa-aikavali?
     {:lihavoi? true
      :otsikko "Budjettia jäljellä"
      :avain_yht nil
      :avain_hoitokausi :jaljella})])


(defn- mhu-hju-maaritykset [data]
  [{:lihavoi? false
    :otsikko "Johto- ja hallintokorvaukset"
    :avain_yht :johto_ja_hallinto_laskutetaan
    :avain_hoitokausi :johto_ja_hallinto_laskutettu}

   {:lihavoi? false
    :otsikko "Erillishankinnat"
    :avain_yht :hj_erillishankinnat_laskutetaan
    :avain_hoitokausi :hj_erillishankinnat_laskutettu}

   {:lihavoi? false
    :otsikko "HJ-palkkio"
    :avain_yht :hj_palkkio_laskutetaan
    :avain_hoitokausi :hj_palkkio_laskutettu}

   ;; MHU25 urakoille ei näytetä bonuksia & sanktioita 
   (when-not (:onko_laskutusraja_kaytossa data)
     {:lihavoi? false
      :otsikko "Bonukset"
      :avain_yht :bonukset_laskutetaan
      :avain_hoitokausi :bonukset_laskutettu})

   (when-not (:onko_laskutusraja_kaytossa data)
     {:lihavoi? false
      :otsikko "Sanktiot"
      :avain_yht :sakot_laskutetaan
      :avain_hoitokausi :sakot_laskutettu})

   ;; Hoitovuoden päättäminen, näytetään vain jos arvot olemassa
   (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päättäminen / Tavoitepalkkio"
      :avain_yht :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan
      :avain_hoitokausi :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu})

   (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä"
      :avain_yht :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan
      :avain_hoitokausi :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu})

   (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä"
      :avain_yht :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan
      :avain_hoitokausi :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu})

   (when (yhteiset/raha-arvo-olemassa? (:hj_paattaminen_hoidonjohtopalkkion_muutos_laskutettu data))
     {:lihavoi? false
      :otsikko "Hoitovuoden päättäminen / Hoidonjohtopalkkion muutos"
      :avain_yht :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutetaan
      :avain_hoitokausi :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutettu})])


(defn- tee-taulukko-rivi
  [data yhteiset-opts {:keys [otsikko] :as maaritys}]
  (taulukko-rivi data otsikko
    (merge yhteiset-opts
      (dissoc maaritys :otsikko))))


(defn- tee-taulukko-rivit
  [data yhteiset-opts rivit]
  (into []
    (comp
      (remove nil?)
      (map #(tee-taulukko-rivi data yhteiset-opts %)))
    rivit))


(defn toteutuneet-taulukko
  "Näkyy viimeisenä tuotekohtaisessa laskutusyhteenvedossa"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? kyseessa-hoitokausi-vali? kalenterivuosi?]}]
  (let [kyseessa-vapaa-aikavali? (and (not kyseessa-kk-vali?)
                                   (not kyseessa-hoitokausi-vali?))

        rivimaaritykset (if (= "Toteutuneet" otsikko)
                          (toteutuneet-rivit data kyseessa-vapaa-aikavali?)
                          nil)

        rivit (tee-taulukko-rivit
                data
                {:tyhja-alku? false :kyseessa-kk-vali? kyseessa-kk-vali?}
                rivimaaritykset)]

    [:taulukko {:piilota-border? true
                :viimeinen-rivi-yhteenveto? false
                :gridin-luokka "laskutusraja-grid"
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2}}
     (if kalenterivuosi?
       (rivi
         {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 36 :tyyppi :varillinen-teksti}
         {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 29 :tyyppi :varillinen-teksti})
       (rivi
         {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}
         {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
         (when kyseessa-kk-vali?
           {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti})))
     rivit]))


(defn- rahavaraus-rivit
  "Näytetään instanssin alla"
  [kyseessa-kk-vali? rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika]
  (map (fn [nimi hoitokausi val-aika]
         (rivi
           ;; Otsikko
           [:varillinen-teksti {:arvo (str nimi)
                                :lihavoi? false}]
           ;; Raha arvo
           [:varillinen-teksti {:fmt :raha
                                :lihavoi? false
                                :arvo (or hoitokausi (yhteiset/summa-fmt nil))}]
           ;; Valitun kuukauden raha arvo
           (when kyseessa-kk-vali?
             [:varillinen-teksti {:fmt :raha
                                  :lihavoi? false
                                  :arvo (or val-aika (yhteiset/summa-fmt nil))}])))
    rahavaraukset-nimet
    rahavaraukset-hoitokausi
    rahavaraukset-val-aika))


(defn- rivit-tuotekohtainen
  "Instanssitaulukkojen rivimääritykset"
  [data otsikko kyseessa-kk-vali? rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika
   hoitokauden-alkuvuosi urakan-alkuvuosi av-validointi?]
  (let [;; MHU ja HJU hoidon johto- taulukko,
        ;; jossa näytetään hieman muista instansseista poikkeavia lukuja
        mhu-hju-rivit (fn [data kyseessa-kk-vali?]
                        (tee-taulukko-rivit data
                          {:kyseessa-kk-vali? kyseessa-kk-vali?
                           :tyhja-arvo (yhteiset/summa-fmt 0.00M)}
                          (mhu-hju-maaritykset data)))

        taulukko-rivit (tee-taulukko-rivit data
                         {:kyseessa-kk-vali? kyseessa-kk-vali?
                          :tyhja-arvo (yhteiset/summa-fmt 0.00M)}
                         (tuotekohtainen-rivit data hoitokauden-alkuvuosi urakan-alkuvuosi av-validointi?))

        rahavaraus-rivit (rahavaraus-rivit
                           kyseessa-kk-vali?
                           rahavaraukset-nimet
                           rahavaraukset-hoitokausi
                           rahavaraukset-val-aika)

        ;; Yhteensä rivi näytetään kaikille
        yhteensa-rivi (tee-taulukko-rivit data
                        {:kyseessa-kk-vali? kyseessa-kk-vali?
                         :tyhja-arvo (yhteiset/summa-fmt 0.00M)}
                        [{:lihavoi? true
                          :yhteensa? true
                          :otsikko "Yhteensä"
                          :avain_yht :kaikki_laskutetaan
                          :avain_hoitokausi :kaikki_laskutettu}])]

    ;; Mergetä ja palauta rivit
    (if (= "MHU ja HJU hoidon johto" otsikko)
      (vec (concat (mhu-hju-rivit data kyseessa-kk-vali?) rahavaraus-rivit yhteensa-rivi))
      (vec (concat taulukko-rivit rahavaraus-rivit yhteensa-rivi)))))


(defn taulukko-tuotekohtainen
  "Instanssikohtaiset taulukot"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? sheet-nimi]} hoitokauden-alkuvuosi urakan-alkuvuosi av-validointi?]
  (let [rahavaraukset-nimet (konversio/pgarray->vector (:rahavaraus_nimet data))
        rahavaraukset-val-aika (konversio/pgarray->vector (:val_aika_yht_array data))
        rahavaraukset-hoitokausi (konversio/pgarray->vector (:hoitokausi_yht_array data))

        rivit (into []
                (remove nil?
                  (rivit-tuotekohtainen
                    data otsikko kyseessa-kk-vali? rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika
                    hoitokauden-alkuvuosi urakan-alkuvuosi av-validointi?)))]

    [:taulukko {:sheet-nimi sheet-nimi
                :viimeinen-rivi-yhteenveto? false
                :oikealle-tasattavat-kentat #{1 2}}

     (rivi
       {:otsikko otsikko :leveys 36}
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))
     rivit]))
