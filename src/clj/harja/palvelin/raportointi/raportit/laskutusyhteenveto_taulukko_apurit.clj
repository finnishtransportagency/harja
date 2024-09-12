(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit
  "Laskutusyhteenvedon taulukoiden apufunktiot"
   (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
             [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]))


(defn- valitaulukko-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli]
  (rivi
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:itsepaisesti-maaritelty-oma-vari (or vari nil)
                         :arvo (or (avain_hoitokausi tp-rivi) (yhteiset/summa-fmt nil))
                         :fmt :raha
                         :lihavoi? lihavoi?}]

    (when kyseessa-kk-vali?
      (let [arvo (or (avain_yht tp-rivi) (yhteiset/summa-fmt nil))]
        [:varillinen-teksti {:kustomi-tyyli tyyli
                             :arvo arvo
                             :fmt :raha
                             :lihavoi? lihavoi?}]))))


(defn valitaulukko
  "Työmaakokous välitaulukko ilman tyylejä"
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali?]}]
  (let [rivit (into []
                (remove nil?
                  (cond
                    (= "Toteutuneet" otsikko)
                    [(valitaulukko-rivi data false "Hankinnat ja hoidonjohto yhteensä" :hankinnat_hoitokausi_yht :hoidonjohto_hoitokausi_yht true nil "vahvistamaton")
                     (valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehintaan vaikuttavat kustannukset yhteensä" :tavhin_hoitokausi_yht :tavhin_val_aika_yht true nil "vahvistamaton")

                     ;; Nätetään arvot vain jos on olemassa
                     (when (yhteiset/raha-arvo-olemassa? (:hoitokauden_tavoitehinta data))
                       (valitaulukko-rivi data false "Tavoitehinta (indeksikorjattu)" :hoitokauden_tavoitehinta :hoitokauden_tavoitehinta true nil nil))

                     (when (yhteiset/raha-arvo-olemassa? (:hk_tavhintsiirto_ed_vuodelta data))
                       (valitaulukko-rivi data false "Siirto edelliseltä vuodelta" :hk_tavhintsiirto_ed_vuodelta :hk_tavhintsiirto_ed_vuodelta true "red" nil))

                     (when (yhteiset/raha-arvo-olemassa? (:budjettia_jaljella data))
                       (valitaulukko-rivi data false "Budjettia jäljellä" :budjettia_jaljella :budjettia_jaljella true nil nil))

                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)]

                    :else
                    ;; Alin välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Tavoitehinnan ulkopuoliset kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true nil "vahvistamaton")
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)])))]

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
      (let [arvo (or (avain_yht tp-rivi) (yhteiset/summa-fmt nil))]
        [:varillinen-teksti {:kustomi-tyyli tyyli :arvo arvo :fmt :raha :lihavoi? lihavoi?}]))))


(defn toteutuneet-valitaulukko [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                                         kyseessa-kk-vali?]}]
  (let [rivit (into []
                (remove nil?
                  (cond
                    (= "Toteutuneet" otsikko)
                    [(toteutuneet-rivi data kyseessa-kk-vali? "Toteutuneet kustannukset yhteensä" :kaikki-yhteensa-laskutettu :kaikki-yhteensa-laskutetaan true nil "vahvistamaton")
                     (toteutuneet-rivi data kyseessa-kk-vali? "Toteutuneet kustannukset, jotka kuuluvat tavoitehintaan" :kaikki-tavoitehintaiset-laskutettu :kaikki-tavoitehintaiset-laskutetaan true nil nil)

                     (toteutuneet-rivi data false "" :nil :nil false nil nil)
                     (toteutuneet-rivi data false "" :nil :nil false nil nil)]

                    :else
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
