(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa
  "Työmaakokous laskutusyhteenveto MHU-urakoissa"
  (:require [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]

            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(def summa-fmt fmt/euro-opt)

(defn- rivi-taulukolle
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi?]
  (rivi
   [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}] 
   [:varillinen-teksti {:arvo (or (avain_hoitokausi tp-rivi) (summa-fmt nil)) :fmt :raha :lihavoi? lihavoi?}]
   (when kyseessa-kk-vali?
     [:varillinen-teksti {:arvo (or (avain_yht tp-rivi) (summa-fmt nil)) :fmt :raha :lihavoi? lihavoi?}])))

(defn- taulukko [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                         kyseessa-kk-vali? sheet-nimi]}]
  (let [rivit (into []
                    (remove nil?
                            (cond
                              (= "Hankinnat" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Talvihoito" :talvihoito_hoitokausi_yht :talvihoito_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Liikenneympäristön hoito" :lyh_hoitokausi_yht :lyh_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Sorateiden hoito" :sora_hoitokausi_yht :sora_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Päällystepaikkaukset" :paallyste_hoitokausi_yht :paallyste_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "MHU ylläpito" :yllapito_hoitokausi_yht :yllapito_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "MHU korvausinvestointi" :korvausinv_hoitokausi_yht :korvausinv_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Yhteensä" :hankinnat_hoitokausi_yht :hankinnat_val_aika_yht true)]

                              (= "Hoidonjohto" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Johto- ja hallintokorvaukset" :johtojahallinto_hoitokausi_yht :johtojahallinto_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Erillishankinnat" :erillishankinnat_hoitokausi_yht :erillishankinnat_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Hoidojohtopalkkio" :hjpalkkio_hoitokausi_yht :hjpalkkio_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Yhteensä" :hoidonjohto_hoitokausi_yht :hoidonjohto_val_aika_yht true)]

                              (= "Äkilliset hoitotyöt ja vahinkojen korjaukset" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Äkilliset hoitotyöt" :akilliset_hoitokausi_yht :akilliset_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Vahinkojen korjaukset" :vahingot_hoitokausi_yht :vahingot_val_aika_yht false)]

                              (= "Lisätyöt" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt (talvihoito)" :lisatyo_talvihoito_hoitokausi_yht :lisatyo_talvihoito_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt (liikenneympäristön hoito)" :lisatyo_lyh_hoitokausi_yht :lisatyo_lyh_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt (sorateiden hoito)" :lisatyo_sora_hoitokausi_yht :lisatyo_sora_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt (päällystepaikkaukset)" :lisatyo_paallyste_hoitokausi_yht :lisatyo_paallyste_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt (MHU korvausinvestointi)" :lisatyo_korvausinv_hoitokausi_yht :lisatyo_korvausinv_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Yhteensä" :lisatyot_hoitokausi_yht :lisatyot_val_aika_yht true)]

                              (= "Muut" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Bonukset" :bonukset_hoitokausi_yht :bonukset_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Sanktiot" :sanktiot_hoitokausi_yht :sanktiot_val_aika_yht false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Hoitovuoden päätös / Urakoitsija maksaa tavoitehinnan ylityksestä" :paatos_kattoh_ylitys_hoitokausi_yht :paatos_kattoh_ylitys_val_aika_yht false)]
                              )))]

    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? false
                :sheet-nimi sheet-nimi}

     (rivi
      {:otsikko otsikko
       :leveys 36}
      {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
      (when kyseessa-kk-vali?
        {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))

     rivit]))

(defn- kt-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari kustomi-tyyli]
  (rivi
   [:varillinen-teksti {:arvo ""}]
   [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
   [:varillinen-teksti {:itsepaisesti-maaritelty-oma-vari (or vari nil) :arvo (or (avain_hoitokausi tp-rivi) (summa-fmt nil)) :fmt :raha :lihavoi? lihavoi?}]
   (when kyseessa-kk-vali?
     (let [arvo (or (avain_yht tp-rivi) (summa-fmt nil))]
       [:varillinen-teksti {:kustomi-tyyli kustomi-tyyli :arvo arvo :fmt :raha :lihavoi? lihavoi?}]))))

