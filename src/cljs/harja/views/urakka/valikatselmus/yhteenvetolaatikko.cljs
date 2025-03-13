(ns harja.views.urakka.valikatselmus.yhteenvetolaatikko
  (:require [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.urakka :as tila]))

;;TODO: Kunhan yhteenvetolaatikko kirjoitetaan uusiksi, niin siirrä tämä ja kaikki mahdollinen muu logiikka backendiin
(defn- aseta-tavoitehinnan-ylitysprosentti [urakkatyyppi valittu-hoitokauden-alkuvuosi urakan-alkuvuosi]
  (let [; MHU+ urakoilla on 50/50, ja vuodelle 2025 tulee 25/75. Mutta tässä vaiheessa kaikki on 70/30
        ]
    {:urakoitsija 30
     :tilaaja 70}))

(defn arvo-paatoksesta
  "Monet euromääräiset arvot päätöksestä kannattaa hakea vasta, kun päätös on tehty. Ja toisaalta päätöksissä voi olla myös tietoja,
  jotka voidaan näyttää, vaikka päätöstä ei ole vielä tehty. Eli verrataan tietokanta id:tä siihen, että onko päätös tehty."
  [paatos avain]
  (when (:id paatos)
    (get paatos avain)))

(defn yhteenvetolaatikko [e! app]
  (let [paatokset (get-in app [:paatokset])
        yhteenvedon-tiedot (:yhteenveto app)

        ;; Toteutuneet kustannukset
        hankintakustannukset (or (get-in yhteenvedon-tiedot [:kustannukset :hankintakustannukset-toteutunut]) 0)
        erillishankinnat (or (get-in yhteenvedon-tiedot [:kustannukset :erillishankinnat-toteutunut]) 0)
        johto-ja-hallintokorvaus (or (get-in yhteenvedon-tiedot [:kustannukset :johto-ja-hallintokorvaus-toteutunut]) 0)
        hoidonjohtopalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :hoidonjohdonpalkkio-toteutunut]) 0)
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
        oikaistu-tavoitehinta (get-in yhteenvedon-tiedot [:budjettitavoite :tavoitehinta-oikaistu])
        oikaistu-kattohinta (get-in yhteenvedon-tiedot [:budjettitavoite :kattohinta-oikaistu])
        tavoitehinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-ylitys)
        tavoitehinnan-alituspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-alitus)
        tavoitehinnan-ylitys (or (:ylityksen_maara tavoitehinnan-ylityspaatos) 0)
        tavoitehinnan-alitus (or (:alituksen_maara tavoitehinnan-alituspaatos) 0)
        tavoitepalkkio (or (arvo-paatoksesta tavoitehinnan-alituspaatos :tavoitehinta) 0)
        seuraavan-vuoden-hankintakustannusten-alennus (or (arvo-paatoksesta tavoitehinnan-alituspaatos :siirron_maara) 0)

        tilaajan-tavoitehinnan-ylitysprosentti (:tilaajan_prosentti tavoitehinnan-ylityspaatos)
        urakoitsijan-tavoitehinnan-ylitysprosentti (:urakoitsijan_prosentti tavoitehinnan-ylityspaatos)
        tilaajan-osuus-tavoitehinnan-ylitys (or (arvo-paatoksesta tavoitehinnan-ylityspaatos :urakoitsija_maksaa) 0)
        urakoitsijan-osuus-tavoitehinnan-ylitys (or (arvo-paatoksesta tavoitehinnan-ylityspaatos :tilaaja_maksaa) 0)
        kattohinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (or (:ylityksen_maara kattohinnan-ylityspaatos) 0)
        ;; Niputetaan siirrot yhdelle riville
        siirto-seuraavan-vuoden-hankintakustannuksiin (- (or (arvo-paatoksesta kattohinnan-ylityspaatos :siirrettava_maara) 0)
                                                        seuraavan-vuoden-hankintakustannusten-alennus)
        urakoitsijan-hyvitysosuus (or (arvo-paatoksesta kattohinnan-ylityspaatos :urakoitsija_maksaa) 0)
        hoidonjohtopalkkiopaatos (valikatselmus-tiedot/ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (arvo-paatoksesta hoidonjohtopalkkiopaatos :hoidonjohtopalkkio_muutos) 0)]
    [:div.valikatselmus-yhteenveto.elevation-2
     [:h2 [:span "Yhteenveto"]]
     [:div.rivi [:span.bold "Hoitokauden lopun tavoitehinta"]
      [:span (fmt/euro-opt oikaistu-tavoitehinta)]]
     [:div.rivi [:span.bold "Hoitokauden lopun kattohinta"]
      [:span (fmt/euro-opt oikaistu-kattohinta)]]

     [:h3 [:span "Tavoitehintaan kuuluvat toteutuneet kustannukset"]]
     [:div.rivi [:span "Hankintakustannukset"]
      [:span (fmt/euro-opt hankintakustannukset)]]
     [:div.rivi [:span "Erillishankinnat"]
      [:span (fmt/euro-opt erillishankinnat)]]
     [:div.rivi [:span "Johto- ja hallintokorvaus"]
      [:span (fmt/euro-opt johto-ja-hallintokorvaus)]]
     [:div.rivi [:span "Hoidonjohtopalkkio"]
      [:span (fmt/euro-opt hoidonjohtopalkkio)]]
     [:hr]
     [:div.rivi [:span.bold "Toteutuma yhteensä"]
      [:span (fmt/euro-opt toteuma-yht)]]
     ;; Ei näytetä tavoitehinnan ylitystä, mikäli ei ole ylitystä
     (when tavoitehinnan-ylityspaatos
       [:div.rivi [:span.bold {:class (when (> tavoitehinnan-ylitys 0)
                                        "negatiivinen-numero")} "Tavoitehinnan ylitys"]
        [:span.bold {:class (when (> tavoitehinnan-ylitys 0)
                              "negatiivinen-numero")} (fmt/euro-opt tavoitehinnan-ylitys)]]
       )
     ;; Näytetään tavoitehinnan-alitusrivi mikäli alitus on olemassa
     (when tavoitehinnan-alituspaatos
       [:div.rivi [:span.bold {:class (when (< 0 tavoitehinnan-alitus)
                                        "positiivinen-numero")} "Tavoitehinnan alitus"]
        [:span.bold {:class (when (< 0 tavoitehinnan-alitus)
                              "positiivinen-numero")} (fmt/euro-opt tavoitehinnan-alitus)]])
     ;; Näytetään kattohinnna ylitysrivi, mikäli kattohinnan ylitys on olemassa
     (when (> kattohinnan-ylitys 0)
       [:div.rivi [:span.bold {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                                        "negatiivinen-numero")} "Kattohinnan ylitys"]
        [:span.bold {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                              "negatiivinen-numero")} (fmt/euro-opt kattohinnan-ylitys)]])

     [:h3 [:span "Urakoitsijan saatavat"]]
     [:div.rivi [:span "Lupausbonus"]
      [:span (fmt/euro-opt lupausbonus)]]
     [:div.rivi [:span "Asiakastyytyväisyysbonus"]
      [:span.luku (fmt/euro-opt asiakastyytyvaisyysbonus)]]
     [:div.rivi [:span "Muut bonukset"]
      [:span (fmt/euro-opt muut-bonukset)]]
     [:div.rivi [:span "Tavoitepalkkio"]
      [:span (fmt/euro-opt tavoitepalkkio)]]
     (when tavoitehinnan-ylityspaatos
       [:div.rivi [:span "Tavoitehinnan ylitys " (when (> tilaajan-tavoitehinnan-ylitysprosentti 0)
                                                   (str "(" tilaajan-tavoitehinnan-ylitysprosentti "%)"))]
        [:span (if (> urakoitsijan-osuus-tavoitehinnan-ylitys 0)
                 (fmt/euro-opt urakoitsijan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt 0))]])
     [:div.rivi [:span "Hoidonjohtopalkkion muutos"]
      [:span (fmt/euro-opt hoidonjohtopalkkion-muutos)]]

