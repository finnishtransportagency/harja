(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa
  "Työmaakokous laskutusyhteenveto MHU-urakoissa"
  (:require [taoensso.timbre :as log]

            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.jarjestelman-tila :as jarjestelmatila-q]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-yhteiset :as taulukot]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-tyomaa :refer [taulukko-tyomaakokous]]))


(defn suorita [db user {:keys [alkupvm loppupvm urakka-id elinvoimakeskus-id aikarajaus] :as parametrit}]
  (log/debug "Työmaakokous PARAMETRIT: " (pr-str parametrit))
  (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        av-validointi? (:arvonvahennys_validoinnit_kaytossa (first (jarjestelmatila-q/hae-jarjestelman-asetukset db)))
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoitovuoden alusta")
        laskutetaan-teksti (str (pvm/kuukausi-isolla (pvm/kuukausi alkupvm)) " " (pvm/vuosi alkupvm))
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
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
                 db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :elinvoimakeskus-id elinvoimakeskus-id
                     :urakkaid urakka-id
                     :urakkatyyppi (name (:urakkatyyppi parametrit))})

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
                                      (yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot db user urakan-parametrit)))
                              urakoiden-parametrit)

        perusluku (when urakka-id (:perusluku (ffirst laskutusyhteenvedot)))
        indeksikertoimet (when urakka-id (bs/hae-urakan-indeksikertoimet db user {:urakka-id urakka-id}))

         [hk-alkupvm hk-loppupvm] (if (or
                                        (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                        (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                    ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                    ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                    (pvm/paivamaaran-hoitokausi alkupvm)
                                    [alkupvm loppupvm])

         hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)

        valittu-aikavali? (= aikarajaus :valittu-aikakvali)
        aikavali-str (str (pvm/pvm hk-alkupvm) " - " (pvm/pvm hk-loppupvm))

        ;; Formatoi valittu aikaväli otsikko
        laskutettu-teksti (if valittu-aikavali? aikavali-str laskutettu-teksti)

        rivitiedot (first (first laskutusyhteenvedot))
        otsikot ["Hankinnat" "Hoidonjohto"]
        sheet-nimi "Työmaakokous"
        laskutusraja-tarkistettu? (boolean (and (:onko_laskutusraja_kaytossa rivitiedot)
                                               (:laskutusraja_yht rivitiedot)
                                               (:laskutusraja_alkuperainen rivitiedot)
                                               (> (:laskutusraja_yht rivitiedot) (:laskutusraja_alkuperainen rivitiedot))))]

    [:raportti {:nimi (str "Laskutusyhteenveto (" (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) ")")
                :otsikon-koko :keskikoko :piilota-otsikko? true}

     [:otsikko-heading-small (str alueen-nimi)]

     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))

     (when (or kyseessa-hoitokausi-vali? kyseessa-kk-vali?)
       (yleinen/urakan-hoitokauden-indeksikerroin {:indeksikertoimet indeksikertoimet
                                                   :hoitokausi (pvm/paivamaaran-hoitokausi alkupvm)}))

     ;; Pääotsikko
     (if
       (and
         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
         (pvm/ennen? (pvm/nyt) loppupvm))
       [:laskutusyhteenveto-otsikko (str "Tavoitehintaan vaikuttavat kustannukset aikajaksolta (" (pvm/pvm alkupvm) " - " (pvm/pvm (pvm/nyt)) ")")]
       [:laskutusyhteenveto-otsikko "Tavoitehintaan vaikuttavat kustannukset"])

     ;; ------------------------------- ;;
     ;;    Hankinnat ja hoidonjohto     ;;
     ;; ------------------------------- ;;
      (concat (for [otsikko otsikot]
                (taulukko-tyomaakokous {:data rivitiedot
                                        :otsikko otsikko
                                        :sheet-nimi (when (= (.indexOf otsikot otsikko) 0) sheet-nimi)
                                        :laskutettu-teksti laskutettu-teksti
                                        :laskutetaan-teksti laskutetaan-teksti
                                        :kyseessa-kk-vali? kyseessa-kk-vali?
                                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        :urakan-alkuvuosi urakan-alkuvuosi
                                        :av-validointi? av-validointi?})))

       ;; --------------- ;;
       ;;    Muutokset    ;;
       ;; --------------- ;;
       (when (ominaisuus-kaytossa? :mhu-muutokset)
         (taulukko-tyomaakokous {:data rivitiedot
                                 :otsikko "Muutokset"
                                 :laskutettu-teksti laskutettu-teksti
                                 :laskutetaan-teksti laskutetaan-teksti
                                 :kyseessa-kk-vali? kyseessa-kk-vali?
                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 :urakan-alkuvuosi urakan-alkuvuosi
                                 :av-validointi? av-validointi?}))

       ;; ------------------------ ;;
       ;;   Rahavaraukset, muut    ;;
       ;; ------------------------ ;;
       (taulukko-tyomaakokous {:data rivitiedot
                               :otsikko "Rahavaraukset"
                               :laskutettu-teksti laskutettu-teksti
                               :laskutetaan-teksti laskutetaan-teksti
                               :kyseessa-kk-vali? kyseessa-kk-vali?
                               :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                               :urakan-alkuvuosi urakan-alkuvuosi
                               :av-validointi? av-validointi?})

       (taulukko-tyomaakokous {:data rivitiedot
                               :otsikko "Muut tavoitehintaan vaikuttavat kulut"
                               :laskutettu-teksti laskutettu-teksti
                               :laskutetaan-teksti laskutetaan-teksti
                               :kyseessa-kk-vali? kyseessa-kk-vali?
                               :tavoitehintainen? true
                               :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                               :urakan-alkuvuosi urakan-alkuvuosi
                               :av-validointi? av-validointi?})

     (when-not
       (boolean (:kustannussuunnitelma_vahvistettu rivitiedot))
       [:info-laatikko "Hoitovuoden alun indeksikorjattua tavoitehintaa ei ole vahvistettu"
        (str
          "Laskenta tehdään ei vahvistetuilla tiedoilla, jotka saattavat päivittyä, "
          "kun hoitovuoden alun indeksikorjattu tavoitehinta vahvistetaan.")
        800])

     ;; ----------------- ;;
     ;;   Laskutusraja    ;;
     ;; ----------------- ;;
     (if (:onko_laskutusraja_kaytossa rivitiedot)
       (taulukot/lapinakyva-taulukko false
         {:data rivitiedot
          :otsikko "Laskutusraja"
          :aikavali aikavali-str
          :valittu-aikavali? valittu-aikavali?
          :laskutettu-teksti laskutettu-teksti
          :laskutetaan-teksti laskutetaan-teksti
          :kyseessa-kk-vali? kyseessa-kk-vali?
          :laskutusraja-tarkistettu? laskutusraja-tarkistettu?})

       ;; ------------------------------------------------------------ ;;
       ;;    Hoitovuoden alun indeksikorjattutavoitehinta              ;;
       ;;    Tavoitehinnan muutokset / Kirjallisesti sovitut muutokset ;;
       ;;    Tavoitehintaan vaikuttavat kustannukset yhteensä          ;;
       ;;    Budjettia jäljellä                                        ;;
       ;; ------------------------------------------------------------ ;;
       (taulukot/lapinakyva-taulukko false
         {:data rivitiedot
          :otsikko "Toteutuneet"
          :valittu-aikavali? valittu-aikavali?
          :vapaa-aikavali-teksti aikavali-str
          :laskutettu-teksti laskutettu-teksti
          :laskutetaan-teksti laskutetaan-teksti
          :kyseessa-kk-vali? kyseessa-kk-vali?
          :kyseessa-hoitokausi-vali? kyseessa-hoitokausi-vali?}))

     ;; Ei tavoitehintaiset
     (if
       (and
         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
         (pvm/ennen? (pvm/nyt) loppupvm))
       [:laskutusyhteenveto-otsikko (str "Tavoitehinnan ulkopuoliset kustannukset aikajaksolta (" (pvm/pvm alkupvm) " - " (pvm/pvm (pvm/nyt)) ")")]
       [:laskutusyhteenveto-otsikko "Tavoitehinnan ulkopuoliset kustannukset"])

       ;; ------------------------ ;;
       ;;    Lisätyöt & muut       ;;
       ;; ------------------------ ;;
       (taulukko-tyomaakokous {:data rivitiedot
                               ;; Tämä on design mukainen otsikko
                               :otsikko "Kustannus"
                               :laskutettu-teksti laskutettu-teksti
                               :laskutetaan-teksti laskutetaan-teksti
                               :kyseessa-kk-vali? kyseessa-kk-vali?
                               :tavoitehintainen? false
                               :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                               :urakan-alkuvuosi urakan-alkuvuosi
                               :av-validointi? av-validointi?})

     ;; Tavoitehinnan ulkopuoliset kustannukset yhteensä
     (taulukot/lapinakyva-taulukko false
       {:data rivitiedot
        :aikavali aikavali-str
        :valittu-aikavali? valittu-aikavali?
        :laskutettu-teksti laskutettu-teksti
        :laskutetaan-teksti laskutetaan-teksti
        :kyseessa-kk-vali? kyseessa-kk-vali?})

     ;; ------------------------ ;;
     ;;    Laskutus yhteensä     ;;
     ;; ------------------------ ;;
     [:tyomaa-laskutusyhteenveto-yhteensa
      kyseessa-kk-vali?
      (:onko_laskutusraja_kaytossa rivitiedot)
      (:onko_laskutusraja_ylittynyt rivitiedot)
      (:yhteensa_kaikki_hoitokausi_yht rivitiedot)
      (:yhteensa_kaikki_val_aika_yht rivitiedot)
      (:laskutettavaa_kaikki_yht rivitiedot)
      (:laskutettavaa_kaikki_val_aika rivitiedot)
      laskutettu-teksti laskutetaan-teksti]]))
