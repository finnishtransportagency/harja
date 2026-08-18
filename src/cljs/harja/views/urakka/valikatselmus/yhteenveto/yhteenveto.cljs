(ns harja.views.urakka.valikatselmus.yhteenveto.yhteenveto
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valikatselmus.yhteenveto.luvut :as luvut]
            [harja.views.urakka.valikatselmus.yhteenveto.sanktiot-ja-bonukset :as bonukset]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]))


(defn osio-lopun-tavoite-ja-katto
  [{:keys [urakan-parametrit hoitokauden-alkuvuosi]}
   {:keys [hoitovuoden-alun-indeksikorjattu-tavoitehinta tavoitehinnan-muutokset
           aktiiviset-pysyvat-muutokset menneet-pysyvat-muutokset
           toteumiin-perustuvat-muutokset-yht pysyvat-muutokset-toteuma-muutokset-yht
           arvonvahennykset-yht hoitokauden_lopun_indeksikorjaus
           hoitovuoden-lopun-tavoitehinta hoitovuoden-lopun-kattohinta]}]
  (let [;; Joko uusi urakka, tai >= 26 vuosi
        nayta-arvonvahennykset? (or
                                  (and
                                    arvonvahennykset-yht
                                    (:muutosten_hallinta urakan-parametrit))
                                  (>= hoitokauden-alkuvuosi 2026))]

    ;; Tämä :aria-live on tässä ruudunlukijaa varten, jotta se jätä tätä DOM:ssa 
    ;; linkin jälkeen olevaa h3-otsikkoa lukematta (tapahtui ainakin Windowsin Lukija-toiminnolla)
    [:div.valikatselmus-yhteenveto.osio {:aria-live "polite"}

     [:h2.yhteenveto "Yhteenveto"]
     [:h3.padding-bottom-16 "Hoitovuoden lopun tavoite- ja kattohinta"]

     [:div.flex-row.summa-rivi-ylin
      [:span "Hoitovuoden alun indeksikorjattu tavoitehinta"]
      [:span (fmt/euro-opt false hoitovuoden-alun-indeksikorjattu-tavoitehinta)]]

     (when menneet-pysyvat-muutokset
       [:div.flex-row.summa-rivi
        [:span.sisennys "• Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)"]
        [:span (fmt/euro-opt false menneet-pysyvat-muutokset)]])

     (if (:muutosten_hallinta urakan-parametrit)
       ;; ----------------------------------------------------
       ;; Uudemmat urakat
       ;; Pysyvät muutokset ja toteutumiin perustuvat muutokset
       [:<>
        [:div.flex-row.summa-rivi
         [:span "Tavoitehinnan muutokset"]
         [:span (str (when (> pysyvat-muutokset-toteuma-muutokset-yht 0) "+")
                  (fmt/euro-opt false pysyvat-muutokset-toteuma-muutokset-yht))]]

        (when aktiiviset-pysyvat-muutokset
          [:div.flex-row.summa-rivi
           [:span.sisennys "• Kirjallisesti sovitut muutokset"]
           [:span (str (when (> aktiiviset-pysyvat-muutokset 0) "+")
                    (fmt/euro-opt false aktiiviset-pysyvat-muutokset))]])

        [:div.flex-row.summa-rivi
         [:span.sisennys "• Toteumiin perustuvat muutokset"]
         [:span (str (when (> toteumiin-perustuvat-muutokset-yht 0) "+")
                  (fmt/euro-opt false toteumiin-perustuvat-muutokset-yht))]]

        ;; MHU 25
        ;; Vanhemmilla urakoilla tämä näkyy sanktiot osiossa
        (when nayta-arvonvahennykset?
          [:div.flex-row.summa-rivi
           [:span.sisennys "• Arvonvähennysten tavoitehintamuutokset"]
           [:span (fmt/euro-opt false arvonvahennykset-yht)]])]


       ;; ----------------------------------------------------
       ;; Vanhemmat urakat
       ;; Käsin kirjatut tavoitehinnan oikaisut
       [:<>
        [:div.flex-row.summa-rivi
         [:span "Tavoitehinnan muutokset"]
         [:span (str (when (> tavoitehinnan-muutokset 0) "+")
                  (fmt/euro-opt false tavoitehinnan-muutokset))]]

        (when nayta-arvonvahennykset?
          [:div.flex-row.summa-rivi
           [:span "Arvonvähennysten tavoitehintamuutokset"]
           [:span (fmt/euro-opt false arvonvahennykset-yht)]])])


     [:div.flex-row.summa-rivi
      [:span "Hoitovuoden lopun indeksikorjaus"]
      [:span (fmt/euro-opt false hoitokauden_lopun_indeksikorjaus)]]

     [:hr]

     [:div.flex-row.summa-rivi
      [:span.laskenta-rivi-lukema "Hoitovuoden lopun tavoitehinta"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false hoitovuoden-lopun-tavoitehinta)]]

     [:div.flex-row.summa-rivi
      [:span.laskenta-rivi-lukema "Hoitovuoden lopun kattohinta"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false hoitovuoden-lopun-kattohinta)]]]))


