(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen
  "Tuotekohtainen laskutusyhteenveto MHU-urakoissa"
  (:require [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(def summa-fmt fmt/euro-opt)

(defn raha-arvo-olemassa? [arvo]
  (not (or (= arvo 0.0M) (nil? arvo))))

(defn- laskettavat-kentat [rivi konteksti]
  (let [kustannusten-kentat (into []
                                  (apply concat [(lyv-yhteiset/kustannuslajin-kaikki-kentat "lisatyot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "johto_ja_hallinto")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_erillishankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitepalkkio")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_kattohinnan_ylitys")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_palkkio")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "tavoitehintaiset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                                 (when (= :urakka konteksti) [:tpi :maksuera_numero])]))]
    kustannusten-kentat))

(defn- koosta-yhteenveto [tiedot]
  (let [kaikki-yhteensa-laskutettu (apply + (map #(:kaikki_laskutettu %) tiedot))
        kaikki-yhteensa-laskutetaan (apply + (map #(:kaikki_laskutetaan %) tiedot))
        kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))
        kaikki-tavoitehintaiset-laskutetaan (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutetaan %)))
                                                             (:tavoitehintaiset_laskutetaan %)
                                                             0) tiedot))]
    {:kaikki-tavoitehintaiset-laskutettu kaikki-tavoitehintaiset-laskutettu
     :kaikki-tavoitehintaiset-laskutetaan kaikki-tavoitehintaiset-laskutetaan
     :kaikki-yhteensa-laskutettu kaikki-yhteensa-laskutettu
     :kaikki-yhteensa-laskutetaan kaikki-yhteensa-laskutetaan
     :nimi "Kaikki toteutuneet kustannukset"}))

(defn- koosta-tavoite [tiedot urakka-tavoite]
  (let [kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))]
    (if urakka-tavoite
      {:tavoite-hinta (or (:tavoitehinta-oikaistu urakka-tavoite) 0M)
       :jaljella (- (or (:tavoitehinta-oikaistu urakka-tavoite) 0M) kaikki-tavoitehintaiset-laskutettu)
       :nimi "Tavoite"}
      {:tavoite-hinta 0
       :jaljella 0
       :nimi "Tavoite"})))

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
                              (= "MHU ja HJU hoidon johto" otsikko)
                              [(rivi-taulukolle data kyseessa-kk-vali? "Johto- ja hallintakorvaukset" :johto_ja_hallinto_laskutettu :johto_ja_hallinto_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Erillishankinnat" :hj_erillishankinnat_laskutettu :hj_erillishankinnat_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "HJ-palkkio" :hj_palkkio_laskutettu :hj_palkkio_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Bonukset" :bonukset_laskutettu :bonukset_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Sanktiot" :sakot_laskutettu :sakot_laskutetaan false)
                               ;; Hoitovuoden päättäminen / Tavoitepalkkio
                               ;; Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä
                               ;; Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä (?)
                               (rivi-taulukolle data kyseessa-kk-vali? "Yhteensä" :kaikki_laskutettu :kaikki_laskutetaan true)]

                              :else
                              [(rivi-taulukolle data kyseessa-kk-vali? "Hankinnat" :hankinnat_laskutettu :hankinnat_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Lisätyöt" :lisatyot_laskutettu :lisatyot_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Sanktiot" :sakot_laskutettu :sakot_laskutetaan false)
                               (rivi-taulukolle data kyseessa-kk-vali? "Yhteensä" :kaikki_laskutettu :kaikki_laskutetaan true)])))]

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

(defn- toteutuneet-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi? vari kustomi-tyyli]
  (rivi
   [:varillinen-teksti {:arvo ""}]
   [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
   [:varillinen-teksti {:itsepaisesti-maaritelty-oma-vari (or vari nil) :arvo (or (avain_hoitokausi tp-rivi) (summa-fmt nil)) :fmt :raha :lihavoi? lihavoi?}]
   (when kyseessa-kk-vali?
     (let [arvo (or (avain_yht tp-rivi) (summa-fmt nil))]
       [:varillinen-teksti {:kustomi-tyyli kustomi-tyyli :arvo arvo :fmt :raha :lihavoi? lihavoi?}]))))

