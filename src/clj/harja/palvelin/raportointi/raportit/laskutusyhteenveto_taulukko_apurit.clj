(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit
  "Laskutusyhteenvedon taulukoiden apufunktiot"
  (:require [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]))

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

(def ^:private toteutuneet-rivi-oletukset
  {:vari nil
   :tyyli nil
   :avain_yht nil
   :lihavoi? false
   :avain_hoitokausi nil
   :kyseessa-kk-vali? false})


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
           laskutetaan-teksti kyseessa-kk-vali? kyseessa-hoitokausi-vali? vapaa-aikavali-teksti]}]

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
                    [;; Näytä oikealla valittu aikaväli
                     (when valittu-aikavali?
                       (valitaulukko-rivi data aikavali
                         {:lihavoi? true
                          :aikavali? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?}))

                     ;; Kun valittu oma aikaväli, laskutusrajaa ei näytetä
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data
                         (str otsikko " " (yhteiset/summa-fmt (:laskutusraja_yht data)))))

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
                    [;; Näytä oikealla valittu aikaväli
                     (when valittu-aikavali?
                       (valitaulukko-rivi data aikavali
                         {:lihavoi? true
                          :aikavali? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?}))

                     ;; Kun valittu oma aikaväli, laskutusrajaa ei näytetä
                     (when-not valittu-aikavali?
                       (valitaulukko-rivi data
                         (str otsikko " " (yhteiset/summa-fmt (:laskutusraja_yht data)))))

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
                    [;; Näytä oikealla valittu aikaväli
                     (when valittu-aikavali?
                       (valitaulukko-rivi data aikavali
                         {:lihavoi? true
                          :aikavali? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?}))

                     (valitaulukko-rivi data "Tavoitehinnan ulkopuoliset kustannukset yhteensä"
                       {:lihavoi? true
                        :tyyli "vahvistamaton"
                        :hk-alku-vahvistamaton? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :muut_kustannukset_val_aika_yht
                        :avain_hoitokausi :muut_kustannukset_hoitokausi_yht})

                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})
                     (valitaulukko-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})])))]

    [:taulukko {:piilota-border? true
                :gridin-luokka "laskutusraja-grid"
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? false}
     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}
       {:otsikko (if kyseessa-vapaa-aikavali? vapaa-aikavali-teksti laskutettu-teksti)
        :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))


;; -------------------------------------------------
;; Tuotekohtaiseen niputetaan hieman erillä tavalla 
(defn- toteutuneet-otsikko-sarake
  "Ensimmäinen sarake eli otsikko, esim < 'Talvihoito'  ... >"
  [valiotsikko {:keys [lihavoi?]}]
  [:varillinen-teksti {:arvo (str valiotsikko)
                       :lihavoi? lihavoi?}])


(defn- toteutuneet-hoitokausi-sarake
  "Oikeanpuoleinen sarake eli eurot"
  [tp-rivi {:keys [avain_hoitokausi lihavoi? vari]}]
  [:varillinen-teksti {:fmt :raha
                       :lihavoi? lihavoi?
                       :itsepaisesti-maaritelty-oma-vari vari
                       :arvo (or (and avain_hoitokausi (avain_hoitokausi tp-rivi))
                               (yhteiset/summa-fmt nil))}])


(defn- toteutuneet-kk-sarake
  "Jos valittu kk väli, tässä kuukauden eurot"
  [tp-rivi {:keys [kyseessa-kk-vali? avain_yht tyyli lihavoi?]}]
  (when kyseessa-kk-vali?
    [:varillinen-teksti {:fmt :raha
                         :lihavoi? lihavoi?
                         :kustomi-tyyli tyyli
                         :arvo (or (and avain_yht (get tp-rivi avain_yht))
                                 (yhteiset/summa-fmt nil))}]))


(defn- toteutuneet-rivi
  [tp-rivi valiotsikko & [opts]]
  (let [opts (merge toteutuneet-rivi-oletukset opts)]
    (rivi
      [:varillinen-teksti {:arvo ""}]
      (toteutuneet-otsikko-sarake valiotsikko opts)
      (toteutuneet-hoitokausi-sarake tp-rivi opts)
      (toteutuneet-kk-sarake tp-rivi opts))))


(defn tuotekohtainen-toteutuneet-taulukko
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? kyseessa-hoitokausi-vali?]}]
  (let [kyseessa-vapaa-aikavali? (and (not kyseessa-kk-vali?)
                                   (not kyseessa-hoitokausi-vali?))
        rivit (into []
                (remove nil?
                  (cond
                    (= "Toteutuneet" otsikko)
                    [(toteutuneet-rivi data "Toteutuneet kustannukset yhteensä"
                       {:lihavoi? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :kaikki-yhteensa-laskutetaan
                        :avain_hoitokausi :kaikki-yhteensa-laskutettu})

                     (toteutuneet-rivi data "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       {:lihavoi? true
                        :kyseessa-kk-vali? kyseessa-kk-vali?
                        :avain_yht :kaikki-tavoitehintaiset-laskutetaan
                        :avain_hoitokausi :kaikki-tavoitehintaiset-laskutettu})

                     (when-not kyseessa-vapaa-aikavali?
                       (toteutuneet-rivi data "Hoitovuoden alun indeksikorjattu tavoitehinta"
                         {:lihavoi? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_hoitokausi :hoitokauden-alun-indeksikorjattu-tavoitehinta}))

                     ;;   19-24 urakoilla on tavoitehinnan oikaisuja
                     (when (and (not kyseessa-vapaa-aikavali?)
                             (yhteiset/raha-arvo-olemassa? (:oikaisujen-maara data)))
                       (toteutuneet-rivi data "Tavoitehinnan muutokset"
                         {:lihavoi? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_hoitokausi :oikaisujen-maara}))

                     ;;   -25 urakoilla on kirjallisesti sovittuja pysyviä muutoksia
                     (when (and (yhteiset/raha-arvo-olemassa? (:kirjallisesti-sovitut-muutokset data))
                             (not kyseessa-vapaa-aikavali?))
                       (toteutuneet-rivi data "Kirjallisesti sovitut muutokset"
                         {:lihavoi? true
                          :kyseessa-kk-vali? kyseessa-kk-vali?
                          :avain_hoitokausi :kirjallisesti-sovitut-muutokset}))

                     (when-not kyseessa-vapaa-aikavali?
                       (toteutuneet-rivi data "Budjettia jäljellä"
                         {:lihavoi? true
                          :avain_hoitokausi :jaljella
                          :kyseessa-kk-vali? kyseessa-kk-vali?}))]

                    :else
                    ;;  Tuotekohtainen -> tavoitehinta
                    [(toteutuneet-rivi data "Tavoite / jäljellä"
                       {:lihavoi? true
                        :avain_yht :jaljella
                        :avain_hoitokausi :tavoite-hinta
                        :kyseessa-kk-vali? kyseessa-kk-vali?})

                     (toteutuneet-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})
                     (toteutuneet-rivi data "" {:avain_yht :nil :avain_hoitokausi :nil})])))]

    [:taulukko {:piilota-border? true
                :viimeinen-rivi-yhteenveto? false
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2 3}}
     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
       {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))
