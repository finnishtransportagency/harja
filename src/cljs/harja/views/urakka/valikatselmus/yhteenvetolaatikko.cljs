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
        lupausbonus (or (and
                          (get-in yhteenvedon-tiedot [:lupaustiedot :yhteenveto :valikatselmus-tehty-urakalle?])
                          (:toteutunut_summa (first (filter #(when (= "lupausbonus" (:maksutyyppi %))
                                                               %) (get-in yhteenvedon-tiedot [:kustannukset :bonukset :tehtavat]))))) 0)

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
        ;; Tavoitepalkkio tulee negatiivisena lukuna, joten käännetään se ympäri tähän yhteenvetolaatikkoon
        tavoitepalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :tavoitepalkkio :toimenpide-toteutunut-summa]) 0)
        tavoitepalkkio (if (zero? tavoitepalkkio) tavoitepalkkio (* tavoitepalkkio -1))

        ;; Tilaajan saatavat
        lupaussanktio (or
                        (and
                          (get-in yhteenvedon-tiedot [:lupaustiedot :yhteenveto :valikatselmus-tehty-urakalle?])
                          (get-in yhteenvedon-tiedot [:lupaustiedot :yhteenveto :bonus-tai-sanktio :sanktio])) 0)
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

        tilaajan-tavoitehinnan-ylitysprosentti (:tilaajan_prosentti tavoitehinnan-ylityspaatos)
        urakoitsijan-tavoitehinnan-ylitysprosentti (:urakoitsijan_prosentti tavoitehinnan-ylityspaatos)
        tilaajan-osuus-tavoitehinnan-ylitys (or (:urakoitsija_maksaa tavoitehinnan-ylityspaatos) 0)
        urakoitsijan-osuus-tavoitehinnan-ylitys (or (:tilaaja_maksaa tavoitehinnan-ylityspaatos) 0)
        kattohinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (or (:ylityksen_maara kattohinnan-ylityspaatos) 0)
        siirto-seuraavan-vuoden-hankintakustannuksiin (or (:siirrettava_maara kattohinnan-ylityspaatos) 0)
        urakoitsijan-hyvitysosuus (or (:urakoitsija_maksaa kattohinnan-ylityspaatos) 0)
        hoidonjohtopalkkiopaatos (valikatselmus-tiedot/ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (:hoidonjohtopalkkio_muutos hoidonjohtopalkkiopaatos) 0)]
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
