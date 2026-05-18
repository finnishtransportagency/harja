(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit
  "Laskutusyhteenvedon taulukoiden apufunktiot"
   (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
             [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
             [clojure.string :as cstr]))


(defn- valitaulukko-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? lihavoi-summa? vari tyyli]
    (rivi
      [:varillinen-teksti {:arvo ""}]
      [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
      [:varillinen-teksti
           {:itsepaisesti-maaritelty-oma-vari (or vari nil)
            :arvo (or (avain_hoitokausi tp-rivi) (yhteiset/summa-fmt nil))
            :fmt :raha
            :lihavoi? lihavoi-summa?
            :kustomi-tyyli (if-not kyseessa-kk-vali? tyyli "")}]

      (when kyseessa-kk-vali?
        (let [arvo (or (avain_yht tp-rivi) (yhteiset/summa-fmt nil))]
          [:varillinen-teksti {:kustomi-tyyli tyyli
                               :arvo arvo
                               :fmt :raha
                               :lihavoi? lihavoi-summa?}]))))

;; NOTE: Tätä käytetään jos urakalla on laskutusraja käytössä eli MHU urakat vuodesta 2025 eteenpäin
(defn valitaulukko-laskutusraja
  "Työmaakokous välitaulukko laskutusrajalla"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? kyseessa-valittu-aikavali? laskutusraja laskutusraja-ylittynyt?]}]
  (let [rivit (into []
                (remove nil?
                  (cond
                    (= "Laskutusraja" otsikko)
                      (if kyseessa-valittu-aikavali?
                        [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true true nil nil)]
                        (if-not laskutusraja-ylittynyt?
                          [(valitaulukko-rivi data false (str "Laskutusraja " (yhteiset/summa-fmt laskutusraja)) :nil :nil false true nil nil)
                           (valitaulukko-rivi data kyseessa-kk-vali?  "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true true nil "vahvistamaton")
                           (valitaulukko-rivi data false "Laskutusrajaan jäljellä" :laskutusrajaan_jaljella "" true true nil nil)]
                          [(valitaulukko-rivi data false (str "Laskutusraja " (yhteiset/summa-fmt laskutusraja)) :nil :nil false true nil "vahvistamaton")
                           (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true true nil nil)
                           (valitaulukko-rivi data kyseessa-kk-vali?  (str "- josta laskutettavaa (sisältyy laskutusrajaan)") :hk_laskutusraja :kk_sallittu_laskutusosuus false true nil "vahvistamaton")
                           (valitaulukko-rivi data kyseessa-kk-vali?  "- josta laskutusrajan ylittäviä kustannuksia (ei laskutusoikeutta ennen välikatselmuksen päätöksiä)" :laskutusrajan_ylittynyt_osuus :tavhin_val_aika_yht false true nil nil)]))
                    :else
                    ;; Alin välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan ulkopuoliset kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true true nil "vahvistamaton")
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)])))]
    [:taulukko {:piilota-border? true
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{2 3}
                :viimeinen-rivi-yhteenveto? false}
     (rivi
       {:otsikko "" :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko "" :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
       {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))

;; NOTE: Tätä käytetään pääasiassa työmaakokouksen laskutusyhteenvedossa
(defn valitaulukko-tyomaa
  "Työmaakokous välitaulukko ilman tyylejä"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? kyseessa-hoitokausi-vali? vapaa-aikavali-teksti]}]
  (let [kyseessa-vapaa-aikavali? (and (not kyseessa-kk-vali?) (not kyseessa-hoitokausi-vali?))
        kirjallisesti-sovitut-muutokset (+ (or (:pysyvat_muutokset_hoitokausi_yht data) 0) (or (:muutostyo_hoitokausi_yht data) 0))
        data (assoc data :pysyvat_muutokset_hoitokausi_yht kirjallisesti-sovitut-muutokset)
        _ (println "kyseessa-kk-vali?" kyseessa-kk-vali?)
        _ (println "kyseessa-hoitokausi-vali?" kyseessa-hoitokausi-vali?)
        rivit (into []
                (remove nil?
                  (cond
                    (= "Toteutuneet" otsikko)
                    [(when (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                       (valitaulukko-rivi data kyseessa-kk-vali? "Hoitovuoden alun indeksikorjattu tavoitehinta" :hoitovuoden_alun_indkorj_tavoitehinta :hoitovuoden_alun_indkorj_tavoitehinta true true nil nil))
                     ;; Jätän tämän kommentteihin, koska voi olla, että lisätään pikaisesti takaisin
                     #_ (when (yhteiset/raha-arvo-olemassa? (:hk_valikatselmus_siirrot_ed_vuodelta data))
                       (valitaulukko-rivi data false "Siirto edelliseltä vuodelta" :hk_valikatselmus_siirrot_ed_vuodelta nil true nil nil))

                     ;;   19-24 urakoilla on tavoitehinnan oikaisuja
                     (when (yhteiset/raha-arvo-olemassa? (:tavoitehinta_oikaisu_summa data))

                       (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan muutokset" :tavoitehinta_oikaisu_summa nil false true nil nil))
                     ;;   -25 urakoilla on tavoitehinnan pysyviä muutoksia - niitä sanotaan kirjallisesti sovituiksi  muutoksiksi
                     (when-not (= kirjallisesti-sovitut-muutokset 0.0M)
                       (valitaulukko-rivi data kyseessa-kk-vali? "Kirjallisesti sovitut muutokset" :pysyvat_muutokset_hoitokausi_yht :nil false true nil nil))
                     (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht false true nil nil)
                     (when (and (yhteiset/raha-arvo-olemassa? (:budjettia_jaljella data)) (not kyseessa-vapaa-aikavali?))
                       (valitaulukko-rivi data kyseessa-kk-vali? "Budjettia jäljellä" :budjettia_jaljella :budjettia_jaljella false true nil nil))

                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)]

                    :else
                    ;; Alin välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan ulkopuoliset kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true true nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)])))]

    [:taulukko {:piilota-border? true
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2 3}
                :viimeinen-rivi-yhteenveto? false}
     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
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