(defn- toteutuneet-taulukko [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
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



(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id aikarajaus valittu-kk] :as parametrit}]
  (log/debug "Tuotekohtainen PARAMETRIT: " (pr-str parametrit))
  (let [kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoitokauden alusta")
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm))
        ;; Aina jos valittuna koko vuosi / vuoden kuukausi, näytetään vain yksi sarake source: trust me bro
        ;; Halutaanko näyttää tietyn vuoden data
        koko-vuosi? (and (= aikarajaus :kalenterivuosi) (nil? valittu-kk))
        ;; Halutaanko näyttää tietyn vuoden tietty kk
        vuoden-kk? (and (= aikarajaus :kalenterivuosi) (not (nil? valittu-kk)))
        ;; Ei näytetä kahta saraketta jos halutaan näyttää tietyn vuoden kuukausi
        kyseessa-kk-vali? (if vuoden-kk? false kyseessa-kk-vali?)
        ;; Vaihdetaan "Hoitokauden alusta"- teksti jos näytetään tiettyä kuukautta
        laskutettu-teksti (if vuoden-kk? (str "Laskutetaan " (pvm/kuukausi-ja-vuosi (first valittu-kk))) laskutettu-teksti)
        ;; Käytetäänkö omaa aikaväliä
        valittu-aikavali? (= aikarajaus :valittu-aikakvali)
        ;; Jos näytetään tietyn vuoden dataa, tai omaa aikaväliä, sarakkeen otsikko on vain "Määrä"
        laskutettu-teksti (if (or koko-vuosi? valittu-aikavali?) "Määrä" laskutettu-teksti)
        
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
        urakka-tavoite (first (filter #(= (:hoitokausi %) hoitokausi) (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})))

        urakoiden-parametrit (mapv #(assoc parametrit :urakka-id (:id %)
                                           :urakka-nimi (:nimi %)
                                           :indeksi (:indeksi %)
                                           :urakkatyyppi (:tyyppi %)) urakat)

        ;; Datan nostaminen tietokannasta urakoittain, hyödyntää cachea
        laskutusyhteenvedot (mapv (fn [urakan-parametrit]
                                    ;; (println "\n params: " urakan-parametrit)
                                    (mapv #(assoc % :urakka-id (:urakka-id urakan-parametrit)
                                                  :urakka-nimi (:urakka-nimi urakan-parametrit)
                                                  :indeksi (:indeksi urakan-parametrit)
                                                  :urakkatyyppi (:urakkatyyppi urakan-parametrit))
                                          (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot db user urakan-parametrit koko-vuosi? vuoden-kk? valittu-aikavali?)))
                                  urakoiden-parametrit)

        tiedot-tuotteittain (fmap #(group-by :nimi %) laskutusyhteenvedot)
        kaikki-tuotteittain (apply merge-with concat tiedot-tuotteittain)
        kaikki-tuotteittain-summattuna (when kaikki-tuotteittain
                                         (fmap #(apply merge-with (fnil + 0 0)
                                                       (map (fn [rivi]
                                                              (select-keys rivi (laskettavat-kentat rivi konteksti)))
                                                            %))
                                               kaikki-tuotteittain))

        tiedot (into [] (map #(merge {:nimi (key %)} (val %)) kaikki-tuotteittain-summattuna))
        yhteenveto (koosta-yhteenveto tiedot)
        tavoite (koosta-tavoite tiedot urakka-tavoite)
        koostettu-yhteenveto (conj [] yhteenveto tavoite)

        sheet-nimi "Työmaakokous"
        otsikot ["Talvihoito"
                 "Liikenneympäristön hoito"
                 "Soratien hoito"
                 "Päällyste"
                 "MHU Ylläpito"
                 "MHU Korvausinvestointi"
                 "MHU ja HJU hoidon johto"]]

    [:raportti {:nimi (str "Laskutusyhteenveto (" (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) ")")
                :otsikon-koko :iso}
     [:otsikko-heading-small (str alueen-nimi)]

     ;; Data on vectorina järjestyksessä, käytetään 'otsikot' indeksiä oikean datan näyttämiseen  
     (concat (for [x otsikot]
               (do
                 (let [tiedot-indeksi (.indexOf otsikot x)]
                   (taulukko {:data (nth (first laskutusyhteenvedot) tiedot-indeksi)
                              :otsikko x
                              :sheet-nimi (when (= (.indexOf otsikot x) 0) sheet-nimi)
                              :laskutettu-teksti laskutettu-teksti
                              :laskutetaan-teksti laskutetaan-teksti
                              :kyseessa-kk-vali? kyseessa-kk-vali?})))))

     (toteutuneet-taulukko {:data (first koostettu-yhteenveto)
                            :otsikko "Toteutuneet"
                            :laskutettu-teksti laskutettu-teksti
                            :laskutetaan-teksti laskutetaan-teksti
                            :kyseessa-kk-vali? kyseessa-kk-vali?})

     [:jakaja ""]

     (toteutuneet-taulukko {:data (second koostettu-yhteenveto)
                            :otsikko ""
                            :laskutettu-teksti "Tavoitehinta"
                            :laskutetaan-teksti "Budjettia jäljellä"
                            :kyseessa-kk-vali? true})]))
