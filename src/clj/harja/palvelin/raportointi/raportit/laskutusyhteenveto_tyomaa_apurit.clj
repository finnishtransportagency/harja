(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa-apurit
  "Työmaakokous laskutusyhteenvedon apufunktiot"
   (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
             [harja.fmt :as fmt]))

(def summa-fmt fmt/euro-opt)

(defn raha-arvo-olemassa? [arvo]
  (not (or (= arvo 0.0M) (nil? arvo))))


(defn- hoidonjohto-valitaulukko-rivi
  [tp-rivi valiotsikko summa1 summa2 lihavoi?]
  (let [arvo (if (summa1 tp-rivi)
               (+ (summa1 tp-rivi) (summa2 tp-rivi))
               (summa-fmt nil))]
    (rivi
      [:varillinen-teksti {:arvo ""}]
      [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
      [:varillinen-teksti {:arvo arvo :fmt :raha :lihavoi? lihavoi?}])))


(defn hoidonjohto-valitaulukko
  "Hankinnat ja hoidonjohto yhteensä"
  [{:keys [data kyseessa-kk-vali?]}]
  (let [rivit (into []
                (remove nil?
                  [(hoidonjohto-valitaulukko-rivi data "Hankinnat ja hoidonjohto yhteensä" :hankinnat_hoitokausi_yht :hoidonjohto_hoitokausi_yht true)
                   (hoidonjohto-valitaulukko-rivi data "" :nil :nil false)
                   (hoidonjohto-valitaulukko-rivi data "" :nil :nil false)]))]

    [:taulukko {:piilota-border? true
                :hoitokausi-arvotaulukko? true
                :raportin-tunniste :tyomaa-yhteenveto
                :oikealle-tasattavat-kentat #{1 2 3}
                :viimeinen-rivi-yhteenveto? false}

     (rivi
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 48 :tyyppi :varillinen-teksti}
       {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 33 :tyyppi :varillinen-teksti}))
     rivit]))


(defn- valitaulukko-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari tyyli]
  (rivi
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:itsepaisesti-maaritelty-oma-vari (or vari nil)
                         :arvo (or (avain_hoitokausi tp-rivi) (summa-fmt nil))
                         :fmt :raha
                         :lihavoi? lihavoi?}]

    (when kyseessa-kk-vali?
      (let [arvo (or (avain_yht tp-rivi) (summa-fmt nil))]
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

                    ;; Rahavarausten alla oleva välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali?
                       "Toteutuneet tavoitehintaan vaikuttaneet kustannukset yhteensä"
                       :tavhin_hoitokausi_yht
                       :tavhin_val_aika_yht
                       true
                       nil
                       "vahvistamaton")

                               ;; Nätetään arvot vain jos on olemassa
                     (when (raha-arvo-olemassa? (:hoitokauden_tavoitehinta data))
                       (valitaulukko-rivi data false "Tavoitehinta (indeksikorjattu)" :hoitokauden_tavoitehinta :hoitokauden_tavoitehinta true nil nil))

                     (when (raha-arvo-olemassa? (:hk_tavhintsiirto_ed_vuodelta data))
                       (valitaulukko-rivi data false "Siirto edelliseltä vuodelta" :hk_tavhintsiirto_ed_vuodelta :hk_tavhintsiirto_ed_vuodelta true "red" nil))

                     (when (raha-arvo-olemassa? (:budjettia_jaljella data))
                       (valitaulukko-rivi data false "Budjettia jäljellä" :budjettia_jaljella :budjettia_jaljella true nil nil))

                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)]

                    :else
                    ;; Alin välitaulukko
                    [(valitaulukko-rivi data kyseessa-kk-vali? "Muut kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true nil "vahvistamaton")
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)
                     (valitaulukko-rivi data false "" :nil :nil false nil nil)])))]

    [:taulukko {:piilota-border? true
                :hoitokausi-arvotaulukko? true
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
