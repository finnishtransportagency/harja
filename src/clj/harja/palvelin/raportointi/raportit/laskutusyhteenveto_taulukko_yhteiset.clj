(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-yhteiset
  "Laskutusyhteenvedon läpinäkyvä taulukko + yhteiset apurit"
  (:require [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]))

(def ^:private taulukko-rivi-oletukset
  {:vari nil
   :tyyli nil
   :avain_yht nil
   :lihavoi? false
   :tyhja-alku? false
   :avain_hoitokausi nil
   :kyseessa-kk-vali? false
   :tyhja-arvo (yhteiset/summa-fmt nil)})

(def ^:private valitaulukko-rivi-oletukset
  {:vari nil
   :tyyli nil
   :avain_yht nil
   :lihavoi? false
   :teksti-alle nil
   :aikavali? false
   :avain_hoitokausi nil
   :kyseessa-kk-vali? false
   :hk-alku-vahvistamaton? false})


(defn- raha-sarake [data avain {:keys [lihavoi? vari tyyli tyhja-arvo yhteensa?]}]
  [:varillinen-teksti
   (cond-> {:fmt :raha
            :lihavoi? lihavoi?
            :korosta-hennosti? yhteensa?
            :arvo (or (and avain (avain data)) tyhja-arvo)}
     vari (assoc :itsepaisesti-maaritelty-oma-vari vari)
     tyyli (assoc :kustomi-tyyli tyyli))])


(defn taulukko-rivi
  [data valiotsikko opts]
  (let [{:keys [kyseessa-kk-vali? avain_hoitokausi avain_yht lihavoi? tyhja-alku? yhteensa?]
         :as opts} (merge taulukko-rivi-oletukset opts)]
    (rivi
      ;; Yhteenvetoon tulee tyhjä sarake
      (when tyhja-alku? [:varillinen-teksti {:arvo ""}])
      ;; Otsikko
      [:varillinen-teksti {:arvo (str valiotsikko)
                           :lihavoi? lihavoi?
                           :korosta-hennosti? yhteensa?}]
      ;; Eurot 
      (raha-sarake data avain_hoitokausi opts)
      ;; Valitun kuukauden eurot 
      (when kyseessa-kk-vali? (raha-sarake data avain_yht opts)))))


(defn- valitaulukko-vasen-sarake
  "Ensimmäinen sarake eli otsikko, esim < 'Hankinnat'  ... >"
  [valiotsikko {:keys [lihavoi? teksti-alle aikavali?]}]
  [:varillinen-teksti {:lihavoi? lihavoi?
                       :teksti-alle teksti-alle
                       :arvo (when-not aikavali? (str valiotsikko))}])


(defn- valitaulukko-oikea-sarake
  "Oikeanpuoleinen sarake eli eurot"
  [data valiotsikko {:keys [kyseessa-kk-vali? avain_hoitokausi
                            lihavoi? vari tyyli hk-alku-vahvistamaton? aikavali?]}]
  [:varillinen-teksti {:arvo (if aikavali?
                               (str valiotsikko)
                               (or
                                 ;; Näytä oikealla aikaväli jos valittu oma aikaväli
                                 (and avain_hoitokausi (avain_hoitokausi data))
                                 (yhteiset/summa-fmt nil)))

                       :lihavoi? lihavoi?
                       :fmt (if aikavali? :string :raha)
                       :itsepaisesti-maaritelty-oma-vari vari
                       :kustomi-tyyli (when (and hk-alku-vahvistamaton? (not kyseessa-kk-vali?)) tyyli)}])


(defn- valitaulukko-kk-sarake
  "Jos valittu kk väli, tässä kuukauden eurot"
  [data {:keys [kyseessa-kk-vali? avain_yht tyyli lihavoi?]}]
  (when kyseessa-kk-vali?
    [:varillinen-teksti {:arvo (or (and avain_yht (avain_yht data))
                                 (yhteiset/summa-fmt nil))
                         :fmt :raha
                         :lihavoi? lihavoi?
                         :kustomi-tyyli tyyli}]))


(defn- valitaulukko-rivi
  [data valiotsikko & [opts]]
  (let [opts (merge valitaulukko-rivi-oletukset opts)]
    (rivi
      (valitaulukko-vasen-sarake valiotsikko opts)
      (valitaulukko-oikea-sarake data valiotsikko opts)
      (valitaulukko-kk-sarake data opts))))