;; NOTE: Tätä käytetään pääasiassa tuotekohtaisessa laskutusyteenvedossa
(defn toteutuneet-valitaulukko-tuotekohtainen [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                                                       kyseessa-kk-vali? kyseessa-hoitokausi-vali?]}]
  (println "toteutuneet-valitaulukko-tuotekohtainen")
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

(defn yhteenveto-laskutusraja-tuotekohtainen
  "Työmaakokous välitaulukko laskutusrajalla"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? kyseessa-valittu-aikavali? laskutusraja laskutusraja-ylittynyt? laskutusraha-erotus]}]
  (let [rivit (into []
                (remove nil?
                  (cond
                    (= "Laskutusraja" otsikko)
                    (if kyseessa-valittu-aikavali?
                      [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true true nil nil)]
                      (if-not laskutusraja-ylittynyt?
                        [(valitaulukko-rivi data false (str "Laskutusraja " (yhteiset/summa-fmt laskutusraja)) :nil :nil false true nil nil)
                         (valitaulukko-rivi data kyseessa-kk-vali?  "Toteutuneet kustannukset yhteensä" :kaikki-yhteensa-laskutettu :kaikki-yhteensa-laskutetaan true true nil nil)
                         (valitaulukko-rivi data kyseessa-kk-vali?  "Tavoitehintaan vaikuttavat kustannukset yhteensä" :kaikki-tavoitehintaiset-laskutettu :kaikki-tavoitehintaiset-laskutetaan true true nil nil)
                         (valitaulukko-rivi data false "Laskutusrajaan jäljellä" :asd "" true true nil nil)]
                        [(valitaulukko-rivi data false (str "Laskutusraja " (yhteiset/summa-fmt laskutusraja)) :nil :nil false true nil "vahvistamaton")
                         (valitaulukko-rivi data kyseessa-kk-vali? "Toteutuneet kustannukset yhteensä" :kaikki-yhteensa-laskutettu :kaikki-yhteensa-laskutetaan true true nil nil)
                         (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :kaikki-tavoitehintaiset-laskutettu :kaikki-tavoitehintaiset-laskutetaan true true nil nil)
                         (valitaulukko-rivi data kyseessa-kk-vali? (str "- josta laskutettavaa (sisältyy laskutusrajaan)") :hk_laskutusraja :kk_sallittu_laskutusosuus false true nil "vahvistamaton")
                         (valitaulukko-rivi data kyseessa-kk-vali? "- josta laskutusrajan ylittäviä kustannuksia (ei laskutusoikeutta ennen välikatselmuksen päätöksiä)" :laskutusrajan_ylittynyt_osuus :tavhin_val_aika_yht false true nil nil)]))
                    :else
                    ;; Alin välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan ulkopuoliset kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true true nil "vahvistamaton")
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false true nil nil)])))]
    [:taulukko {:piilota-border? true
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{2 3}
                :viimeinen-rivi-yhteenveto? false}
     (rivi
       {:otsikko "" :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko "" :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
       {:otsikko laskutettu-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))