(defn osio-toteutuneet-kustannukset
  [{:keys [paatokset hoitokauden-alkuvuosi]}
   {:keys [yhteenvedon-tiedot arvonvahennykset-yht
           hoitovuoden-lopun-tavoitehinta hoitovuoden-lopun-kattohinta]}]
  (let [urakan-loppuvuosi (some-> @nav/valittu-urakka :loppupvm pvm/vuosi)
        viimeinen-hoitovuosi? (= hoitokauden-alkuvuosi (dec urakan-loppuvuosi))
        ;; Toteutuneet kustannukset
        ;; Välikatselmuksessa käytetyt Hankintakustannukset ovat eri asia kuin Kustannusten Seurannan Hankintakustannukset/Suunnitellut Hankinnat.
        ;; Välikatselmuksessa Hankintakustannuksiin lisätään toteutuneet Rahavaraukset.
        hankintakustannukset (or
                               (+ (get-in yhteenvedon-tiedot [:kustannukset :hankintakustannukset-toteutunut])
                                 (get-in yhteenvedon-tiedot [:kustannukset :rahavaraukset-toteutunut]))
                               0)
        erillishankinnat (or (get-in yhteenvedon-tiedot [:kustannukset :erillishankinnat-toteutunut]) 0)
        johto-ja-hallintokorvaus (or (get-in yhteenvedon-tiedot [:kustannukset :johto-ja-hallintokorvaus-toteutunut]) 0)
        ;; Muutosten jjh-muutos tyyppiset kulut eivät kuulu kustannusten seurannassa 
        ;; johto-ja-hallintokorvauksiin ja sen vuoksi ne eivät ole tässä mukana.
        ;; Välikatselmuksen yhteenvedossa niille ei kuitenkaan ole muuta paikkaa, 
        ;; niin ne yhdistetään tässä johto-ja-hallintokorvauksiin
        muutokset (get-in yhteenvedon-tiedot [:kustannukset :muutokset])
        jjh-muutokset (or (:toimenpide-toteutunut-summa (first (filter #(= (:toimenpide %) "Johto- ja hallintokorvauksen muutokset") muutokset))) 0)
        johto-ja-hallintokorvaus (+ johto-ja-hallintokorvaus jjh-muutokset)
        hoidonjohtopalkkio (or (get-in yhteenvedon-tiedot [:kustannukset :hoidonjohdonpalkkio-toteutunut]) 0)
        muut-kulut (or (get-in yhteenvedon-tiedot [:kustannukset :muukulu-tavoitehintainen-toteutunut]) 0)
        toteuma-yht (or (get-in yhteenvedon-tiedot [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)

        ;; Alitukset ja ylitykset
        tavoitehinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-ylitys)
        tavoitehinnan-alituspaatos (valikatselmus-tiedot/ota-paatos paatokset :tavoitehinnan-alitus)

        ;; Jos validoinnit on käytössä ja hoitovuosi on kesken, niin päätöksiä ei anneta frontille.
        ;; Lasketaan siis tavoitehinnan ylitys ja alitus olemassa olevista luvuista.
        tavoitehinnan-ylitys (if (and (not (:id tavoitehinnan-ylityspaatos)) (> toteuma-yht hoitovuoden-lopun-tavoitehinta))
                               (- toteuma-yht hoitovuoden-lopun-tavoitehinta)
                               (luvut/arvo-paatoksesta tavoitehinnan-ylityspaatos :ylityksen_maara))

        tavoitehinnan-alitus (if (and (not tavoitehinnan-alituspaatos) (< toteuma-yht hoitovuoden-lopun-tavoitehinta))
                               (- hoitovuoden-lopun-tavoitehinta toteuma-yht)
                               (or (:alituksen_maara tavoitehinnan-alituspaatos) 0))

        tavoitepalkkio (or (luvut/arvo-paatoksesta tavoitehinnan-alituspaatos :tavoitepalkkio) 0)
        seuraavan-vuoden-hankintakustannusten-alennus (or (luvut/arvo-paatoksesta tavoitehinnan-alituspaatos :siirron_maara) 0)
        kattohinnan-ylityspaatos (valikatselmus-tiedot/ota-paatos paatokset :kattohinnan-ylitys)

        kattohinnan-ylitys (if (and (not (:id kattohinnan-ylityspaatos)) (> toteuma-yht hoitovuoden-lopun-kattohinta))
                             (- toteuma-yht hoitovuoden-lopun-kattohinta)
                             (luvut/arvo-paatoksesta kattohinnan-ylityspaatos :ylityksen_maara))
        ;; Niputetaan siirrot yhdelle riville
        siirto-seuraavan-vuoden-hankintakustannuksiin (- (or (luvut/arvo-paatoksesta kattohinnan-ylityspaatos :siirrettava_maara) 0)
                                                        seuraavan-vuoden-hankintakustannusten-alennus)

        tavoitehinnan-ylitys? (or
                                (:id tavoitehinnan-ylityspaatos)
                                (and
                                  (not (nil? tavoitehinnan-ylitys))
                                  (not tavoitehinnan-ylityspaatos)
                                  (not= 0 tavoitehinnan-ylitys)))

        tavoitehinnan-alitus? (or
                                tavoitehinnan-alituspaatos
                                (and
                                  (not tavoitehinnan-alituspaatos)
                                  (not=
                                    ;; 2 desimaaliin pyöristettynä jos summa on sama
                                    (fmt/pyorista-desimaaliin toteuma-yht 2)
                                    (fmt/pyorista-desimaaliin hoitovuoden-lopun-tavoitehinta 2))
                                  (not= 0 tavoitehinnan-alitus)))

        kattohinnan-ylitys? (> kattohinnan-ylitys 0)
        ympyra-class (if (or tavoitehinnan-ylitys? kattohinnan-ylitys?) "punainen" "vihrea")]

    [:div.valikatselmus-yhteenveto.osio {:aria-live "polite"}
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

     (when arvonvahennykset-yht
       [:div.flex-row.summa-rivi
        [:span "Arvonvähennykset"]
        [:span (fmt/euro-opt false arvonvahennykset-yht)]])

     (when (> muut-kulut 0)
       [:div.flex-row.summa-rivi
        [:span "Muut kulut"]
        [:span (fmt/euro-opt false muut-kulut)]])

     [:hr]

     [:div.flex-row.summa-rivi-ylin
      [:span.laskenta-rivi-lukema "Toteutuma yhteensä"]
      [:span.laskenta-rivi-lukema (fmt/euro-opt false toteuma-yht)]]


     ;; ----------------------------------------------------
     ;; TAVOITEHINNAN YLITYS
     [:div {:class (when (or tavoitehinnan-ylitys? tavoitehinnan-alitus?) (str "toteutuneet-kustannukset " ympyra-class))}
      ;; Ei näytetä tavoitehinnan ylitystä, mikäli ei ole ylitystä
      (when tavoitehinnan-ylitys?
        [:<>
         [:div.flex-row.summa-rivi
          [:h3 {:class (when (> tavoitehinnan-ylitys 0)
                         "negatiivinen-numero")} "Tavoitehinnan ylitys"]

          [:h3 {:class (when (> tavoitehinnan-ylitys 0)
                         "negatiivinen-numero")} (fmt/euro-opt false tavoitehinnan-ylitys)]]

         [:div.flex-row.summa-rivi
          [:span.sisennys (str "• Urakoitsija maksaa (" (:urakoitsijan_prosentti tavoitehinnan-ylityspaatos) "%)")]
          [:span (fmt/euro-opt false (:urakoitsija_maksaa tavoitehinnan-ylityspaatos))]]

         [:div.flex-row.summa-rivi
          [:span.sisennys (str "• Tilaaja maksaa (" (:tilaajan_prosentti tavoitehinnan-ylityspaatos) "%)")]
          [:span (fmt/euro-opt false (:tilaaja_maksaa tavoitehinnan-ylityspaatos))]]])


      ;; ----------------------------------------------------
      ;; TAVOITEHINNAN ALITUS
      (when tavoitehinnan-alitus?
        [:<>
         [:div.flex-row.summa-rivi
          [:h3 {:class (when (< 0 tavoitehinnan-alitus)
                         "positiivinen-numero")} "Tavoitehinnan alitus"]

          [:h3 {:class (when (< 0 tavoitehinnan-alitus)
                         "positiivinen-numero summa")} (fmt/euro-opt false tavoitehinnan-alitus)]]

         [:div.flex-row.summa-rivi
          [:span.sisennys "• Tavoitepalkkio"]
          [:span (fmt/euro-opt false tavoitepalkkio)]]

         [:div.flex-row.summa-rivi
          [:span.sisennys "• Siirto seuraavan vuoden hankintakustannuksiin"]
          [:span (fmt/euro-opt false siirto-seuraavan-vuoden-hankintakustannuksiin)]]])]


     ;; ----------------------------------------------------
     ;; KATTOHINNAN YLITYS
     (when kattohinnan-ylitys?
       ;; Näille erillinen border himmeli
       [:div {:class (str "toteutuneet-kustannukset " ympyra-class)}

        [:div.flex-row.summa-rivi
         [:h3 {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                        "negatiivinen-numero")} "Kattohinnan ylitys"]

         [:h3 {:class (when (and kattohinnan-ylitys (> kattohinnan-ylitys 0))
                        "negatiivinen-numero summa")} (fmt/euro-opt false kattohinnan-ylitys)]]

        [:div.flex-row.summa-rivi
         [:span.sisennys "• Urakoitsija maksaa"]
         [:span (fmt/euro-opt false (:urakoitsija_maksaa kattohinnan-ylityspaatos))]]

        ;; Ei tarvitse näyttää siirtoja viimeisenä hoitovuonna
        (when-not viimeinen-hoitovuosi?
          [:div.flex-row.summa-rivi
           [:span.sisennys "• Siirto seuraavan vuoden hankintakustannuksiin"]
           [:span (fmt/euro-opt false siirto-seuraavan-vuoden-hankintakustannuksiin)]])])]))


(defn yhteenvetolaatikko [_e! app]
  (let [luvut (luvut/yhteenveto-luvut app)]
    [:<>
     (osio-lopun-tavoite-ja-katto app luvut)
     (osio-toteutuneet-kustannukset app luvut)
     (bonukset/osio-bonukset app luvut)
     (bonukset/osio-sanktiot app luvut)
     (bonukset/osio-hoidonjohtopalkkio app luvut)]))
