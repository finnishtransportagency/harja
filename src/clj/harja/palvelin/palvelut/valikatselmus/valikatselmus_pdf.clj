(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf
  (:require [harja.fmt :as fmt]
            [harja.tyokalut.xsl-fo :as xsl-fo]))

(def ^:private reunat {:border-bottom "solid 0.1mm black"
                       :border-top "solid 0.1mm black"
                       :border-left "solid 0.1mm black"
                       :border-right "solid 0.1mm black"})

(defn- rivi [[otsikko arvo]]
  [:fo:table-row
   [:fo:table-cell
    [:fo:block {:padding "1.5mm"} otsikko]]
   [:fo:table-cell
    [:fo:block {:padding "1.5mm" :text-align "right"} (str arvo)]]])

(defn- osio [otsikko rivit]
  [:fo:block {:margin-top "5mm"}
   [:fo:block {:font-size "11pt" :font-weight "bold" :margin-bottom "2mm"} otsikko]
   [:fo:table (merge reunat {:table-layout "fixed"})
    [:fo:table-column {:column-width "70%"}]
    [:fo:table-column {:column-width "30%"}]
    [:fo:table-body (map rivi rivit)]]])

(defn- euro [arvo]
  (fmt/euro-opt false arvo))

(defn- lisaa-plus [arvo]
  (str (when (and (number? arvo) (pos? arvo)) "+") (euro arvo)))

(defn- arvopaatoksesta [paatos avain]
  (when (:id paatos)
    (get paatos avain)))

