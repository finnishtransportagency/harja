(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit
  "Laskutusyhteenvedon taulukoiden apufunktiot"
  (:require [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]))


(defn- valitaulukko-rivi
  ([data otsikko]
   (valitaulukko-rivi data false otsikko nil nil false nil nil nil false))
  ([data kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli]
   (valitaulukko-rivi data kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli nil false))
  ([data kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli teksti-alle hk-alku-vahvistamaton?]
   (rivi
     [:varillinen-teksti {:lihavoi? lihavoi?
                          :arvo (str valiotsikko)
                          :teksti-alle teksti-alle}]
     [:varillinen-teksti {:arvo (or
                                  (and avain_hoitokausi (avain_hoitokausi data))
                                  (yhteiset/summa-fmt nil))
                          :fmt :raha
                          :lihavoi? lihavoi?
                          :kustomi-tyyli (when (and hk-alku-vahvistamaton? (not kyseessa-kk-vali?)) tyyli)
                          :itsepaisesti-maaritelty-oma-vari (or vari nil)}]
     (when kyseessa-kk-vali?
       (let [arvo (or
                    (and avain_yht (avain_yht data))
                    (yhteiset/summa-fmt nil))]
         [:varillinen-teksti {:arvo arvo
                              :fmt :raha
                              :kustomi-tyyli tyyli
                              :lihavoi? lihavoi?}])))))

(defn valitaulukko-tyomaa
  "Läpinäkyvä välitaulukko"
  [{:keys [data otsikko laskutettu-teksti
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
                    [(valitaulukko-rivi data (str otsikko " " (yhteiset/summa-fmt (:laskutusraja_yht data))))
                     (valitaulukko-rivi data kyseessa-kk-vali?
                       "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       :tavhin_hoitokausi_yht :tavhin_val_aika_yht true nil "vahvistamaton" nil true)

                     (valitaulukko-rivi data kyseessa-kk-vali?
                       "Laskutusrajaan jäljellä"
                       :laskutusrajaan_jaljella nil true nil nil)]

                    ;; -----------------------------
                    ;; Laskutusraja ylittynyt
                    (and
                      laskutusraja-ylittynyt?
                      (= otsikko "Laskutusraja"))
                    [(valitaulukko-rivi data (str otsikko " " (yhteiset/summa-fmt (:laskutusraja_yht data))))
                     (valitaulukko-rivi data kyseessa-kk-vali?
                       "Tavoitehintaan vaikuttavat kustannukset yhteensä"
                       :tavhin_hoitokausi_yht :tavhin_val_aika_yht true nil nil)

                     ;; -----------------------------
                     (valitaulukko-rivi data kyseessa-kk-vali?
                       "• josta laskutettavaa (sisältyy laskutusrajaan)"
                       :laskutusraja_laskutettavaa_yht :laskutusraja_laskutettavaa_val_aika false nil nil)

                     (valitaulukko-rivi data kyseessa-kk-vali?
                       "• josta laskutusrajan ylittäviä kustannuksia"
                       :laskutusrajan_ylittynyt_yht :laskutusrajan_ylittynyt_val_aika false nil
                       "vahvistamaton" "(ei laskutusoikeutta ennen välikatselmuksen päätöksiä)" false)]

                    ;; -----------------------------
                    ;; Toteutuneet kustannukset 
                    (= "Toteutuneet" otsikko)
                    [(when (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                       (valitaulukko-rivi data false
                         "Hoitovuoden alun indeksikorjattu tavoitehinta"
                         :hoitovuoden_alun_indkorj_tavoitehinta :hoitovuoden_alun_indkorj_tavoitehinta true nil nil))

                     ;;  19-24 urakoilla on tavoitehinnan oikaisuja
                     (when (yhteiset/raha-arvo-olemassa? (:tavoitehinta_oikaisu_summa data))
                       (valitaulukko-rivi data false "Tavoitehinnan muutokset" :tavoitehinta_oikaisu_summa nil true nil nil))

                     ;; -25 urakoilla on tavoitehinnan pysyviä muutoksia - niitä sanotaan kirjallisesti sovituiksi  muutoksiksi
                     (when-not (= kirjallisesti-sovitut-muutokset 0.0M)
                       (valitaulukko-rivi data false "Kirjallisesti sovitut muutokset" :pysyvat_muutokset_hoitokausi_yht nil true nil nil))

                     (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true nil nil)

                     (when (and (yhteiset/raha-arvo-olemassa? (:budjettia_jaljella data)) (not kyseessa-vapaa-aikavali?))
                       (valitaulukko-rivi data false "Budjettia jäljellä" :budjettia_jaljella :budjettia_jaljella true nil nil))

                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)]

                    ;; -----------------------------
                    ;; Tavoitehinnan ulkopuoliset viimeisenä 
                    :else
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan ulkopuoliset kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true nil "vahvistamaton")
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)])))]

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


