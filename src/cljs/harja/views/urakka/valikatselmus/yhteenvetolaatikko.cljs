(ns harja.views.urakka.valikatselmus.yhteenvetolaatikko
  (:require [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.urakka :as tila]))

;;TODO: Kunhan yhteenvetolaatikko kirjoitetaan uusiksi, niin siirrä tämä ja kaikki mahdollinen muu logiikka backendiin
(defn- aseta-tavoitehinnan-ylitysprosentti [urakkatyyppi valittu-hoitokauden-alkuvuosi urakan-alkuvuosi]
  (let [; MHU+ urakoilla on 50/50, ja vuodelle 2025 tulee 25/75. Mutta tässä vaiheessa kaikki on 70/30
        ]
    {:urakoitsija 30
     :tilaaja 70}))

(defn yhteenvetolaatikko [e! app]
  (let [urakkatyyppi "MHU" ;; Myöhemmin voi olla myös MHU+
        paatokset (get-in app [:yhteenveto :paatokset])
        yhteenvedon-tiedot (:yhteenveto app)
        valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero (-> @tila/yleiset :urakka :alkupvm) valittu-hoitokauden-alkuvuosi)

        ;; Toteutuneet kustannukset
        hankintakustannukset (or (get-in yhteenvedon-tiedot [:kustannukset :hankintakustannukset-toteutunut]) 0)
        erillishankinnat (or (get-in yhteenvedon-tiedot [:kustannukset :erillishankinnat-toteutunut]) 0)
        johto-ja-hallintokorvaus (or (get-in yhteenvedon-tiedot [:kustannukset :johto-ja-hallintokorvaus-toteutunut]) 0)
        hoidonjohtopalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :hoidonjohdonpalkkio-toteutunut]) 0)
        toteuma-yht (get-in yhteenvedon-tiedot [:kustannukset-yhteensa :yht-toteutunut-summa])

        ;; Urakoitsijan saatavat
        lupausbonus (or (:toteutunut_summa (first (filter #(when (= "lupausbonus" (:maksutyyppi %))
                                                             %) (get-in yhteenvedon-tiedot [:kustannukset :bonukset :tehtavat])))) 0)
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
        tavoitepalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :tavoitepalkkio :toimenpide-toteutunut-summa]) 0)

        ;; Tilaajan saatavat
        lupaussanktio (or
                        (and
                          (get-in yhteenvedon-tiedot [:lupaustiedot :yhteenveto :valikatselmus-tehty-urakalle?])
                          (get-in yhteenvedon-tiedot [:lupaustiedot :yhteenveto :bonus-tai-sanktio :sanktio])) 0)
        ;; Tilaajalle sanktio on positiivinen luku
        lupaussanktio (if (zero? lupaussanktio) lupaussanktio (- lupaussanktio))
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
        totutuneet-kustannukset (or (get-in app [:yhteenveto :kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        oikaistu-tavoitehinta (get-in yhteenvedon-tiedot [:budjettitavoite :tavoitehinta-oikaistu])
        oikaistu-kattohinta (get-in yhteenvedon-tiedot [:budjettitavoite :kattohinta-oikaistu])
        ;; Tavoitehinnan ylitys otetaan huomioon vasta, kun päätös on tehty
        tavoitehinnan-ylityspaatos (some #(when (= (str (:harja.domain.kulut.valikatselmus/tyyppi %)) "tavoitehinnan-ylitys")
                                            true)
                                     paatokset)
        tavoitehinnan-ylitys (if (> totutuneet-kustannukset oikaistu-kattohinta)
                               (- oikaistu-kattohinta oikaistu-tavoitehinta)
                               (- totutuneet-kustannukset oikaistu-tavoitehinta))

        tavoitehinnan-alitus (if (< totutuneet-kustannukset oikaistu-tavoitehinta)
                               (- oikaistu-tavoitehinta totutuneet-kustannukset)
                               0)

        tavoitehinnan-ylitysprosentti (aseta-tavoitehinnan-ylitysprosentti urakkatyyppi valittu-hoitokauden-alkuvuosi urakan-alkuvuosi)
        tilaajan-osuus-tavoitehinnan-ylitys (if tavoitehinnan-ylityspaatos
                                              (* (/ (:urakoitsija tavoitehinnan-ylitysprosentti) 100) tavoitehinnan-ylitys)
                                              0)
        urakoitsijan-osuus-tavoitehinnan-ylitys (if tavoitehinnan-ylityspaatos
                                              (* (/ (:tilaaja tavoitehinnan-ylitysprosentti) 100) tavoitehinnan-ylitys)
                                              0)

        ;; Kattohinnan ylitys otetaan huomioon vasta, kun päätös on tehty
        kattohinnan-ylityspaatos (some #(when (= (str (:harja.domain.kulut.valikatselmus/tyyppi %)) "kattohinnan-ylitys")
                                            true)
                                     paatokset)
        kattohinnan-ylitys (if (> totutuneet-kustannukset oikaistu-kattohinta)
                             (- totutuneet-kustannukset oikaistu-kattohinta)
                             0)

        ;; Siirrot
        filtteroi-paatos-fn (fn [paatoksen-tyyppi]
                              (first (filter #(and (= (::valikatselmus/hoitokauden-alkuvuosi %) valittu-hoitokauden-alkuvuosi)
                                                (= (::valikatselmus/tyyppi %) (name paatoksen-tyyppi))) (:paatokset app))))
        kattohinnan-ylitys-paatos (filtteroi-paatos-fn :kattohinnan-ylitys)
        siirto-seuraavan-vuoden-hankintakustannuksiin (if (::valikatselmus/siirto kattohinnan-ylitys-paatos)
                                                        (::valikatselmus/siirto kattohinnan-ylitys-paatos)
                                                        0)
        ;; Urakoitsijan hyvitysosuus voi olla nolla, vaikka on kattohinnan ylitystä, koska se voidaan siirtää
        ;; kokonaisuudessaan seuraavalle vuodelle.
        urakoitsijan-hyvitysosuus (- kattohinnan-ylitys siirto-seuraavan-vuoden-hankintakustannuksiin)]
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
     (when (> tavoitehinnan-ylitys 0)
       [:div.rivi [:span.bold {:class (when (and tavoitehinnan-ylitys (> tavoitehinnan-ylitys 0))
                                        "negatiivinen-numero")} "Tavoitehinnan ylitys"]
        [:span.bold {:class (when (and tavoitehinnan-ylitys (> tavoitehinnan-ylitys 0))
                              "negatiivinen-numero")} (fmt/euro-opt tavoitehinnan-ylitys)]]
       )
       ;; Näytetään tavoitehinnan-alitusrivi mikäli alitus on olemassa
     (when (> tavoitehinnan-alitus 0)
       [:div.rivi [:span.bold {:class (when (and tavoitehinnan-alitus (< 0 tavoitehinnan-alitus))
                                        "positiivinen-numero")} "Tavoitehinnan alitus"]
        [:span.bold {:class (when (and tavoitehinnan-alitus (< 0 tavoitehinnan-alitus))
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
       [:div.rivi [:span "Tavoitehinnan ylitys " (when (> (:tilaaja tavoitehinnan-ylitysprosentti) 0)
                                                   (str "(" (:tilaaja tavoitehinnan-ylitysprosentti) "%)"))]
        [:span (if (> urakoitsijan-osuus-tavoitehinnan-ylitys 0)
                 (fmt/euro-opt urakoitsijan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt 0))]])

     [:h3 [:span "Tilaajan saatavat"]]
     [:div.rivi [:span "Lupaussanktio"]
      [:span (fmt/euro-opt lupaussanktio)]]
     [:div.rivi [:span "Muut sanktiot"]
      [:span (fmt/euro-opt muut-sanktiot)]]
     [:div.rivi [:span "Arvonvähennykset"]
      [:span (fmt/euro-opt arvonvahennykset)]]
     (when tavoitehinnan-ylityspaatos
       [:div.rivi [:span "Tavoitehinnan ylitys " (when (> (:urakoitsija tavoitehinnan-ylitysprosentti) 0)
                                                   (str "(" (:urakoitsija tavoitehinnan-ylitysprosentti) "%)"))]
        [:span (if (> tavoitehinnan-ylitys 0)
                 (fmt/euro-opt tilaajan-osuus-tavoitehinnan-ylitys)
                 (fmt/euro-opt 0))]])
     (when kattohinnan-ylityspaatos
      [:div.rivi [:span "Kattohinnan ylitys"]
       [:span (if (> urakoitsijan-hyvitysosuus 0)
                (fmt/euro-opt urakoitsijan-hyvitysosuus)
                (fmt/euro-opt 0))]])

     [:h3 [:span "Siirrot"]]
     [:div.rivi [:span "Siirto seuraavan vuoden hankintakustannuksiin"]
      [:span (fmt/euro-opt siirto-seuraavan-vuoden-hankintakustannuksiin)]]]))