(defn- ota-paatos [paatokset avain]
  (some #(get % avain) paatokset))

(defn- yhteenveto-rivit [data]
  (let [paatokset (:paatokset data)
        urakan-parametrit (:urakan-parametrit data)
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
        muut-sanktiot (reduce + 0 (map #(if (not (contains? #{"lupaussanktio" "arvonvahennyssanktio"} (:sakkoryhma %)))
                                       (+ (:maara %) (:indeksikorjaus %)) 0) (:sanktiot yhteenveto)))
        tavoitehinnan-ylityspaatos (ota-paatos paatokset :tavoitehinnan-ylitys)
        tavoitehinnan-alituspaatos (ota-paatos paatokset :tavoitehinnan-alitus)
        tavoitehinnan-ylitys (if (and (not (:id tavoitehinnan-ylityspaatos)) (> toteuma-yht hoitovuoden-lopun-tavoitehinta))
                              (- toteuma-yht hoitovuoden-lopun-tavoitehinta)
                              (or (arvopaatoksesta tavoitehinnan-ylityspaatos :ylityksen_maara) 0))
        tavoitehinnan-alitus (if (and (not tavoitehinnan-alituspaatos) (< toteuma-yht hoitovuoden-lopun-tavoitehinta))
                               (- hoitovuoden-lopun-tavoitehinta toteuma-yht)
                               (or (:alituksen_maara tavoitehinnan-alituspaatos) 0))
        tavoitepalkkio (or (arvopaatoksesta tavoitehinnan-alituspaatos :tavoitepalkkio) 0)
        siirto (or (arvopaatoksesta tavoitehinnan-alituspaatos :siirron_maara) 0)
        kattohinnan-ylityspaatos (ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (if (and (not (:id kattohinnan-ylityspaatos)) (> toteuma-yht hoitovuoden-lopun-kattohinta))
                             (- toteuma-yht hoitovuoden-lopun-kattohinta)
                             (or (arvopaatoksesta kattohinnan-ylityspaatos :ylityksen_maara) 0))
        siirto (- (or (arvopaatoksesta kattohinnan-ylityspaatos :siirrettava_maara) 0) siirto)
        urakoitsijan-hyvitysosuus (or (arvopaatoksesta kattohinnan-ylityspaatos :urakoitsija_maksaa) 0)
        hoidonjohtopalkkion-paatos (ota-paatos paatokset :hoidonjohtopalkkion-muutos)
        hoidonjohtopalkkion-muutos (or (arvopaatoksesta hoidonjohtopalkkion-paatos :hoidonjohtopalkkio_muutos) 0)
        tilaajan-ylitysosuus (or (arvopaatoksesta tavoitehinnan-ylityspaatos :tilaaja_maksaa) 0)
        urakoitsijan-ylitysosuus (or (arvopaatoksesta tavoitehinnan-ylityspaatos :urakoitsija_maksaa) 0)]
    {:tavoitehinta (cond-> [["Hoitovuoden alun indeksikorjattu tavoitehinta" (euro hoitovuoden-alun-tavoitehinta)]]
                     menneet-pysyvat-muutokset (conj ["Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)" (euro menneet-pysyvat-muutokset)])
                     muutosten-hallinta? (conj ["Tavoitehinnan muutokset" (lisaa-plus pysyvat-muutokset)])
                     (and muutosten-hallinta? kirjallisesti-sovitut-muutokset) (conj ["  • Kirjallisesti sovitut muutokset" (lisaa-plus kirjallisesti-sovitut-muutokset)])
                     muutosten-hallinta? (conj ["  • Toteumiin perustuvat muutokset" (lisaa-plus toteumiin-perustuvat-muutokset)])
                     (and muutosten-hallinta? arvonvahennykset) (conj ["  • Arvonvähennysten tavoitehintamuutokset" (euro arvonvahennykset)])
                     (not muutosten-hallinta?) (conj ["Tavoitehinnan muutokset" (lisaa-plus tavoitehinnan-muutokset)])
                     (and (not muutosten-hallinta?) arvonvahennykset) (conj ["Arvonvähennykset" (euro arvonvahennykset)])
                     true (conj ["Hoitovuoden lopun indeksikorjaus" (euro hoitokauden-lopun-indeksikorjaus)]
                                ["Hoitovuoden lopun tavoitehinta" (euro hoitovuoden-lopun-tavoitehinta)]
                                ["Hoitovuoden lopun kattohinta" (euro hoitovuoden-lopun-kattohinta)]))
     :kustannukset (cond-> [["Hankintakustannukset" (euro hankintakustannukset)]
                            ["Erillishankinnat" (euro erillishankinnat)]
                            ["Johto- ja hallintokorvaus" (euro johto-ja-hallintokorvaus)]
                            ["Hoidonjohtopalkkio" (euro hoidonjohtopalkkio)]]
                      arvonvahennykset (conj ["Arvonvähennykset" (euro arvonvahennykset)])
                      (pos? muut-kulut) (conj ["Muut kulut" (euro muut-kulut)])
                      true (conj ["Toteutuma yhteensä" (euro toteuma-yht)])
                      (or (:id tavoitehinnan-ylityspaatos)
                          (and (not tavoitehinnan-ylityspaatos) (some? tavoitehinnan-ylitys) (not= 0 tavoitehinnan-ylitys)))
                      (conj ["Tavoitehinnan ylitys" (euro tavoitehinnan-ylitys)])
                      (or tavoitehinnan-alituspaatos (not= 0 tavoitehinnan-alitus))
                      (conj ["Tavoitehinnan alitus" (euro tavoitehinnan-alitus)])
                      (pos? kattohinnan-ylitys)
                      (conj ["Kattohinnan ylitys" (euro kattohinnan-ylitys)]))
     :urakoitsijan-saatavat (cond-> [["Lupausbonus" (euro lupausbonus)]
                                     ["Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta" (euro asiakastyytyvaisyysbonus)]
                                     ["Muut bonukset" (euro muut-bonukset)]
                                     ["Tavoitepalkkio" (euro tavoitepalkkio)]]
                               tavoitehinnan-ylityspaatos (conj ["Tavoitehinnan ylitys" (euro (if (> urakoitsijan-ylitysosuus 0) urakoitsijan-ylitysosuus 0))])
                               true (conj ["Hoidonjohtopalkkion muutos" (euro (max 0 hoidonjohtopalkkion-muutos))]))
     :tilaajan-saatavat (cond-> [["Lupaussanktio" (euro lupaussanktio)]
                                 ["Muut sanktiot" (euro muut-sanktiot)]]
                           tavoitehinnan-ylityspaatos (conj ["Tavoitehinnan ylitys" (euro (if (> tavoitehinnan-ylitys 0) tilaajan-ylitysosuus 0))])
                           (and kattohinnan-ylityspaatos (> urakoitsijan-hyvitysosuus 0)) (conj ["Kattohinnan ylitys" (euro urakoitsijan-hyvitysosuus)])
                           true (conj ["Hoidonjohtopalkkion muutos" (euro (max 0 (- hoidonjohtopalkkion-muutos)))]))
     :siirrot [["Siirto seuraavan vuoden hankintakustannuksiin" (euro siirto)]]}))

(defn valikatselmus-pdf
  [db hae-tiedot kayttaja {:keys [urakka-id hoitovuosi]}]
  (let [data (hae-tiedot db kayttaja {:urakkaid urakka-id :hoitovuosi hoitovuosi})
        rivit (yhteenveto-rivit data)
        _ (println "Valikatselmus PDF data:" (pr-str data))
        _ (println "Valikatselmus PDF rivit:" (pr-str rivit))
        ]
    (with-meta
      (xsl-fo/dokumentti
        {:margin {:left "5mm" :right "5mm" :top "5mm" :bottom "5mm" :body "0mm"}}
        [:fo:wrapper {:font-size 8}
         [:fo:block {:font-size "14pt" :font-weight "bold" :margin-bottom "3mm"} "Välikatselmus"]
         (osio "Tavoitehinta" (:tavoitehinta rivit))
         (osio "Tavoitehintaan kuuluvat toteutuneet kustannukset" (:kustannukset rivit))
         (osio "Urakoitsijan saatavat" (:urakoitsijan-saatavat rivit))
         (osio "Tilaajan saatavat" (:tilaajan-saatavat rivit))
         (osio "Siirrot" (:siirrot rivit))])
      {:tiedostonimi (str "Valikatselmus_" urakka-id "_" hoitovuosi ".pdf")})))

