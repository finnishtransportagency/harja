(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen
  "Tuotekohtainen laskutusyhteenveto MHU-urakoissa"
  (:require [clojure.string :as str]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit :as taulukot]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.pvm :as pvm]))


(defn- laskettavat-kentat [konteksti]
  (let [kustannusten-kentat (into []
                              (apply concat [(yhteiset/kustannuslajin-kaikki-kentat "lisatyot")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hankinnat")
                                             (yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                             (yhteiset/kustannuslajin-kaikki-kentat "johto_ja_hallinto")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_erillishankinnat")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitepalkkio")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_kattohinnan_ylitys")
                                             (yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                             (yhteiset/kustannuslajin-kaikki-kentat "hj_palkkio")
                                             (yhteiset/kustannuslajin-kaikki-kentat "tavoitehintaiset")
                                             (yhteiset/kustannuslajin-kaikki-kentat "kaikki")
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

(defn- taulukko-rivi
  [tp-rivi kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi?]
  (rivi
    [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:arvo (or (avain_hoitokausi tp-rivi) (yhteiset/summa-fmt 0.00M))
                         :fmt :raha
                         :lihavoi? lihavoi?}]

    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (avain_yht tp-rivi) (yhteiset/summa-fmt 0.00M))
                           :fmt :raha
                           :lihavoi? lihavoi?}])))

(defn- taulukko
  [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti kyseessa-kk-vali? sheet-nimi]}]
  (let [rivit (into []
                (remove nil?
                  (cond
                    (= "MHU ja HJU hoidon johto" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Johto- ja hallintokorvaukset" :johto_ja_hallinto_laskutettu :johto_ja_hallinto_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Erillishankinnat" :hj_erillishankinnat_laskutettu :hj_erillishankinnat_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "HJ-palkkio" :hj_palkkio_laskutettu :hj_palkkio_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Bonukset" :bonukset_laskutettu :bonukset_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Sanktiot" :sakot_laskutettu :sakot_laskutetaan false)

                     ;; Hoitovuoden päättäminen, näytetään vain jos arvot olemassa
                     (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu data))
                       (taulukko-rivi data kyseessa-kk-vali? "Hoitovuoden päättäminen / Tavoitepalkkio"
                         :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan false))

                     (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu data))
                       (taulukko-rivi data kyseessa-kk-vali? "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä"
                         :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan false))

                     (when (yhteiset/raha-arvo-olemassa? (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu data))
                       (taulukko-rivi data kyseessa-kk-vali? "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä"
                         :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan false))

                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :kaikki_laskutettu :kaikki_laskutetaan true)]

                    (= "MHU Ylläpito" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Hankinnat" :hankinnat_laskutettu :hankinnat_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään" :tilaajan_rahavaraukset_laskutettu :tilaajan_rahavaraukset_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt" :lisatyot_laskutettu :lisatyot_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Sanktiot" :sakot_laskutettu :sakot_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :kaikki_laskutettu :kaikki_laskutetaan true)]

                    :else
                    [(taulukko-rivi data kyseessa-kk-vali? "Hankinnat" :hankinnat_laskutettu :hankinnat_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt" :lisatyot_laskutettu :lisatyot_laskutetaan false)
                     (taulukko-rivi data kyseessa-kk-vali? "Sanktiot" :sakot_laskutettu :sakot_laskutetaan false)

                     ;; Jos ideoita miten molemmat rivit saadaan yhden whenin alle niin saa toimia
                     (when (or (= "Talvihoito" otsikko) (= "Liikenneympäristön hoito" otsikko) (= "Soratien hoito" otsikko))
                       (taulukko-rivi data kyseessa-kk-vali? "Äkilliset hoitotyöt" :akilliset_laskutettu :akilliset_laskutetaan false))

                     (when (or (= "Talvihoito" otsikko) (= "Liikenneympäristön hoito" otsikko) (= "Soratien hoito" otsikko))
                       (taulukko-rivi data kyseessa-kk-vali? "Vahinkojen korjaukset" :vahingot_laskutettu :vahingot_laskutetaan false))

                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :kaikki_laskutettu :kaikki_laskutetaan true)])))]

    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? false
                :sheet-nimi sheet-nimi}

     (rivi
       {:otsikko otsikko :leveys 36}
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))
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
        ;; Ei käytetä kk-väliä jos oma aikaväli valittuna
        kyseessa-kk-vali? (if valittu-aikavali? false kyseessa-kk-vali?)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        ;; Jos näytetään tietyn vuoden dataa, tai omaa aikaväliä, sarakkeen otsikko on vain "Määrä"
        laskutettu-teksti (if (or koko-vuosi? valittu-aikavali?) "Määrä" laskutettu-teksti)
        ;; Hoitokausi valittuna?
        hoitokausi? (= aikarajaus :hoitokausi)

        ;; Kun koko hoitokausi on valittu ja loppupvm on myöhemmin kuin kuluva päivä, käytetään kuluvaa päivää
        ;; Muuten laskutusyhteenveto alkaa "ennustamaan" kustannuksia tulevaisuudesta.
        parametrit (assoc parametrit :haun-loppupvm (if (and
                                                          (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
                                                          (pvm/ennen? (pvm/nyt) loppupvm))
                                                      (pvm/nyt)
                                                      loppupvm))
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
                                    (mapv #(assoc % :urakka-id (:urakka-id urakan-parametrit)
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
        yhteenveto (koosta-yhteenveto tiedot)
        tavoite (koosta-tavoite tiedot urakka-tavoite)
        koostettu-yhteenveto (conj [] yhteenveto tavoite)

        sheet-nimi "Tuotekohtainen"
        otsikot [["Talvihoito" "alvi"]
                 ["Liikenneympäristön hoito" "ympä"]
                 ["Soratien hoito" "sora"]
                 ["Päällyste" "pääl"]
                 ["MHU Ylläpito" "yllä"]
                 ["MHU ja HJU hoidon johto" "johto"]
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
     ;; Data on vectorina järjestyksessä, käytetään 'otsikot' indeksiä oikean datan näyttämiseen  
     (concat (for [x otsikot]
               (let [tiedot-indeksi (etsi-indeksi (second x) (first laskutusyhteenvedot))
                     data (try
                            (nth (first laskutusyhteenvedot) tiedot-indeksi)
                            (catch Throwable t
                              (log/error "Tuotekohtaisen laskutusyhteenvedon tietoja ei löytynyt.")
                              nil))]
                 (taulukko {:data data
                            :otsikko (first x)
                            :sheet-nimi (when (= (.indexOf otsikot x) 0) sheet-nimi)
                            :laskutettu-teksti laskutettu-teksti
                            :laskutetaan-teksti laskutetaan-teksti
                            :kyseessa-kk-vali? kyseessa-kk-vali?
                            :alkupvm alkupvm}))))

     (taulukot/toteutuneet-valitaulukko {:data (first koostettu-yhteenveto)
                                         :otsikko "Toteutuneet"
                                         :laskutettu-teksti laskutettu-teksti
                                         :laskutetaan-teksti laskutetaan-teksti
                                         :kyseessa-kk-vali? kyseessa-kk-vali?})
     ;; Näytetään nämä vain jos hoitokausi valittuna
     (when hoitokausi?
       (taulukot/toteutuneet-valitaulukko {:data (second koostettu-yhteenveto)
                                           :otsikko ""
                                           :laskutettu-teksti "Tavoitehinta"
                                           :laskutetaan-teksti "Budjettia jäljellä"
                                           :kyseessa-kk-vali? true}))]))
