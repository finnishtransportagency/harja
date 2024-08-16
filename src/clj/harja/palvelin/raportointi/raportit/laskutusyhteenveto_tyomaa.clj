(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa
  "Työmaakokous laskutusyhteenveto MHU-urakoissa"
  (:require [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-apurit :as taulukot]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as yhteiset]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]))


(defn- taulukko-rivi
  [data kyseessa-kk-vali? valiotsikko avain_hoitokausi avain_yht lihavoi?]
  (rivi
    [:varillinen-teksti {:arvo (str valiotsikko)
                         :lihavoi? lihavoi?}]
    [:varillinen-teksti {:arvo (or (avain_hoitokausi data) (yhteiset/summa-fmt nil))
                         :fmt :raha
                         :lihavoi? lihavoi?}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (avain_yht data) (yhteiset/summa-fmt nil))
                           :fmt :raha
                           :lihavoi? lihavoi?}])))


(defn- rahavaraus-rivit
  "Generoi urakan rahavaraukset taulukon
   val-aika = valittu kk arvo 
   hoitokausi =  hoitokauden alusta arvo
   nimi = Rahavaraus"
  [data avain-yhteensa-hoitokausi avain-yhteensa-valittu kyseessa-kk-vali?
   rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika]
  (let [yhteensa-hoitokausi (avain-yhteensa-hoitokausi data) ;; Migraatiosta palautetut arvot
        yhteensa-valittu (avain-yhteensa-valittu data)
        ;; Kaikki taulukon rivit tässä
        rivit (map (fn [nimi hoitokausi val-aika]
                     ;; Näytä rahavarausrivi aina, vaikka arvo on 0
                     ;; Jos mitään arvoja ei ole olemassa, Rahavarausten alla tulee lukemaan "Ei tietoja."
                     (rivi
                       [:varillinen-teksti {:arvo (str nimi)
                                            :lihavoi? false}]
                       [:varillinen-teksti {:arvo (or hoitokausi (yhteiset/summa-fmt nil))
                                            :fmt :raha
                                            :lihavoi? false}]
                       (when kyseessa-kk-vali?
                         [:varillinen-teksti {:arvo (or val-aika (yhteiset/summa-fmt nil))
                                              :fmt :raha
                                              :lihavoi? false}])))
                rahavaraukset-nimet
                rahavaraukset-hoitokausi
                rahavaraukset-val-aika)]

    ;; Lisää yhteensä-arvot rivien päätteeksi
    (concat rivit
      [(rivi
         [:varillinen-teksti {:arvo "Yhteensä"
                              :lihavoi? true}]
         [:varillinen-teksti {:arvo yhteensa-hoitokausi
                              :fmt :raha
                              :lihavoi? true}]
         (when kyseessa-kk-vali?
           [:varillinen-teksti {:arvo yhteensa-valittu
                                :fmt :raha
                                :lihavoi? true}]))])))


