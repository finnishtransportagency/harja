(ns harja.palvelin.raportointi.raportit.valikatselmusraportti
  "Valikatselmuksen PDF-raportti"
  (:require [harja.fmt :as fmt]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
            [harja.kyselyt.valikatselmus :as valikatselmus-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]))

(defn- euro [arvo]
  (fmt/euro-opt false arvo))

(defn- lisaa-plus [arvo]
  (str (when (and (number? arvo) (pos? arvo)) "+") (euro arvo)))

(defn- arvopaatoksesta [paatos avain]
  (when (:id paatos)
    (get paatos avain)))

(defn- ota-paatos [paatokset avain]
  (some #(get % avain) paatokset))

(defn- laske-siirto [siirrettava-maara seuraavan-vuoden-hankintakustannusten-alennus]
  (when (or siirrettava-maara seuraavan-vuoden-hankintakustannusten-alennus)
    (- (or siirrettava-maara 0)
      (or seuraavan-vuoden-hankintakustannusten-alennus 0))))

(defn yhteenveto-rivit [data urakan-tiedot]
  (let [paatokset (:paatokset data)
        urakan-parametrit (:urakan-parametrit data)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi data)
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
        yhteenveto (:yhteenveto data)
        budjettitavoite (:budjettitavoite yhteenveto)
        kustannukset (:kustannukset yhteenveto)
        muutokset (:muutokset kustannukset)
        muutosten-hallinta? (:muutosten_hallinta urakan-parametrit)
        indeksikorjaus-paatos (ota-paatos paatokset :hoitovuoden-lopun-indeksikorjaus)
        hoitokauden-lopun-indeksikorjaus (or (:hoitokauden_lopun_indeksikorjaus indeksikorjaus-paatos) 0)
        hoitovuoden-alun-tavoitehinta (or (:tavoitehinta-indeksikorjattu budjettitavoite) 0)
        tavoitehinnan-muutokset (or (:tavoitehinnanoikaisu-budjetoitu kustannukset) 0)
        kirjallisesti-sovitut-muutokset (when muutosten-hallinta? (:kirjallisesti-sovitut-muutokset budjettitavoite))
        menneet-pysyvat-muutokset (when muutosten-hallinta? (:menneet-muutos-summa budjettitavoite))
        toteumiin-perustuvat-muutokset (when muutosten-hallinta? (:toteumiin-perustuvat-muutokset-yht yhteenveto))
        pysyvat-muutokset (+ (or kirjallisesti-sovitut-muutokset 0) (or toteumiin-perustuvat-muutokset 0))
        arvonvahennykset (reduce + 0 (map :maara (:arvonvahennykset yhteenveto)))
        hoitovuoden-lopun-tavoitehinta (+ (or (:hoitovuoden-lopun-tavoitehinta budjettitavoite) 0)
                                         (if (:id indeksikorjaus-paatos) 0 hoitokauden-lopun-indeksikorjaus)
                                         pysyvat-muutokset arvonvahennykset)
        kattohinta-kerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        hoitovuoden-lopun-kattohinta (+ (or (:hoitovuoden-lopun-kattohinta budjettitavoite) 0)
                                       (* (if (:id indeksikorjaus-paatos) 0 hoitokauden-lopun-indeksikorjaus) kattohinta-kerroin)
                                       (* pysyvat-muutokset kattohinta-kerroin)
                                       (* arvonvahennykset kattohinta-kerroin))
        hankintakustannukset (+ (or (:hankintakustannukset-toteutunut kustannukset) 0)
                               (or (:rahavaraukset-toteutunut kustannukset) 0))
        erillishankinnat (or (:erillishankinnat-toteutunut kustannukset) 0)
        jjh-muutokset (or (:toimenpide-toteutunut-summa
                            (first (filter #(= (:toimenpide %) "Johto- ja hallintokorvauksen muutokset") muutokset))) 0)
        johto-ja-hallintokorvaus (+ (or (:johto-ja-hallintokorvaus-toteutunut kustannukset) 0) jjh-muutokset)
        hoidonjohtopalkkio (or (:hoidonjohdonpalkkio-toteutunut kustannukset) 0)
        muut-kulut (or (:muukulu-tavoitehintainen-toteutunut kustannukset) 0)
        toteuma-yht (or (get-in yhteenveto [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        lupauspaatos (ota-paatos paatokset :lupaukset)
        lupausbonus (or (arvopaatoksesta lupauspaatos :lupausbonus) 0)
        asiakastyytyvaisyysbonus (reduce + 0 (keep #(when (= (:tyyppi %) "asiakastyytyvaisyysbonus") (:rahasumma %)) (:bonukset yhteenveto)))
        muut-bonukset (reduce + 0 (keep #(when (not (contains? #{"asiakastyytyvaisyysbonus" "lupausbonus"} (:tyyppi %))) (:rahasumma %)) (:bonukset yhteenveto)))
        lupaussanktio (or (arvopaatoksesta lupauspaatos :lupaussanktio) 0)
        laskutusrajan-ylitys-sanktiot (reduce + 0 (keep #(when (= "laskutus_yli_laskutusrajan" (:sakkoryhma %))
                                                           (+ (:maara %) (:indeksikorjaus %))) (:sanktiot yhteenveto)))
        muut-sanktiot (reduce + 0 (map #(if (not (contains? #{"lupaussanktio" "arvonvahennyssanktio" "laskutus_yli_laskutusrajan"} (:sakkoryhma %)))
                                          (+ (:maara %) (:indeksikorjaus %)) 0) (:sanktiot yhteenveto)))
        tavoitehinnan-ylityspaatos (ota-paatos paatokset :tavoitehinnan-ylitys)
        tavoitehinnan-alituspaatos (ota-paatos paatokset :tavoitehinnan-alitus)
        tavoitehinnan-ylitys (or (:ylityksen_maara tavoitehinnan-ylityspaatos) 0)
        tavoitehinnan-alitus (or (:alituksen_maara tavoitehinnan-alituspaatos) 0)
        tavoitepalkkio (or (arvopaatoksesta tavoitehinnan-alituspaatos :tavoitepalkkio) 0)
        seuraavan-vuoden-hankintakustannusten-alennus  (:siirron_maara tavoitehinnan-alituspaatos)
        kattohinnan-ylityspaatos (ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (or (:ylityksen_maara kattohinnan-ylityspaatos) 0)
        siirto (laske-siirto (:siirrettava_maara kattohinnan-ylityspaatos)
                 seuraavan-vuoden-hankintakustannusten-alennus)
        nayta-arvonvahennykset? (sanktio-domain/arvonvahennykset-kaytossa? urakan-tiedot (pvm/vuodesta-hoitokausi hoitokauden-alkuvuosi))
        tavoitehinnan-ylitys? (or (:id tavoitehinnan-ylityspaatos)
                                (and (nil? (:id tavoitehinnan-ylityspaatos)) (not= 0 tavoitehinnan-ylitys) (> toteuma-yht hoitovuoden-lopun-tavoitehinta)))
        tavoitehinnan-alitus? (or tavoitehinnan-alituspaatos
                                (and (not tavoitehinnan-alituspaatos)
                                  (not= (fmt/pyorista-desimaaliin toteuma-yht 2)
                                    (fmt/pyorista-desimaaliin hoitovuoden-lopun-tavoitehinta 2))
                                  (not= 0 tavoitehinnan-alitus)))
        hoidonjohtopalkkion-paatos (ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (arvopaatoksesta hoidonjohtopalkkion-paatos :hoidonjohtopalkkio_muutos) 0)]

    {:tavoitehinta (cond-> [["Hoitovuoden alun indeksikorjattu tavoitehinta" (euro hoitovuoden-alun-tavoitehinta)]]
                     ;; Vanhemilla urakoilla ei ole muutostenhallinta käytössä ja heille näytetään vähän erilaiset tiedot
                     (not muutosten-hallinta?) (conj ["Tavoitehinnan muutokset" (lisaa-plus tavoitehinnan-muutokset)])
                     (not muutosten-hallinta?) (conj ["Hoitovuoden lopun indeksikorjaus" (euro hoitokauden-lopun-indeksikorjaus)])

                     ;; Uudemmat urakat
                     (and muutosten-hallinta? menneet-pysyvat-muutokset) (conj ["Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)" (euro menneet-pysyvat-muutokset)])
                     muutosten-hallinta? (conj ["Tavoitehinnan muutokset" (lisaa-plus tavoitehinnan-muutokset)])
                     (and muutosten-hallinta? kirjallisesti-sovitut-muutokset) (conj ["  • Kirjallisesti sovitut muutokset" (lisaa-plus kirjallisesti-sovitut-muutokset)])
                     muutosten-hallinta? (conj ["  • Toteumiin perustuvat muutokset" (lisaa-plus toteumiin-perustuvat-muutokset)])
                     (and muutosten-hallinta? nayta-arvonvahennykset?) (conj ["  • Arvonvähennysten tavoitehintamuutokset" (euro arvonvahennykset)])
                     muutosten-hallinta? (conj ["Hoitovuoden lopun indeksikorjaus" (euro hoitokauden-lopun-indeksikorjaus)])

                     true (conj ["Hoitovuoden lopun tavoitehinta" (euro hoitovuoden-lopun-tavoitehinta) true])
                     true (conj ["Hoitovuoden lopun kattohinta" (euro hoitovuoden-lopun-kattohinta) true]))
     :kustannukset (cond-> [["Hankintakustannukset" (euro hankintakustannukset)]
                            ["Erillishankinnat" (euro erillishankinnat)]
                            ["Johto- ja hallintokorvaus" (euro johto-ja-hallintokorvaus)]
                            ["Hoidonjohtopalkkio" (euro hoidonjohtopalkkio)]]
                     arvonvahennykset (conj ["Arvonvähennykset" (euro arvonvahennykset)])
                     (pos? muut-kulut) (conj ["Muut kulut" (euro muut-kulut)])
                     true (conj ["Toteutuma yhteensä" (euro toteuma-yht) true]))
     :tavoitehinnan-ylitys (when tavoitehinnan-ylitys?
                             [["Tavoitehinnan ylitys" (euro tavoitehinnan-ylitys)]
                              [(str "Urakoitsija maksaa (" (int (:urakoitsijan_prosentti tavoitehinnan-ylityspaatos)) "%)") (euro (:urakoitsija_maksaa tavoitehinnan-ylityspaatos))]
                              [(str "Tilaaja maksaa (" (int (:tilaajan_prosentti tavoitehinnan-ylityspaatos)) "%)") (euro (:tilaaja_maksaa tavoitehinnan-ylityspaatos))]])
     :tavoitehinnan-ylitys-maara tavoitehinnan-ylitys
     :tavoitehinnan-alitus (when tavoitehinnan-alitus?
                             [["Tavoitehinnan alitus" (euro tavoitehinnan-alitus)]
                              ["Tavoitepalkkio" (euro tavoitepalkkio)]
                              ["Siirto seuraavan vuoden hankintakustannuksiin" (euro siirto)]])
     :tavoitehinnan-alitus-maara tavoitehinnan-alitus
     :kattohinnan-ylitys (when (pos? kattohinnan-ylitys)
                           (cond-> [["Kattohinnan ylitys" (euro kattohinnan-ylitys)]
                                    ["Urakoitsija maksaa" (euro (:urakoitsija_maksaa kattohinnan-ylityspaatos))]]
                             (not= hoitokauden-alkuvuosi (dec urakan-loppuvuosi))
                             (conj ["Siirto seuraavan vuoden hankintakustannuksiin" (euro siirto)])))
     :kattohinnan-ylitys-maara kattohinnan-ylitys
     :bonukset [["Lupausbonus" (euro lupausbonus)]
                ["Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (euro asiakastyytyvaisyysbonus)]
                ["Muut bonukset" (euro muut-bonukset)]]
     :sanktiot (cond-> [["Lupaussanktio" (euro lupaussanktio)]]
                 (true? (:laskutusraja_kaytossa urakan-parametrit)) (conj ["Laskutus yli laskutusrajan -sanktiot" (euro laskutusrajan-ylitys-sanktiot)])
                 true (conj ["Muut sanktiot" (euro muut-sanktiot)])
                 nayta-arvonvahennykset? (conj ["Arvonvähennykset" (euro arvonvahennykset)]))
     :hoidonjohtopalkkio [["Hoidonjohtopalkkion muutos"
                           (euro (if (and (:id hoidonjohtopalkkion-paatos)
                                       (neg? hoidonjohtopalkkion-muutos))
                                   (* -1 hoidonjohtopalkkion-muutos)
                                   0))]]}))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as _parametrit}]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        _ (println "alkupvm" alkupvm)
        hoitovuosi (pvm/vuosi alkupvm)
        hoitovuoden-tiedot (valikatselmus-palvelu/hae-valikatselmuksen-tiedot-hoitovuodelle
                             db user {:urakkaid urakka-id :hoitovuosi hoitovuosi})
        data (yhteenveto-rivit hoitovuoden-tiedot urakan-tiedot)

        raportin-otsikko "Välikatselmus"
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        muodosta-taulukkorivi (fn [[otsikko arvo lihavoitu?]]
                                {:lihavoi? lihavoitu?
                                 :rivi [otsikko arvo]})]

    (into [:raportti {:nimi raportin-otsikko
                      :orientaatio :portrait
                      :urakan-nimi (:nimi urakan-tiedot)
                      :aikajakso aikajakso
                      :otsikon-koko :iso
                      :raportin-yleiset-tiedot {:raportin-nimi raportin-otsikko}
                      :alkupvm alkupvm
                      :loppupvm loppupvm}
           [:jakaja nil]]

      (concat
        [[:otsikko-heading "Hoitovuoden lopun tavoite- ja kattohinta"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinta data)))]]

        [[:otsikko-heading "Tavoitehintaan kuuluvat toteutuneet kustannukset"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:kustannukset data)))]]

        ;; Jos tavoitehinta on alitettu, ylitetty jne, niin kootaan ne tähän.
        (when (:tavoitehinnan-ylitys data)
          [[:otsikko-heading "Tavoitehinnan ylitys"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinnan-ylitys data)))]])

        (when (:tavoitehinnan-alitus data)
          [[:otsikko-heading "Tavoitehinnan alitus"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:tavoitehinnan-alitus data)))]])

        (when (:kattohinnan-ylitys data)
          [[:otsikko-heading "Kattohinnan ylitys"]
           [:taulukko {}
            [{:leveys 10 :otsikko ""}
             {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
            (into [] (keep #(muodosta-taulukkorivi %) (:kattohinnan-ylitys data)))]])

        [[:otsikko-heading "Bonukset"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:bonukset data)))]]

        [[:otsikko-heading "Sanktiot"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:sanktiot data)))]]

        [[:otsikko-heading "Hoidonjohtopalkkion muutos"]
         [:taulukko {}
          [{:leveys 10 :otsikko ""}
           {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
          (into [] (keep #(muodosta-taulukkorivi %) (:hoidonjohtopalkkio data)))]]))))
