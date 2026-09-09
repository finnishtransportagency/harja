(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf
  (:require [harja.fmt :as fmt]
            [harja.tyokalut.xsl-fo :as xsl-fo]))

(def ^:private reunat {:border-bottom "solid 0.1mm black"
                       :border-top "solid 0.1mm black"
                       :border-left "solid 0.1mm black"
                       :border-right "solid 0.1mm black"})

(defn- rivi
  "Tätä kutsutaan mapissä ja se muodostaa kaikista pdf:lle tulevista riveistä samanlaisen rivin. Ensin on teksti ja sitä seuraava summa."
  [[otsikko arvo]]
  [:fo:table-row
   [:fo:table-cell
    [:fo:block {:padding-top "1.425mm"
                :padding-right "1.5mm"
                :padding-bottom "1.425mm"
                :padding-left "1.5mm"} otsikko]]
   [:fo:table-cell
    [:fo:block {:padding-top "1.425mm"
                :padding-right "1.5mm"
                :padding-bottom "1.425mm"
                :padding-left "1.5mm"
                :text-align "right"} (str arvo)]]])

(defn- osio [otsikko rivit & lisasisalto]
  (into
    [:fo:block (merge reunat {:margin-top "2.75mm"
                              :padding-top "2.85mm"
                              :padding-right "3mm"
                              :padding-bottom "2.85mm"
                              :padding-left "3mm"})
     [:fo:block {:font-size "11pt" :font-weight "bold" :margin-bottom "1.5mm"} otsikko]
     [:fo:table {:table-layout "fixed"}
      [:fo:table-column {:column-width "70%"}]
      [:fo:table-column {:column-width "30%"}]
      [:fo:table-body (map rivi rivit)]]]
    (remove nil? lisasisalto)))

(defn- varillinen-osio
  "Tavoitehinnan ylitykset/alitukset ja kattohinnan ylitys näytetään värillisessä laatikossa."
  [vari rivit]
  (let [borderin-vari (case vari
                        :vihrea "#1C891C"
                        :punainen "#B40A14")]
    [:fo:block {:margin-top "4.75mm"
                :padding-top "1.9mm"
                :padding-right "2mm"
                :margin-left "2mm"
                :margin-right "2mm"
                :padding-bottom "1.9mm"
                :padding-left "2mm"
                :border-top (str "solid 0.5mm " borderin-vari)
                :border-right (str "solid 0.5mm " borderin-vari)
                :border-bottom (str "solid 0.5mm " borderin-vari)
                :border-left (str "solid 0.5mm " borderin-vari)}
     ;; otsikko on ensimmäisellä rivillä
     [:fo:table {:table-layout "fixed" :font-size "11pt" :font-weight "bold" :color borderin-vari :margin-bottom "1.9mm"}
      [:fo:table-column {:column-width "70%"}]
      [:fo:table-column {:column-width "30%"}]
      [:fo:table-body (rivi (first rivit))]]
     [:fo:table {:table-layout "fixed"}
      [:fo:table-column {:column-width "70%"}]
      [:fo:table-column {:column-width "30%"}]
      [:fo:table-body (map rivi (rest rivit))]]]))

(defn- euro [arvo]
  (fmt/euro-opt false arvo))

(defn- lisaa-plus [arvo]
  (str (when (and (number? arvo) (pos? arvo)) "+") (euro arvo)))

(defn- arvopaatoksesta [paatos avain]
  (when (:id paatos)
    (get paatos avain)))