(defn- toteutuneet-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli]
  (rivi
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:itsepaisesti-maaritelty-oma-vari (or vari nil)
                         :arvo (or (avain_hoitokausi tp-rivi) (yhteiset/summa-fmt nil))
                         :fmt :raha
                         :lihavoi? lihavoi?}]

    (when kyseessa-kk-vali?
      (let [arvo (or (get tp-rivi avain_yht) (yhteiset/summa-fmt nil))]
        [:varillinen-teksti {:kustomi-tyyli tyyli :arvo arvo :fmt :raha :lihavoi? lihavoi?}]))))


(defn toteutuneet-valitaulukko-tuotekohtainen [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                                                       kyseessa-kk-vali? kyseessa-hoitokausi-vali?]}]
  (let [kyseessa-vapaa-aikavali? (and (not kyseessa-kk-vali?) (not kyseessa-hoitokausi-vali?))
        rivit (into []
                (remove nil?
                  (cond
                    (= "Toteutuneet" otsikko)
                    [(toteutuneet-rivi data kyseessa-kk-vali? "Toteutuneet kustannukset yhteensä" :kaikki-yhteensa-laskutettu :kaikki-yhteensa-laskutetaan true nil nil)
                     (toteutuneet-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :kaikki-tavoitehintaiset-laskutettu :kaikki-tavoitehintaiset-laskutetaan true nil nil)
                     (when-not kyseessa-vapaa-aikavali?
                       (toteutuneet-rivi data kyseessa-kk-vali? "Hoitovuoden alun indeksikorjattu tavoitehinta" :hoitokauden-alun-indeksikorjattu-tavoitehinta nil true nil nil))
                     ;;   19-24 urakoilla on tavoitehinnan oikaisuja
                     (when (and (not kyseessa-vapaa-aikavali?) (yhteiset/raha-arvo-olemassa? (:oikaisujen-maara data))) (toteutuneet-rivi data kyseessa-kk-vali? "Tavoitehinnan muutokset" :oikaisujen-maara nil true nil nil))
                     ;;   -25 urakoilla on kirjallisesti sovittuja pysyviä muutoksia
                     (when (and (yhteiset/raha-arvo-olemassa? (:kirjallisesti-sovitut-muutokset data)) (not kyseessa-vapaa-aikavali?))
                       (toteutuneet-rivi data kyseessa-kk-vali? "Kirjallisesti sovitut muutokset" :kirjallisesti-sovitut-muutokset nil true nil nil))
                     (when-not kyseessa-vapaa-aikavali? (toteutuneet-rivi data kyseessa-kk-vali? "Budjettia jäljellä" :jaljella nil true nil nil))]

                    :else
                    ;;  Tuotekohtainen -> tavoitehinta
                    [(toteutuneet-rivi data kyseessa-kk-vali? "Tavoite / jäljellä" :tavoite-hinta :jaljella true nil nil)
                     (toteutuneet-rivi data false "" :nil :nil false nil nil)
                     (toteutuneet-rivi data false "" :nil :nil false nil nil)])))]

    [:taulukko {:piilota-border? true
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2 3}
                :viimeinen-rivi-yhteenveto? false}
     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
       {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))