(defn- kustannus-ja-tavoite-taulukko [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                             kyseessa-kk-vali?]}]
  (let [rivit (into []
                    (remove nil?
                            (cond
                              (= "Toteutuneet" otsikko)
                              [(kt-rivi data kyseessa-kk-vali? 
                                        "Toteutuneet tavoitehintaan vaikuttaneet kustannukset yhteensä" 
                                        :tavhin_hoitokausi_yht
                                        :tavhin_val_aika_yht
                                        true 
                                        nil
                                        "vahvistamaton")
                               
                               (kt-rivi data false "Tavoitehinta (indeksikorjattu)" :hoitokauden_tavoitehinta :hoitokauden_tavoitehinta true nil nil)
                               (kt-rivi data false "Siirto edelliseltä vuodelta" :hk_tavhintsiirto_ed_vuodelta :hk_tavhintsiirto_ed_vuodelta true "red" nil)
                               (kt-rivi data false "Budjettia jäljellä" :budjettia_jaljella :budjettia_jaljella true nil nil)
                               (kt-rivi data false "" :nil :nil false nil nil)
                               (kt-rivi data false "" :nil :nil false nil nil)]

                              :else
                              [(kt-rivi data kyseessa-kk-vali? "Muut kustannukset yhteensä" :muut_kustannukset_hoitokausi_yht :muut_kustannukset_val_aika_yht true nil "vahvistamaton")
                               (kt-rivi data false "" :nil :nil false nil nil)
                               (kt-rivi data false "" :nil :nil false nil nil)])))]

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

(defn- hh-rivi
  [tp-rivi valiotsikko summa1 summa2 lihavoi?]
  (let [arvo (if (summa1 tp-rivi)
               (+ (summa1 tp-rivi) (summa2 tp-rivi))
               (summa-fmt nil))]
    (rivi
     [:varillinen-teksti {:arvo ""}]
     [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
     [:varillinen-teksti {:arvo arvo :fmt :raha :lihavoi? lihavoi?}])))

(defn- hh-yhteensa-taulukko [{:keys [data kyseessa-kk-vali?]}]
  ;; Hankinnat ja hoidonjohto
  (let [rivit (into []
                    (remove nil?
                            [(hh-rivi data "Hankinnat ja hoidonjohto yhteensä" :hankinnat_hoitokausi_yht :hoidonjohto_hoitokausi_yht true)
                             (hh-rivi data "" :nil :nil false)
                             (hh-rivi data "" :nil :nil false)]))]

    [:taulukko {:piilota-border? true
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

(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id] :as parametrit}]
  (log/debug "TYOMAA PARAMETRIT: " (pr-str parametrit))
  (let [kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoitokauden alusta")
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm))

        ;; Konteksti ja urakkatiedot
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :urakka)

        {alueen-nimi :nimi} (first (if (= konteksti :hallintayksikko)
                                     (hallintayksikko-q/hae-organisaatio db hallintayksikko-id)
                                     (urakat-q/hae-urakka db urakka-id)))

        urakat (urakat-q/hae-urakkatiedot-laskutusyhteenvetoon
                db {:alkupvm alkupvm :loppupvm loppupvm
                    :hallintayksikkoid hallintayksikko-id :urakkaid urakka-id
                    :urakkatyyppi (name (:urakkatyyppi parametrit))})

        hoitokausi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm (first urakat)) alkupvm)

        urakoiden-parametrit (mapv #(assoc parametrit :urakka-id (:id %)
                                           :urakka-nimi (:nimi %)
                                           :indeksi (:indeksi %)
                                           :urakkatyyppi (:tyyppi %)) urakat)

        ;; Datan nostaminen tietokannasta urakoittain, hyödyntää cachea
        laskutusyhteenvedot (mapv (fn [urakan-parametrit]
                                    (mapv #(assoc % :urakka-id (:urakka-id urakan-parametrit)
                                                  :urakka-nimi (:urakka-nimi urakan-parametrit)
                                                  :indeksi (:indeksi urakan-parametrit)
                                                  :urakkatyyppi (:urakkatyyppi urakan-parametrit))
                                          (lyv-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot db user urakan-parametrit)))
                                  urakoiden-parametrit)

        [hk-alkupvm hk-loppupvm] (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                   ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                   ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                   (pvm/paivamaaran-hoitokausi alkupvm)
                                   [alkupvm loppupvm])

        rivitiedot (first (first laskutusyhteenvedot))
        otsikot ["Hankinnat" "Hoidonjohto"]]

    [:raportti {:nimi (str "Laskutusyhteenveto (" (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) ")")}
     [:otsikko (str alueen-nimi)]
     [:teksti ""]
     [:otsikko-iso "Tavoitehintaan vaikuttavat toteutuneet kustannukset"]

     (concat (for [x otsikot]
               (do
                 (taulukko {:data rivitiedot
                            :otsikko x
                            :sheet-nimi (when (= (.indexOf otsikot x) 0) "Työmaakokous")
                            :laskutettu-teksti laskutettu-teksti
                            :laskutetaan-teksti laskutetaan-teksti
                            :kyseessa-kk-vali? kyseessa-kk-vali?}))))

     (hh-yhteensa-taulukko {:data rivitiedot
                            :kyseessa-kk-vali? kyseessa-kk-vali?})

     (taulukko {:data rivitiedot
                :otsikko "Äkilliset hoitotyöt ja vahinkojen korjaukset"
                :laskutettu-teksti laskutettu-teksti
                :laskutetaan-teksti laskutetaan-teksti
                :kyseessa-kk-vali? kyseessa-kk-vali?})

     (kustannus-ja-tavoite-taulukko {:data rivitiedot
                                     :otsikko "Toteutuneet"
                                     :laskutettu-teksti laskutettu-teksti
                                     :laskutetaan-teksti laskutetaan-teksti
                                     :kyseessa-kk-vali? kyseessa-kk-vali?})

     [:otsikko "Muut toteutuneet kustannukset (ei lasketa tavoitehintaan)"]
     
     (let [otsikot ["Lisätyöt" "Muut"]]

       (concat (for [x otsikot]
                 (do
                   (taulukko {:data rivitiedot
                              :otsikko x
                              :laskutettu-teksti laskutettu-teksti
                              :laskutetaan-teksti laskutetaan-teksti
                              :kyseessa-kk-vali? kyseessa-kk-vali?})))))

     (kustannus-ja-tavoite-taulukko {:data rivitiedot
                                     :otsikko "Muut"
                                     :laskutettu-teksti laskutettu-teksti
                                     :laskutetaan-teksti laskutetaan-teksti
                                     :kyseessa-kk-vali? kyseessa-kk-vali?})

     [:tyomaa-laskutusyhteenveto-yhteensa (str (pvm/pvm hk-alkupvm) " - " (pvm/pvm hk-loppupvm))
      (fmt/formatoi-arvo-raportille (:yhteensa_kaikki_hoitokausi_yht rivitiedot))
      (fmt/formatoi-arvo-raportille (:yhteensa_kaikki_val_aika_yht rivitiedot))
      laskutettu-teksti laskutetaan-teksti]]))