(defn lapinakyva-taulukko
  "Läpinäkyvä välitaulukko"
  [tuotekohtainen?
   {:keys [data otsikko laskutettu-teksti valittu-aikavali? aikavali
           laskutetaan-teksti kyseessa-kk-vali? kyseessa-hoitokausi-vali? vapaa-aikavali-teksti laskutusraja-tarkistettu?]}]

  (let [laskutusraja-ylittynyt? (boolean (:onko_laskutusraja_ylittynyt data))
        kyseessa-vapaa-aikavali? (and (not kyseessa-kk-vali?) (not kyseessa-hoitokausi-vali?))
        kirjallisesti-sovitut-muutokset (+
                                          (or (:pysyvat_muutokset_hoitokausi_yht data) 0)
                                          (or (:muutostyo_hoitokausi_yht data) 0))
        data (assoc data :pysyvat_muutokset_hoitokausi_yht kirjallisesti-sovitut-muutokset)
        rivit (into []
                (remove nil?
                  (cond
                    ;; -----------------------------
                    ;; Laskutusraja ei ylittynyt
                    (and
                      (not laskutusraja-ylittynyt?)
                      (= otsikko "Laskutusraja"))
                    [;; Kun valittu oma aikaväli, laskutusrajaa ei näytetä
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data (str (if laskutusraja-tarkistettu? "Tarkistettu laskutusraja" otsikko) " " (yhteiset/summa-fmt (:laskutusraja_yht data)))))

                     ;; Tuotekohtaiseen tulee tällainen
                     (when tuotekohtainen?
                       (valitaulukko-rivi data "Toteutuneet kustannukset yhteensä"
                         {:lihavoi? true
                          :tyyli "vahvistamaton"
                          :hk-alku-vahvistamaton? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_yht :kaikki-yhteensa-laskutetaan
                          :avain_hoitokausi :kaikki-yhteensa-laskutettu}))

                     (valitaulukko-rivi data "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       {:lihavoi? true
                        :tyyli "vahvistamaton"
                        :hk-alku-vahvistamaton? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :tavhin_val_aika_yht
                        :avain_hoitokausi :tavhin_hoitokausi_yht})

                     ;; Kun valittu oma aikaväli, laskutusrajaa ei näytetä
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data "Laskutusrajaan jäljellä"
                         {:lihavoi? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_hoitokausi :laskutusrajaan_jaljella}))]

                    ;; -----------------------------
                    ;; Laskutusraja ylittynyt
                    (and
                      laskutusraja-ylittynyt?
                      (= otsikko "Laskutusraja"))
                    [;; Kun valittu oma aikaväli, laskutusrajaa ei näytetä
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data (str (if laskutusraja-tarkistettu? "Tarkistettu laskutusraja" otsikko) " " (yhteiset/summa-fmt (:laskutusraja_yht data)))))

                     ;; Tuotekohtaiseen tulee tällainen
                     (when tuotekohtainen?
                       (valitaulukko-rivi data "Toteutuneet kustannukset yhteensä"
                         {:lihavoi? true
                          :tyyli "vahvistamaton"
                          :hk-alku-vahvistamaton? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_yht :kaikki-yhteensa-laskutetaan
                          :avain_hoitokausi :kaikki-yhteensa-laskutettu}))

                     (valitaulukko-rivi data "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       {:lihavoi? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :tavhin_val_aika_yht
                        :avain_hoitokausi :tavhin_hoitokausi_yht})

                     ;; -----------------------------
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data "• josta laskutettavaa (sisältyy laskutusrajaan)"
                         {:tyyli "vahvistamaton"
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_yht :laskutusraja_laskutettavaa_val_aika
                          :avain_hoitokausi :laskutusraja_laskutettavaa_yht}))

                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data "• josta laskutusrajan ylittäviä kustannuksia"
                         {:kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_yht :laskutusrajan_ylittynyt_val_aika
                          :avain_hoitokausi :laskutusrajan_ylittynyt_yht
                          ;; Tässä 'välikatselmus' sanan pelkkä olemassaolo muuntaa sen linkiksi  
                          :teksti-alle "(ei laskutusoikeutta ennen välikatselmuksen päätöksiä)"}))]

                    ;; -----------------------------
                    ;; Toteutuneet kustannukset 
                    (= "Toteutuneet" otsikko)
                    [(when (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                       (valitaulukko-rivi data "Hoitovuoden alun indeksikorjattu tavoitehinta"
                         {:lihavoi? true
                          :avain_yht :hoitovuoden_alun_indkorj_tavoitehinta
                          :avain_hoitokausi :hoitovuoden_alun_indkorj_tavoitehinta}))

                     ;;  19-24 urakoilla on tavoitehinnan oikaisuja
                     (when (yhteiset/raha-arvo-olemassa? (:tavoitehinta_oikaisu_summa data))
                       (valitaulukko-rivi data "Tavoitehinnan muutokset"
                         {:lihavoi? true
                          :avain_hoitokausi :tavoitehinta_oikaisu_summa}))

                     ;; -25 urakoilla on tavoitehinnan pysyviä muutoksia - niitä sanotaan kirjallisesti sovituiksi  muutoksiksi
                     (when-not (= kirjallisesti-sovitut-muutokset 0.0M)
                       (valitaulukko-rivi data "Kirjallisesti sovitut muutokset"
                         {:lihavoi? true
                          :avain_hoitokausi :pysyvat_muutokset_hoitokausi_yht}))

                     (valitaulukko-rivi data "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       {:lihavoi? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :tavhin_val_aika_yht
                        :avain_hoitokausi :tavhin_hoitokausi_yht})

                     (when (and
                             (yhteiset/raha-arvo-olemassa? (:budjettia_jaljella data))
                             (not kyseessa-vapaa-aikavali?))
                       (valitaulukko-rivi data "Budjettia jäljellä"
                         {:lihavoi? true
                          :avain_yht :budjettia_jaljella
                          :avain_hoitokausi :budjettia_jaljella}))

                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})
                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})]

                    ;; -----------------------------
                    ;; Tavoitehinnan ulkopuoliset viimeisenä 
                    :else
                    [(valitaulukko-rivi data "Tavoitehinnan ulkopuoliset kustannukset yhteensä"
                       {:lihavoi? true
                        :tyyli "vahvistamaton"
                        :hk-alku-vahvistamaton? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :muut_kustannukset_val_aika_yht
                        :avain_hoitokausi :muut_kustannukset_hoitokausi_yht})

                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})
                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})])))]

    [:taulukko {:piilota-border? true
                :viimeinen-rivi-yhteenveto? false
                :oikealle-tasattavat-kentat #{1 2}
                :gridin-luokka "laskutusraja-grid"
                :raportin-tunniste :tyomaa-yhteenveto}
     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}
       {:otsikko (if valittu-aikavali? (or vapaa-aikavali-teksti aikavali) laskutettu-teksti)
        :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))