(defn- taulukko [{:keys [data otsikko laskutettu-teksti laskutetaan-teksti
                         kyseessa-kk-vali? sheet-nimi]}]

  (let [rahavaraukset-nimet (konversio/pgarray->vector (:rahavaraus_nimet data))
        rahavaraukset-val-aika (konversio/pgarray->vector (:val_aika_yht_array data))
        rahavaraukset-hoitokausi (konversio/pgarray->vector (:hoitokausi_yht_array data))

        _ (println
            "\n---- \n rahavaraukset-nimet: \n " rahavaraukset-nimet
            " \n rahavaraukset-val-aika: " rahavaraukset-val-aika
            " \n rahavaraukset-hoitokausi:" rahavaraukset-hoitokausi "\n -----")

        rivit (into []
                (remove nil?
                  (cond
                    (= "Hankinnat" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Talvihoito" :talvihoito_hoitokausi_yht :talvihoito_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Liikenneympäristön hoito" :lyh_hoitokausi_yht :lyh_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Sorateiden hoito" :sora_hoitokausi_yht :sora_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Päällystepaikkaukset" :paallyste_hoitokausi_yht :paallyste_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "MHU ylläpito" :yllapito_hoitokausi_yht :yllapito_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "MHU korvausinvestointi" :korvausinv_hoitokausi_yht :korvausinv_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :hankinnat_hoitokausi_yht :hankinnat_val_aika_yht true)]

                    (= "Hoidonjohto" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Johto- ja hallintokorvaukset" :johtojahallinto_hoitokausi_yht :johtojahallinto_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Erillishankinnat" :erillishankinnat_hoitokausi_yht :erillishankinnat_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Hoidonjohtopalkkio" :hjpalkkio_hoitokausi_yht :hjpalkkio_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :hoidonjohto_hoitokausi_yht :hoidonjohto_val_aika_yht true)]

                    (= "Rahavaraukset" otsikko)
                    (rahavaraus-rivit data :kaikki_rahavaraukset_hoitokausi_yht :kaikki_rahavaraukset_val_yht kyseessa-kk-vali? rahavaraukset-nimet rahavaraukset-hoitokausi rahavaraukset-val-aika)

                    (= "Lisätyöt" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (talvihoito)" :lisatyo_talvihoito_hoitokausi_yht :lisatyo_talvihoito_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (liikenneympäristön hoito)" :lisatyo_lyh_hoitokausi_yht :lisatyo_lyh_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (sorateiden hoito)" :lisatyo_sora_hoitokausi_yht :lisatyo_sora_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (päällystepaikkaukset)" :lisatyo_paallyste_hoitokausi_yht :lisatyo_paallyste_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (MHU ylläpito)" :lisatyo_yllapito_hoitokausi_yht :lisatyo_yllapito_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (MHU korvausinvestointi)" :lisatyo_korvausinv_hoitokausi_yht :lisatyo_korvausinv_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Lisätyöt (MHU hoidonjohto)" :lisatyo_hoidonjohto_hoitokausi_yht :lisatyo_hoidonjohto_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Yhteensä" :lisatyot_hoitokausi_yht :lisatyot_val_aika_yht true)]

                    (= "Muut" otsikko)
                    [(taulukko-rivi data kyseessa-kk-vali? "Bonukset" :bonukset_hoitokausi_yht :bonukset_val_aika_yht false)
                     (taulukko-rivi data kyseessa-kk-vali? "Sanktiot" :sanktiot_hoitokausi_yht :sanktiot_val_aika_yht false)

                     ;; Näytetään päätökset vain jos ne on olemassa 
                     (when (yhteiset/raha-arvo-olemassa? (:paatos_kattoh_ylitys_hoitokausi_yht data))
                       (taulukko-rivi data kyseessa-kk-vali? "Hoitovuoden päätös / Urakoitsija maksaa kattohinnan ylityksestä" :paatos_kattoh_ylitys_hoitokausi_yht :paatos_kattoh_ylitys_val_aika_yht false))

                     (when (yhteiset/raha-arvo-olemassa? (:paatos_tavoiteh_ylitys_hoitokausi_yht data))
                       (taulukko-rivi data kyseessa-kk-vali? "Hoitovuoden päätös / Urakoitsija maksaa tavoitehinnan ylityksestä" :paatos_tavoiteh_ylitys_hoitokausi_yht :paatos_tavoiteh_ylitys_hoitokausi_yht false))

                     (when (yhteiset/raha-arvo-olemassa? (:paatos_tavoitepalkkio_hoitokausi_yht data))
                       (taulukko-rivi data kyseessa-kk-vali? "Tavoitepalkkio" :paatos_tavoitepalkkio_hoitokausi_yht :paatos_tavoitepalkkio_hoitokausi_yht false))])))]

    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? false
                :sheet-nimi sheet-nimi}
     (rivi
       {:otsikko otsikko :leveys 36}
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))
     rivit]))