(defn- ota-paatos [paatokset avain]
  (some #(get % avain) paatokset))

(defn yhteenveto-rivit [data]
  (let [paatokset (:paatokset data)
        urakan-parametrit (:urakan-parametrit data)
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi data)
        urakan-loppuvuosi (:urakan-loppuvuosi data)
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
        seuraavan-vuoden-hankintakustannusten-alennus (or (arvopaatoksesta tavoitehinnan-alituspaatos :siirron_maara) 0)
        kattohinnan-ylityspaatos (ota-paatos paatokset :kattohinnan-ylitys)
        kattohinnan-ylitys (or (:ylityksen_maara kattohinnan-ylityspaatos) 0)
        siirto (- (or (arvopaatoksesta kattohinnan-ylityspaatos :siirrettava_maara) 0)
                 seuraavan-vuoden-hankintakustannusten-alennus)
        nayta-arvonvahennykset? (or (and arvonvahennykset (not muutosten-hallinta?))
                                  (>= hoitokauden-alkuvuosi 2026))
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
                     menneet-pysyvat-muutokset (conj ["Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)" (euro menneet-pysyvat-muutokset)])
                     muutosten-hallinta? (conj ["Tavoitehinnan muutokset" (lisaa-plus pysyvat-muutokset)])
                     (and muutosten-hallinta? kirjallisesti-sovitut-muutokset) (conj ["  • Kirjallisesti sovitut muutokset" (lisaa-plus kirjallisesti-sovitut-muutokset)])
                     muutosten-hallinta? (conj ["  • Toteumiin perustuvat muutokset" (lisaa-plus toteumiin-perustuvat-muutokset)])
                     (and muutosten-hallinta? (>= hoitokauden-alkuvuosi 2026)) (conj ["  • Arvonvähennysten tavoitehintamuutokset" (euro arvonvahennykset)])
                     (not muutosten-hallinta?) (conj ["Tavoitehinnan muutokset" (lisaa-plus tavoitehinnan-muutokset)])
                     (and (not muutosten-hallinta?) arvonvahennykset) (conj ["Arvonvähennysten tavoitehintamuutokset" (euro arvonvahennykset)])
                     true (conj ["Hoitovuoden lopun indeksikorjaus" (euro hoitokauden-lopun-indeksikorjaus)]
                            ["Hoitovuoden lopun tavoitehinta" (euro hoitovuoden-lopun-tavoitehinta)]
                            ["Hoitovuoden lopun kattohinta" (euro hoitovuoden-lopun-kattohinta)]))
     :kustannukset (cond-> [["Hankintakustannukset" (euro hankintakustannukset)]
                            ["Erillishankinnat" (euro erillishankinnat)]
                            ["Johto- ja hallintokorvaus" (euro johto-ja-hallintokorvaus)]
                            ["Hoidonjohtopalkkio" (euro hoidonjohtopalkkio)]]
                     arvonvahennykset (conj ["Arvonvähennykset" (euro arvonvahennykset)])
                     (pos? muut-kulut) (conj ["Muut kulut" (euro muut-kulut)])
                     true (conj ["Toteutuma yhteensä" (euro toteuma-yht)]))
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

(defn valikatselmus-pdf
  [db hae-tiedot kayttaja {:keys [urakka-id hoitovuosi]}]
  (let [data (hae-tiedot db kayttaja {:urakkaid urakka-id :hoitovuosi hoitovuosi})
        rivit (yhteenveto-rivit data)]
    (with-meta
      (xsl-fo/dokumentti
        {:margin {:left "10mm" :right "10mm" :top "0mm" :bottom "10mm" :body "10mm"}}
        [:fo:wrapper {:font-size 8}
         [:fo:block {:font-size "14pt" :font-weight "bold" :margin-bottom "3mm"} "Välikatselmus"]
         (osio "Hoitovuoden lopun tavoite- ja kattohinta" (:tavoitehinta rivit))
         (osio "Tavoitehintaan kuuluvat toteutuneet kustannukset"
           (:kustannukset rivit)
           (when (:tavoitehinnan-ylitys rivit)
             (varillinen-osio :punainen (:tavoitehinnan-ylitys rivit)))
           (when (:tavoitehinnan-alitus rivit)
             (varillinen-osio :vihrea (:tavoitehinnan-alitus rivit)))
           (when (:kattohinnan-ylitys rivit)
             (varillinen-osio :punainen (:kattohinnan-ylitys rivit))))
         (osio "Bonukset" (:bonukset rivit))
         (osio "Sanktiot" (:sanktiot rivit))
         (osio "Hoidonjohtopalkkion muutos" (:hoidonjohtopalkkio rivit))])
      {:tiedostonimi (str (:nimi urakan-tiedot) " Valikatselmus " hoitovuosi " - " (inc hoitovuosi) ".pdf")})))
