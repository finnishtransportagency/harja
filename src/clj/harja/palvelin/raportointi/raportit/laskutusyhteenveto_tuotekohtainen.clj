(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen
  "Tuotekohtainen laskutusyhteenveto MHU-urakoissa"
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]

            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-yhteiset :as taulukot]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-tuotekohtainen :as apurit]))


(defn- laskettavat-kentat [konteksti]
  (let [kustannusten-kentat (into []
                              (apply concat [[:laskutusraja_yht
                                              :laskutusrajaan_jaljella
                                              :onko_laskutusraja_kaytossa
                                              :onko_laskutusraja_ylittynyt
                                              :laskutusraja_laskutettavaa_yht
                                              :laskutusraja_laskutettavaa_val_aika
                                              :laskutusrajan_ylittynyt_yht
                                              :laskutusrajan_ylittynyt_val_aika
                                              :laskutettavaa_kaikki_yht
                                              :laskutettavaa_kaikki_val_aika]
                                             (yhteiset/kustannuslajin-kaikki-kentat "lisatyot")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hankinnat")
                                             (yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                             (yhteiset/kustannuslajin-kaikki-kentat "johto_ja_hallinto")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_erillishankinnat")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitepalkkio")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_kattohinnan_ylitys")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_paattaminen_hoidonjohtopalkkion_muutos")
                                             (yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_palkkio")
                                             (yhteiset/kustannuslajin-kaikki-kentat "tavoitehintaiset")
                                             (yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                             (when (= :urakka konteksti) [:tpi :maksuera_numero])]))]
    kustannusten-kentat))


(defn- koosta-yhteenveto [tiedot valikatselmus-siirrot-ed-vuodelta]
  (let [laskutusraja-yht (or (some :laskutusraja_yht tiedot) 0M)
        tiedot (mapv
                 (fn [rivi kaytetty-yht]
                   (assoc rivi
                     :laskutusraja_yht laskutusraja-yht
                     :laskutusrajaan_jaljella
                     (max 0M (- laskutusraja-yht kaytetty-yht))))
                 tiedot
                 (rest
                   (reductions
                     + 0M
                     (map #(or (:kaikki_laskutettu %) 0M) tiedot))))

        onko_laskutusraja_kaytossa (:onko_laskutusraja_kaytossa (last tiedot))
        laskutusraja_yht (:laskutusraja_yht (last tiedot))
        laskutusrajaan_jaljella (:laskutusrajaan_jaljella (last tiedot))
        onko_laskutusraja_ylittynyt (<= laskutusrajaan_jaljella 0.0M)
        laskutusraja-yht (or (:laskutusraja_yht (last tiedot)) 0M)

        summaa (fn [k]
                 (apply + (map #(or (k %) 0M) tiedot)))

        laskutettavaa-yht-raw
        (summaa :laskutusraja_laskutettavaa_yht)

        laskutettavaa-val-aika-raw
        (summaa :laskutusraja_laskutettavaa_val_aika)

        laskutusraja_laskutettavaa_yht
        (min laskutusraja-yht laskutettavaa-yht-raw)

        laskutusraja_laskutettavaa_val_aika
        (min laskutusraja-yht laskutettavaa-val-aika-raw)

        laskutusrajan_ylittynyt_yht
        (max 0M (- laskutettavaa-yht-raw laskutusraja-yht))

        laskutusrajan_ylittynyt_val_aika
        (max 0M (- laskutettavaa-val-aika-raw laskutusraja-yht))

        kaikki-yhteensa-laskutettu (apply + (keep #(:kaikki_laskutettu %) tiedot))
        kaikki-yhteensa-laskutetaan (apply + (keep #(:kaikki_laskutetaan %) tiedot))
        kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))
        kaikki-tavoitehintaiset-laskutetaan (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutetaan %)))
                                                             (:tavoitehintaiset_laskutetaan %)
                                                             0) tiedot))]
    {:hk_valikatselmus_siirrot_ed_vuodelta valikatselmus-siirrot-ed-vuodelta
     ;; Lisätään välikatselmuksen kulujen siirrot laskutettuihin kuluihin (Lisätään hoitokauden alusta lähtien kokonaissuummaan)
     ;; Hoitokauden alusta
     :kaikki-tavoitehintaiset-laskutettu (+ kaikki-tavoitehintaiset-laskutettu valikatselmus-siirrot-ed-vuodelta)
     ;; Kk-välin laskutus
     :kaikki-tavoitehintaiset-laskutetaan kaikki-tavoitehintaiset-laskutetaan

     ;; Hoitokauden alusta
     :kaikki-yhteensa-laskutettu (+ kaikki-yhteensa-laskutettu valikatselmus-siirrot-ed-vuodelta)
     ;; KK-välin laskutus
     :kaikki-yhteensa-laskutetaan kaikki-yhteensa-laskutetaan
     :nimi "Kaikki toteutuneet kustannukset"
     ;; Laskutusraja 
     :laskutusraja_yht laskutusraja_yht
     :laskutusrajaan_jaljella laskutusrajaan_jaljella
     :onko_laskutusraja_kaytossa onko_laskutusraja_kaytossa
     :onko_laskutusraja_ylittynyt onko_laskutusraja_ylittynyt
     :laskutusraja_laskutettavaa_yht laskutusraja_laskutettavaa_yht
     :laskutusraja_laskutettavaa_val_aika laskutusraja_laskutettavaa_val_aika
     :laskutusrajan_ylittynyt_yht laskutusrajan_ylittynyt_yht
     :laskutusrajan_ylittynyt_val_aika laskutusrajan_ylittynyt_val_aika}))


(defn- koosta-tavoite [tiedot urakka-tavoite valikatselmus-siirrot-ed-vuodelta]
  (let [kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))
        oikaistu? (and
                    (some? (:tavoitehinta-oikaistu urakka-tavoite))
                    (some? (:tavoitehinta-indeksikorjattu urakka-tavoite))
                    (not= (:tavoitehinta-oikaistu urakka-tavoite) (:tavoitehinta-indeksikorjattu urakka-tavoite)))
        oikaisujen-maara (if (and (:tavoitehinta-oikaistu urakka-tavoite) (:tavoitehinta-indeksikorjattu urakka-tavoite))
                           (- (:tavoitehinta-oikaistu urakka-tavoite) (:tavoitehinta-indeksikorjattu urakka-tavoite))
                           0)
        pysyvat-muutokset-summa (or (:muutos-summa urakka-tavoite) 0M)
        hoitovuoden-lopun-tavoitehinta (+ (or (:tavoitehinta-oikaistu urakka-tavoite) (:tavoitehinta-indeksikorjattu urakka-tavoite) 0M) pysyvat-muutokset-summa)
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (when (:tavoitehinta-oikaistu urakka-tavoite) (- (:tavoitehinta-oikaistu urakka-tavoite) oikaisujen-maara))]

    (if urakka-tavoite
      {:hoitokauden-alun-indeksikorjattu-tavoitehinta (or hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu urakka-tavoite) 0M)
       :oikaisujen-maara oikaisujen-maara
       :kirjallisesti-sovitut-muutokset pysyvat-muutokset-summa
       :jaljella (- hoitovuoden-lopun-tavoitehinta kaikki-tavoitehintaiset-laskutettu valikatselmus-siirrot-ed-vuodelta)
       :oikaistu? oikaistu?}
      {:hoitokauden-alun-indeksikorjattu-tavoitehinta 0
       :jaljella 0
       :kirjallisesti-sovitut-muutokset nil
       :oikaisujen-maara nil
       :oikaistu? oikaistu?})))


(defn suorita [db user {:keys [alkupvm loppupvm urakka-id elinvoimakeskus-id aikarajaus valittu-kk] :as parametrit}]
  (log/debug "Tuotekohtainen PARAMETRIT: " (pr-str parametrit))
  (let [kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoitovuoden alusta")
        laskutetaan-teksti (str (pvm/kuukausi-isolla (pvm/kuukausi alkupvm)) " " (pvm/vuosi alkupvm))
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
        ;; Ei käytetä kk-väliä jos oma aikaväli valittuna
        kyseessa-kk-vali? (if valittu-aikavali? false kyseessa-kk-vali?)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        ;; Jos näytetään tietyn vuoden dataa, tai omaa aikaväliä, sarakkeen otsikko on vain "Määrä"
        laskutettu-teksti (if (or koko-vuosi? valittu-aikavali?) "Määrä" laskutettu-teksti)
        ;; Kun koko hoitokausi on valittu ja loppupvm on myöhemmin kuin kuluva päivä, käytetään kuluvaa päivää
        ;; Muuten laskutusyhteenveto alkaa "ennustamaan" kustannuksia tulevaisuudesta.
        parametrit (assoc parametrit :haun-loppupvm (if (and
                                                          (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
                                                          (pvm/ennen? (pvm/nyt) loppupvm))
                                                      (pvm/nyt)
                                                      loppupvm))
        ;; Konteksti ja urakkatiedot
        konteksti (cond
                    urakka-id :urakka
                    elinvoimakeskus-id :elinvoimakeskus
                    :else :urakka)

        {alueen-nimi :nimi} (first (if (= konteksti :elinvoimakeskus)
                                     (hallintayksikko-q/hae-organisaatio db elinvoimakeskus-id)
                                     (urakat-q/hae-urakka db urakka-id)))

        urakat (urakat-q/hae-urakkatiedot-laskutusyhteenvetoon
                 db {:alkupvm alkupvm :loppupvm loppupvm
                     :elinvoimakeskus-id elinvoimakeskus-id :urakkaid urakka-id
                     :urakkatyyppi (name (:urakkatyyppi parametrit))})

        hoitokausi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm (first urakat)) alkupvm)
        urakka-tavoite (first (filter #(= (:hoitokausi %) hoitokausi) (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})))
        hoitokausi (pvm/paivamaaran-hoitokausi alkupvm)
        valikatselmus-siirrot-ed-vuodelta (budjetti-q/hae-valikatselmus-siirrot-ed-vuodelta db {:urakka urakka-id :alkupvm (first hoitokausi)})

        urakoiden-parametrit (mapv #(assoc parametrit
                                      :urakka-id (:id %)
                                      :urakka-nimi (:nimi %)
                                      :indeksi (:indeksi %)
                                      :urakkatyyppi (:tyyppi %)) urakat)

        ;; Datan nostaminen tietokannasta urakoittain, hyödyntää cachea
        laskutusyhteenvedot (mapv (fn [urakan-parametrit]
                                    (mapv #(assoc %
                                             :urakka-id (:urakka-id urakan-parametrit)
                                             :urakka-nimi (:urakka-nimi urakan-parametrit)
                                             :indeksi (:indeksi urakan-parametrit)
                                             :urakkatyyppi (:urakkatyyppi urakan-parametrit))
                                      (yhteiset/hae-laskutusyhteenvedon-tiedot db user urakan-parametrit koko-vuosi? vuoden-kk? valittu-aikavali?)))
                              urakoiden-parametrit)
        perusluku (when urakka-id (:perusluku (ffirst laskutusyhteenvedot)))
        indeksikertoimet (when urakka-id (bs/hae-urakan-indeksikertoimet db user {:urakka-id urakka-id}))
        tiedot-tuotteittain (fmap #(group-by :nimi %) laskutusyhteenvedot)
        kaikki-tuotteittain (apply merge-with concat tiedot-tuotteittain)

        kaikki-tuotteittain-summattuna (when kaikki-tuotteittain
                                         (fmap #(apply merge-with (fnil + 0 0)
                                                  (map (fn [rivi]
                                                         (select-keys rivi (laskettavat-kentat konteksti)))
                                                    %))
                                           kaikki-tuotteittain))

        tiedot (into [] (map #(merge {:nimi (key %)} (val %)) kaikki-tuotteittain-summattuna))
        yhteenveto (koosta-yhteenveto tiedot valikatselmus-siirrot-ed-vuodelta)
        tavoite (koosta-tavoite tiedot urakka-tavoite valikatselmus-siirrot-ed-vuodelta)
        koostettu-yhteenveto (conj [] yhteenveto tavoite)
        rivitiedot (merge (first koostettu-yhteenveto) (second koostettu-yhteenveto))

        rivitiedot (assoc rivitiedot
                     :tavhin_hoitokausi_yht (-> koostettu-yhteenveto first :kaikki-yhteensa-laskutettu)
                     :tavhin_val_aika_yht (-> koostettu-yhteenveto first :kaikki-yhteensa-laskutetaan)
                     :onko_laskutusraja_ylittynyt (-> koostettu-yhteenveto first :onko_laskutusraja_ylittynyt)
                     :laskutusrajan_ylittynyt_yht (-> koostettu-yhteenveto first :laskutusrajan_ylittynyt_yht)
                     :laskutusrajan_ylittynyt_val_aika (-> koostettu-yhteenveto first :laskutusrajan_ylittynyt_val_aika))

        sheet-nimi "Tuotekohtainen"
        otsikot [["Talvihoito" "alvi"]
                 ["Liikenneympäristön hoito" "ympä"]
                 ["Soratien hoito" "sora"]
                 ["Päällysteiden paikkaus" "pääl"]
                 ["MHU Ylläpito" "yllä"]
                 ["MHU hoidon johto" "johto"]
                 ["MHU Korvausinvestointi" "korv"]]

        ;; Etsitään otsikon indeksi Toimenpideinstanssin nimen osan peruteella
        etsi-indeksi (fn [otsikon-osa rivit]
                       (let [indeksi (some #(when-not (nil? %) %)
                                       (map-indexed
                                         (fn [i rivi]
                                           (when (str/includes? (str/lower-case (:nimi rivi)) otsikon-osa)
                                             i))
                                         rivit))]
                         indeksi))]

    [:raportti {:nimi (str "Laskutusyhteenveto (" (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) ")")
                :otsikon-koko :keskikoko}
     [:otsikko-heading-small (str alueen-nimi)]

     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))
     (when (or kyseessa-hoitokausi-vali? kyseessa-kk-vali?)
       (yleinen/urakan-hoitokauden-indeksikerroin {:indeksikertoimet indeksikertoimet
                                                   :hoitokausi (pvm/paivamaaran-hoitokausi alkupvm)}))

     ;; Data on vectorina järjestyksessä
     (concat (for [otsikko otsikot]
               (let [tiedot-indeksi (etsi-indeksi (second otsikko) (first laskutusyhteenvedot))
                     data (try
                            (nth (first laskutusyhteenvedot) tiedot-indeksi)
                            (catch Throwable _t
                              (log/debug "Tuotekohtaisen laskutusyhteenvedon tietoja ei löytynyt.")
                              nil))]

                 (apurit/taulukko-tuotekohtainen {:data data
                                                  :otsikko (first otsikko)
                                                  :sheet-nimi (when (= (.indexOf otsikot otsikko) 0) sheet-nimi)
                                                  :laskutettu-teksti laskutettu-teksti
                                                  :laskutetaan-teksti laskutetaan-teksti
                                                  :kyseessa-kk-vali? kyseessa-kk-vali?
                                                  :alkupvm alkupvm}))))

     (if (-> koostettu-yhteenveto first :onko_laskutusraja_kaytossa)
       (taulukot/lapinakyva-taulukko true
         {:data rivitiedot
          :otsikko "Laskutusraja"
          :aikavali "Hoitovuoden alusta"
          :valittu-aikavali? valittu-aikavali?
          :laskutettu-teksti laskutettu-teksti
          :laskutetaan-teksti laskutetaan-teksti
          :kyseessa-kk-vali? kyseessa-kk-vali?})

       (apurit/toteutuneet-taulukko
         {:data rivitiedot
          :otsikko "Toteutuneet"
          :laskutettu-teksti laskutettu-teksti
          :laskutetaan-teksti laskutetaan-teksti
          :kyseessa-kk-vali? kyseessa-kk-vali?
          :kyseessa-hoitokausi-vali? kyseessa-hoitokausi-vali?}))]))
