(ns harja.views.urakka.valikatselmus.yhteenvetolaatikko
  (:require [harja.fmt :as fmt]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]))

(defn arvo-paatoksesta
  "Monet euromääräiset arvot päätöksestä kannattaa hakea vasta, kun päätös on tehty. Ja toisaalta päätöksissä voi olla myös tietoja,
  jotka voidaan näyttää, vaikka päätöstä ei ole vielä tehty. Eli verrataan tietokanta id:tä siihen, että onko päätös tehty."
  [paatos avain]
  (when (:id paatos)
    (get paatos avain)))

(defn yhteenvetolaatikko [e! {:keys [paatokset urakan-parametrit] :as app}]
  (let [yhteenvedon-tiedot (:yhteenveto app)

        ;; Yhteenvedot kokonaissummat
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (get-in yhteenvedon-tiedot [:budjettitavoite :tavoitehinta-indeksikorjattu])
        ;; Tavoitehinnan muutokset saadaan oikaisuista -24 ja sitä vanhemmille urakoille
        tavoitehinnan-muutokset (get-in yhteenvedon-tiedot [:kustannukset :tavoitehinnanoikaisu-budjetoitu])
        ;; Pysyvät muutokset ja toteutumiin perustuvat muutokset saadaan vain, jos muutosten hallinta on käytössä. -25 ja uudemmat urakat.
        toteuma-muutokset (when (:muutosten_hallinta urakan-parametrit) (get-in yhteenvedon-tiedot [:budjettitavoite :toteuma-muutos-summa]))
        pysyvat-muutokset (when (:muutosten_hallinta urakan-parametrit)
                            (get-in yhteenvedon-tiedot [:budjettitavoite :pysyva-muutos-summa]))
        pysyvat-muutokset-toteuma-muutokset (+ (or pysyvat-muutokset 0) (or toteuma-muutokset 0))

        hoitovuoden-lopun-tavoitehinta (get-in yhteenvedon-tiedot [:budjettitavoite :hoitovuoden-lopun-tavoitehinta])
        hoitovuoden-lopun-kattohinta (get-in yhteenvedon-tiedot [:budjettitavoite :hoitovuoden-lopun-kattohinta])

        ;; Toteutuneet kustannukset
        ;; Välikatselmuksessa käytetyt Hankintakustannukset ovat eri asia kuin Kustannusten Seurannan Hankintakustannukset/Suunnitellut Hankinnat.
        ;; Välikatselmuksessa Hankintakustannuksiin lisätään toteutuneet Rahavaraukset.
        hankintakustannukset (or
                               (+ (get-in yhteenvedon-tiedot [:kustannukset :hankintakustannukset-toteutunut])
                                 (get-in yhteenvedon-tiedot [:kustannukset :rahavaraukset-toteutunut]))
                               0)
        erillishankinnat (or (get-in yhteenvedon-tiedot [:kustannukset :erillishankinnat-toteutunut]) 0)
        johto-ja-hallintokorvaus (or (get-in yhteenvedon-tiedot [:kustannukset :johto-ja-hallintokorvaus-toteutunut]) 0)
        hoidonjohtopalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :hoidonjohdonpalkkio-toteutunut]) 0)
        muut-kulut (or (get-in yhteenvedon-tiedot [:kustannukset :muukulu-tavoitehintainen-toteutunut]) 0)
        toteuma-yht (get-in yhteenvedon-tiedot [:kustannukset-yhteensa :yht-toteutunut-summa])

        ;; Urakoitsijan saatavat
        lupauspaatos (valikatselmus-tiedot/ota-paatos paatokset :lupaukset)
        lupausbonus (or (arvo-paatoksesta lupauspaatos :lupausbonus) 0)

        asiakastyytyvaisyysbonus (apply + (map (fn [bonus]
                                                 (if (= (:tyyppi bonus) "asiakastyytyvaisyysbonus")
                                                   (:rahasumma bonus)
                                                   0))
                                            (:bonukset yhteenvedon-tiedot)))
        muut-bonukset (apply + (map (fn [bonus]
                                      (if (not (contains? #{"asiakastyytyvaisyysbonus" "lupausbonus"} (:tyyppi bonus)))
                                        (:rahasumma bonus)
                                        0))
                                 (:bonukset yhteenvedon-tiedot)))

        ;; Tilaajan saatavat
        lupaussanktio (or (arvo-paatoksesta lupauspaatos :lupaussanktio) 0)
        muut-sanktiot (apply + (map (fn [a]
                                      (if (not (contains? #{"lupaussanktio" "arvonvahennyssanktio"} (:sakkoryhma a)))
                                        (+ (:maara a) (:indeksikorjaus a))
                                        0))
                                 (:sanktiot yhteenvedon-tiedot)))
        arvonvahennykset (apply + (map (fn [a]
                                         (if (= (:sakkoryhma a) "arvonvahennyssanktio")
                                           (+ (:maara a) (:indeksikorjaus a))
                                           0))
                                    (:sanktiot yhteenvedon-tiedot)))
        ;; Alitukset ja ylitykset
        tavoitehinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-ylitys)
        tavoitehinnan-alituspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-alitus)
        ;; Jos validoinnit on käytössä ja hoitovuosi on kesken, niin päätöksiä ei anneta frontille.
        ;; Lasketaan siis tavoitehinnan ylitys ja alitus olemassa olevista luvuista.
        tavoitehinnan-ylitys (if (and (not tavoitehinnan-ylityspaatos) (> toteuma-yht hoitovuoden-lopun-tavoitehinta))
                               (- toteuma-yht hoitovuoden-lopun-tavoitehinta)
                               (or (:ylityksen_maara tavoitehinnan-ylityspaatos) 0))
        tavoitehinnan-alitus (if (and (not tavoitehinnan-alituspaatos) (< toteuma-yht hoitovuoden-lopun-tavoitehinta))
                               (- hoitovuoden-lopun-tavoitehinta toteuma-yht)
                               (or (:alituksen_maara tavoitehinnan-alituspaatos) 0))
        tavoitepalkkio (or (arvo-paatoksesta tavoitehinnan-alituspaatos :tavoitepalkkio) 0)
        seuraavan-vuoden-hankintakustannusten-alennus (or (arvo-paatoksesta tavoitehinnan-alituspaatos :siirron_maara) 0)

        tilaajan-tavoitehinnan-ylitysprosentti (:tilaajan_prosentti tavoitehinnan-ylityspaatos)
        urakoitsijan-tavoitehinnan-ylitysprosentti (:urakoitsijan_prosentti tavoitehinnan-ylityspaatos)
        tilaajan-osuus-tavoitehinnan-ylitys (or (arvo-paatoksesta tavoitehinnan-ylityspaatos :urakoitsija_maksaa) 0)
        urakoitsijan-osuus-tavoitehinnan-ylitys (or (arvo-paatoksesta tavoitehinnan-ylityspaatos :tilaaja_maksaa) 0)
        kattohinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (if (and (not kattohinnan-ylityspaatos) (> toteuma-yht hoitovuoden-lopun-kattohinta))
                             (- toteuma-yht hoitovuoden-lopun-kattohinta)
                             (or (:ylityksen_maara kattohinnan-ylityspaatos) 0))
        ;; Niputetaan siirrot yhdelle riville
        siirto-seuraavan-vuoden-hankintakustannuksiin (- (or (arvo-paatoksesta kattohinnan-ylityspaatos :siirrettava_maara) 0)
                                                        seuraavan-vuoden-hankintakustannusten-alennus)
        urakoitsijan-hyvitysosuus (or (arvo-paatoksesta kattohinnan-ylityspaatos :urakoitsija_maksaa) 0)
        hoidonjohtopalkkiopaatos (valikatselmus-tiedot/ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (arvo-paatoksesta hoidonjohtopalkkiopaatos :hoidonjohtopalkkio_muutos) 0)]
    [:div.valikatselmus-yhteenveto {:aria-live "polite"}
     ;;Tämä :aria-live on tässä ruudunlukijaa varten, jotta se jätä tätä DOM:ssa linkin jälkeen olevaa h3-otsikkoa lukematta (tapahtui ainakin Windowsin Lukija-toiminnolla)
     [:h2.yhteenveto "Yhteenveto"]
     [:div.flex-row.summa-rivi-ylin
      [:span "Hoitovuoden alun indeksikorjattu tavoitehinta"]
      [:span (fmt/euro-opt false hoitovuoden-alun-indeksikorjattu-tavoitehinta)]]
     (if (:muutosten_hallinta urakan-parametrit)
       ;; Pysyvät muutokset ja toteutumiin perustuvat muutokset
       [:div
        [:div.flex-row.summa-rivi
         [:span "Tavoitehinnan muutokset"]
         [:span (str (when (> pysyvat-muutokset-toteuma-muutokset 0) "+") (fmt/euro-opt false pysyvat-muutokset-toteuma-muutokset))]]
        [:div.flex-row.summa-rivi
         [:span "• Kirjallisesti sovitut muutokset"]
         [:span (str (when (> pysyvat-muutokset 0) "+") (fmt/euro-opt false pysyvat-muutokset))]]
        [:div.flex-row.summa-rivi
         [:span "• Toteumiin perustuvat muutokset"]
         [:span (str (when (> toteuma-muutokset 0) "+") (fmt/euro-opt false toteuma-muutokset))]]]
       ;; Käsin kirjatut tavoitehinnan oikaisut
       [:div.flex-row.summa-rivi
        [:span "Tavoitehinnan muutokset"]
        [:span (str (when (> tavoitehinnan-muutokset 0) "+") (fmt/euro-opt false tavoitehinnan-muutokset))]])
     [:div.flex-row.summa-rivi
      [:span.laskenta-rivi-lukema "Hoitovuoden lopun tavoitehinta"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false hoitovuoden-lopun-tavoitehinta)]]
     [:div.flex-row.summa-rivi
      [:span.laskenta-rivi-lukema "Hoitovuoden lopun kattohinta"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false hoitovuoden-lopun-kattohinta)]]

     [:h3 "Tavoitehintaan kuuluvat toteutuneet kustannukset"]
     [:div.flex-row.summa-rivi-ylin
      [:span "Hankintakustannukset"]
      [:span (fmt/euro-opt false hankintakustannukset)]]
     [:div.flex-row.summa-rivi
      [:span "Erillishankinnat"]
      [:span (fmt/euro-opt false erillishankinnat)]]
     [:div.flex-row.summa-rivi
      [:span "Johto- ja hallintokorvaus"]
      [:span (fmt/euro-opt false johto-ja-hallintokorvaus)]]
     [:div.flex-row.summa-rivi
      [:span "Hoidonjohtopalkkio"]
      [:span (fmt/euro-opt false hoidonjohtopalkkio)]]
     (when (> muut-kulut 0)
       [:div.flex-row.summa-rivi
        [:span "Muut kulut"]
        [:span (fmt/euro-opt false muut-kulut)]])
     [:hr]
     [:div.flex-row.summa-rivi-ylin
      [:span.laskenta-rivi-lukema "Toteutuma yhteensä"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false toteuma-yht)]]
     ;; Ei näytetä tavoitehinnan ylitystä, mikäli ei ole ylitystä
     (when (or tavoitehinnan-ylityspaatos (and (not tavoitehinnan-ylityspaatos) (not= 0 tavoitehinnan-ylitys)))
       [:div.flex-row.summa-rivi
        [:span {:class (when (> tavoitehinnan-ylitys 0)
                         "negatiivinen-numero")} "Tavoitehinnan ylitys"]
        [:span {:class (when (> tavoitehinnan-ylitys 0)
                         "negatiivinen-numero")} (fmt/euro-opt false tavoitehinnan-ylitys)]])
     ;; Näytetään tavoitehinnan-alitusrivi mikäli alitus on olemassa
     (when (or tavoitehinnan-alituspaatos (and (not tavoitehinnan-alituspaatos) (not= 0 tavoitehinnan-alitus)))
       [:div.flex-row.summa-rivi
        [:span {:class (when (< 0 tavoitehinnan-alitus)
                         "positiivinen-numero")} "Tavoitehinnan alitus"]
        [:span {:class (when (< 0 tavoitehinnan-alitus)
                         "positiivinen-numero")} (fmt/euro-opt false tavoitehinnan-alitus)]])
     ;; Näytetään kattohinnna ylitysrivi, mikäli kattohinnan ylitys on olemassa
     (when (> kattohinnan-ylitys 0)
       [:div.flex-row.summa-rivi
        [:span {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                         "negatiivinen-numero")} "Kattohinnan ylitys"]
        [:span {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                         "negatiivinen-numero")} (fmt/euro-opt false kattohinnan-ylitys)]])

     ;; URAKOITSIJAN SAATAVAT
     [:h3 "Urakoitsijan saatavat"]
     [:div.flex-row.summa-rivi-ylin
      [:span "Lupausbonus"]
      [:span (fmt/euro-opt false lupausbonus)]]
     [:div.flex-row.summa-rivi
      [:span "Asiakastyytyväisyysbonus"]
      [:span (fmt/euro-opt false asiakastyytyvaisyysbonus)]]
     [:div.flex-row.summa-rivi
      [:span "Muut bonukset"]
      [:span (fmt/euro-opt false muut-bonukset)]]
     [:div.flex-row.summa-rivi
      [:span "Tavoitepalkkio"]
      [:span (fmt/euro-opt false tavoitepalkkio)]]
     (when tavoitehinnan-ylityspaatos
       [:div.flex-row.summa-rivi
        [:span "Tavoitehinnan ylitys " (when (> tilaajan-tavoitehinnan-ylitysprosentti 0)
                                         (str "(" tilaajan-tavoitehinnan-ylitysprosentti "%)"))]
        [:span (if (> urakoitsijan-osuus-tavoitehinnan-ylitys 0)
                 (fmt/euro-opt false urakoitsijan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt false 0))]])
     ;; Jos hoidonjohtopalkkio on positiivinen, niin se on urakoitsijan saatavia.
     ;; Jos hoitovuoden lopun tavoitehinta ilman indeksitarkastuksia on enemmmän kuin 5% suurempi kuin tarjouksen tavoitehinta
     ;; niin hoidonjohtopalkkiota muutetaan. Jos se ei ole muuttunut yli 5%, niin muutos on nolla ja silloin näytetään nollaa.
     (if (:id hoidonjohtopalkkiopaatos)
       (if (>= hoidonjohtopalkkion-muutos 0)
         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false hoidonjohtopalkkion-muutos)]]
         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false 0)]])
       [:div.flex-row.summa-rivi
        [:span "Hoidonjohtopalkkion muutos"]
        [:span (fmt/euro-opt false 0)]])

     ; TILAAJAN SAATAVAT
     [:h3 "Tilaajan saatavat"]
     [:div.flex-row.summa-rivi-ylin
      [:span "Lupaussanktio"]
      [:span (fmt/euro-opt false lupaussanktio)]]
     [:div.flex-row.summa-rivi
      [:span "Muut sanktiot"]
      [:span (fmt/euro-opt false muut-sanktiot)]]
     [:div.flex-row.summa-rivi
      [:span "Arvonvähennykset"]
      [:span (fmt/euro-opt false arvonvahennykset)]]
     (when tavoitehinnan-ylityspaatos
       [:div.flex-row.summa-rivi
        [:span "Tavoitehinnan ylitys " (when (> urakoitsijan-tavoitehinnan-ylitysprosentti 0)
                                         (str "(" urakoitsijan-tavoitehinnan-ylitysprosentti "%)"))]
        [:span (if (> tavoitehinnan-ylitys 0)
                 (fmt/euro-opt false tilaajan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt false 0))]])
     (when kattohinnan-ylityspaatos
       [:div.flex-row.summa-rivi
        [:span "Kattohinnan ylitys"]
        [:span (if (> urakoitsijan-hyvitysosuus 0)
                 (fmt/euro-opt false urakoitsijan-hyvitysosuus)
                 (fmt/euro-opt false 0))]])

     ;; Kun hoidonjohtopalkkio on negatiivinen, niin se on tilaajan saatavia
     (if (:id hoidonjohtopalkkiopaatos)
       (if (< hoidonjohtopalkkion-muutos 0)
         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false (* -1 hoidonjohtopalkkion-muutos))]] ;; KAikki yhteenvedon luvut näytetään positiivisena
         [:div.flex-row.summa-rivi
          [:span "Hoidonjohtopalkkion muutos"]
          [:span (fmt/euro-opt false 0)]])
       [:div.flex-row.summa-rivi
        [:span "Hoidonjohtopalkkion muutos"]
        [:span (fmt/euro-opt false 0)]])

     [:h3 "Siirrot"]
     [:div.flex-row
      [:span "Siirto seuraavan vuoden hankintakustannuksiin"]
      [:span (fmt/euro-opt false siirto-seuraavan-vuoden-hankintakustannuksiin)]]]))