(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id aikarajaus] :as parametrit}]
  (log/debug "Työmaakokous PARAMETRIT: " (pr-str parametrit))
  (let [kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoitokauden alusta")
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm))

        ;; Käytetäänkö omaa aikaväliä
        valittu-aikavali? (= aikarajaus :valittu-aikakvali)
        ;; Ei käytetä kk-väliä jos oma aikaväli valittuna
        kyseessa-kk-vali? (if valittu-aikavali? false kyseessa-kk-vali?)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        ;; Kun koko hoitokausi on valittu ja loppupvm on myöhemmin kuin kuluva päivä, käytetään kuluvaa päivää
        ;; Muuten laskutusyhteenveto alkaa "ennustamaan" kustannuksia tulevaisuudesta.
        parametrit (assoc parametrit :haun-loppupvm (if (and
                                                          (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
                                                          (pvm/ennen? (pvm/nyt) loppupvm))
                                                      (pvm/nyt)
                                                      loppupvm))

        ;; Jos käytetään valittua aikaväliä, näytetään vain "Määrä" -otsikko
        laskutettu-teksti (if (= aikarajaus :valittu-aikavali) "Määrä" laskutettu-teksti)

        ;; Konteksti ja urakkatiedot
        konteksti (cond
                    urakka-id :urakka
                    hallintayksikko-id :hallintayksikko
                    :else :urakka)

        {alueen-nimi :nimi} (first (if (= konteksti :hallintayksikko)
                                     (hallintayksikko-q/hae-organisaatio db hallintayksikko-id)
                                     (urakat-q/hae-urakka db urakka-id)))

        urakat (urakat-q/hae-urakkatiedot-laskutusyhteenvetoon
                 db {:alkupvm alkupvm
                     :loppupvm loppupvm
                     :hallintayksikkoid hallintayksikko-id
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

        rivitiedot (first (first laskutusyhteenvedot))
        otsikot ["Hankinnat" "Hoidonjohto"]
        sheet-nimi "Työmaakokous"]

    [:raportti {:nimi (str "Laskutusyhteenveto (" (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) ")")
                :otsikon-koko :keskikoko}

     [:otsikko-heading-small (str alueen-nimi)]

     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))

     (when (or kyseessa-hoitokausi-vali? kyseessa-kk-vali?)
       (yleinen/urakan-hoitokauden-indeksikerroin {:indeksikertoimet indeksikertoimet
                                                   :hoitokausi (pvm/paivamaaran-hoitokausi alkupvm)}))

     (if
       (and
         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
         (pvm/ennen? (pvm/nyt) loppupvm))
       [:otsikko-heading (str "Tavoitehintaan vaikuttavat toteutuneet kustannukset aikajaksolta (" (pvm/pvm alkupvm) " - " (pvm/pvm (pvm/nyt)) ")")]
       [:otsikko-heading "Tavoitehintaan vaikuttavat toteutuneet kustannukset"])

     (concat (for [otsikko otsikot]
               (taulukko {:data rivitiedot
                          :otsikko otsikko
                          :sheet-nimi (when (= (.indexOf otsikot otsikko) 0) sheet-nimi)
                          :laskutettu-teksti laskutettu-teksti
                          :laskutetaan-teksti laskutetaan-teksti
                          :kyseessa-kk-vali? kyseessa-kk-vali?})))

     (taulukot/hoidonjohto-valitaulukko {:data rivitiedot
                                         :kyseessa-kk-vali? kyseessa-kk-vali?})

     [:otsikko-heading "Muut tavoitehintaan vaikuttavat kulut"]

     (taulukko {:data rivitiedot
                :otsikko "Rahavaraukset"
                :laskutettu-teksti laskutettu-teksti
                :laskutetaan-teksti laskutetaan-teksti
                :kyseessa-kk-vali? kyseessa-kk-vali?})

     (taulukot/valitaulukko {:data rivitiedot
                             :otsikko "Toteutuneet"
                             :laskutettu-teksti laskutettu-teksti
                             :laskutetaan-teksti laskutetaan-teksti
                             :kyseessa-kk-vali? kyseessa-kk-vali?})

     (if
       (and
         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
         (pvm/ennen? (pvm/nyt) loppupvm))
       [:otsikko-heading (str "Muut toteutuneet kustannukset (ei lasketa tavoitehintaan) aikajaksolta (" (pvm/pvm alkupvm) " - " (pvm/pvm (pvm/nyt)) ")")]
       [:otsikko-heading "Muut toteutuneet kustannukset (ei lasketa tavoitehintaan)"])

     (let [otsikot ["Lisätyöt" "Muut"]]
       (concat (for [x otsikot]
                 (taulukko {:data rivitiedot
                            :otsikko x
                            :laskutettu-teksti laskutettu-teksti
                            :laskutetaan-teksti laskutetaan-teksti
                            :kyseessa-kk-vali? kyseessa-kk-vali?}))))

     (taulukot/valitaulukko {:data rivitiedot
                             :otsikko "Muut"
                             :laskutettu-teksti laskutettu-teksti
                             :laskutetaan-teksti laskutetaan-teksti
                             :kyseessa-kk-vali? kyseessa-kk-vali?})

     [:tyomaa-laskutusyhteenveto-yhteensa kyseessa-kk-vali? (str (pvm/pvm hk-alkupvm) " - " (pvm/pvm hk-loppupvm))
      (fmt/formatoi-arvo-raportille (:yhteensa_kaikki_hoitokausi_yht rivitiedot))
      (fmt/formatoi-arvo-raportille (:yhteensa_kaikki_val_aika_yht rivitiedot))
      laskutettu-teksti laskutetaan-teksti]]))