; TILAAJAN SAATAVAT
     [:h3 [:span "Tilaajan saatavat"]]
     [:div.rivi [:span "Lupaussanktio"]
      [:span (fmt/euro-opt lupaussanktio)]]
     [:div.rivi [:span "Muut sanktiot"]
      [:span (fmt/euro-opt muut-sanktiot)]]
     [:div.rivi [:span "Arvonvähennykset"]
      [:span (fmt/euro-opt arvonvahennykset)]]
     (when tavoitehinnan-ylityspaatos
       [:div.rivi [:span "Tavoitehinnan ylitys " (when (> urakoitsijan-tavoitehinnan-ylitysprosentti 0)
                                                   (str "(" urakoitsijan-tavoitehinnan-ylitysprosentti "%)"))]
        [:span (if (> tavoitehinnan-ylitys 0)
                 (fmt/euro-opt tilaajan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt 0))]])
     (when kattohinnan-ylityspaatos
       [:div.rivi [:span "Kattohinnan ylitys"]
        [:span (if (> urakoitsijan-hyvitysosuus 0)
                 (fmt/euro-opt urakoitsijan-hyvitysosuus)
                 (fmt/euro-opt 0))]])

     [:div.rivi [:span "Hoidonjohtopalkkion muutos"]
      [:span (fmt/euro-opt hoidonjohtopalkkion-muutos)]]

     [:h3 [:span "Siirrot"]]
     [:div.rivi [:span "Siirto seuraavan vuoden hankintakustannuksiin"]
      [:span (fmt/euro-opt siirto-seuraavan-vuoden-hankintakustannuksiin)]]]))
