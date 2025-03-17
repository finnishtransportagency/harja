(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone
  (:require [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]))

(def paatostyypit
  [{:nimi "Lupaukset" :tyyppi "bonus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1}
   {:nimi "Lupaukset" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1}
   {:nimi "Lupaukset" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 2}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2021 :hoitotyyppi #{"MHU"} :jarjestys 2}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 2}
   {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "A" :urakan_alkuvuosi 2020 :urakan_loppuvuosi 2028 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 4}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :urakan_loppuvuosi 2029 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 4}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 4}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "C" :urakan_alkuvuosi 2025 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 4}
   {:nimi "Tavoitehinnan alitus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 5}
   {:nimi "Tavoitehinnan alitus" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 5}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "A" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 6}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6}
   {:nimi "Kattohinnan ylitys" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 7}
   {:nimi "Kattohinnan ylitys" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 7}
   {:nimi "Hoidonjohtopalkkion muutos" :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 8}
   {:nimi "Hoidonjohtopalkkion muutos" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 9}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9}])

(defn distinct-by [vektori avain]
  (:result (reduce
             (fn [{:keys [seen result]} m]
               (let [arvo (get-in m [avain])]
                 (if (contains? seen arvo)
                   {:seen seen :result result}
                   {:seen (conj seen arvo) :result (conj result m)})))
             {:seen #{}, :result []}
             vektori)))

(defn urakan-hoitotyyppi
  "Erittäin vaativat hoitourakat merkitään päätöstauluun hoitotyyppinä MHU+"
  [erittain_vaativa_hoitourakka]
  (if erittain_vaativa_hoitourakka "MHU+" "MHU"))

(defn mahdolliset-paatokset-tyypilla [mhu-tyyppi paatokset]
  (filter #(contains? (:hoitotyyppi %) mhu-tyyppi) paatokset))

(defn mahdolliset-paatokset-urakan-alkuvuodella [urakan-alkuvuosi paatokset]
  (filter #(<= (:urakan_alkuvuosi %) urakan-alkuvuosi) paatokset))

(defn mahdolliset-paatokset-urakan-loppuvuodella [urakan-loppuvuosi paatokset]
  (filter #(or (nil? (:urakan_loppuvuosi %))
            (and (:urakan_loppuvuosi %) (>= (:urakan_loppuvuosi %) urakan-loppuvuosi))) paatokset))

(defn mahdolliset-paatokset-nakyvyys-vuodella [kuluva-vuosi paatokset]
  (filter #(<= (:nakyvyys_alkaen %) kuluva-vuosi) paatokset))

(defn vain-yksi-paatos-per-tyyppi [paatokset]
  (let [uniikit-tyypit  (map (fn [paatos]
                              (assoc paatos :uniikki-tyyppi (str (:tyyppi paatos) (:nimi paatos)))) paatokset)
        uniikit (distinct-by uniikit-tyypit :uniikki-tyyppi)
        paatokset (map (fn [paatos]
                        (dissoc paatos :uniikki-tyyppi)) uniikit)]
    paatokset))

(defn kaikki-mahdolliset-paatokset [mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi]
  (let [paatokset paatostyypit
        mahdollset-tyypilla (mahdolliset-paatokset-tyypilla mhu-tyyppi paatokset)
        mahdolliset-aloitusvuodella (mahdolliset-paatokset-urakan-alkuvuodella urakan-alkuvuosi mahdollset-tyypilla)
        mahdolliset-lopetusvuodella (mahdolliset-paatokset-urakan-loppuvuodella urakan-loppuvuosi mahdolliset-aloitusvuodella)
        mahdolliset-kuluvalle-vuodelle (mahdolliset-paatokset-nakyvyys-vuodella kuluva-hoitovuosi mahdolliset-lopetusvuodella)
        paatokset (vain-yksi-paatos-per-tyyppi mahdolliset-kuluvalle-vuodelle)]
    paatokset))

(defn yhdista-mapit
  "Yhdistetään tietokannasta tulevat ja päätöskoneelta tulevat päätökset niin, että
   käytetään päätöskoneen päätöksiä, jos niitä ei ole vielä tietokannassa ja muuten tietokannan päätöksiä.
   Vertailussa käytetään :nimi avainta. Se täytyy löytyä molemmista mapeistä."
  [pk-paatokset db-paatokset]
  (let [index-map (into {} (map (fn [m] [(:nimi m) m]) db-paatokset))]
    (map (fn [m1]
           (if-let [m2 (index-map (:nimi m1))]
             m2
             m1))
      pk-paatokset)))

(defn lisaa-paatos-virheellisena
  "Jos päätös on mukana päätöslistassa, mutta sille ei ole antaa tarkentavia tietoja, niin lisätään siihen virhe.
  Mikäli päätöstä ei löydy listasta, niin älä lisää mitään."
  [paatokset nimi virhe lisataan? jarjestys]
  (keep identity
    (sort-by :jarjestys
      (if (some #(= (:nimi %) nimi) paatokset)
        (conj
          (filter #(not= (:nimi %) nimi) paatokset)
          ;; Jos ehdot eivät täyttyneet, niin päätöstä ei voida lisätä edes virheellisenä
          (when lisataan?
            {:nimi nimi :virhe virhe :jarjestys jarjestys}))
        paatokset))))

(defn laske-indeksikorotus-lupaukselle [db urakkaid paatos-pvm indeksi summa sanktio?]
  (let [indeksikorotus-parametrit {:pvm paatos-pvm
                                   :indeksi indeksi
                                   :maara summa
                                   :urakka-id urakkaid
                                   :sanktiolaji (if sanktio? "lupaussanktio" nil)}
        ;; Taustalla ajetaan tämmönen: SELECT korotus FROM sanktion_indeksikorotus(:pvm::DATE, :indeksi,:maara::NUMERIC, :urakka-id::INTEGER, :sanktiolaji::sanktiolaji);
        indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))]
    indeksikorotus))

(defn valmistele-lupauspaatokset [db urakkaid paatokset toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjouksen-tavoitehinta indeksi]
  ;; Ota mukaan oikea lupauspäätös, jos ehdot täyttyvät
  (if (and toteutuneet-pisteet luvatut-pisteet tarjouksen-tavoitehinta tavoitehinta)
    (let [erotus (- luvatut-pisteet toteutuneet-pisteet)
          tyyppi (cond
                   (= erotus 0) "taytetty"
                   (< erotus 0) "bonus"
                   (> erotus 0) "sanktio")
          ;; Urakan parametreista lupaussanktion prosentit
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit) ;; Jaetaan sadalla, niin saadaan helpompi laskutoimitus
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          lupaussanktio (when (= tyyppi "sanktio") (* (/ sanktioprosentti 100) tarjouksen-tavoitehinta erotus))
          lupausbonus (when (= tyyppi "bonus") (* (/ bonusprosentti 100) tarjouksen-tavoitehinta (* -1 erotus)))
          ;; Valitaan lupauspäätös, joissa tyyppi täsmää
          lupauspaatos (first (filter
                                (fn [paatos]
                                  (and (= (:nimi paatos) "Lupaukset") (= (:tyyppi paatos) tyyppi)))
                                paatokset))
          indeksikorotus (cond
                           (and (= tyyppi "bonus") (:indeksi_kaytossa_bonuksella urakan-parametrit))
                           (laske-indeksikorotus-lupaukselle db urakkaid (pvm/nyt) indeksi lupausbonus false)

                           (and (= tyyppi "sanktio") (:indeksi_kaytossa_sanktiolla urakan-parametrit))
                           (laske-indeksikorotus-lupaukselle db urakkaid (pvm/nyt) indeksi lupaussanktio false)

                           :else nil)

          ;; Korvataan koneelta saatu päätös tässä valistellulta
          lupauspaatos (-> lupauspaatos
                         (assoc :tyyppi tyyppi)
                         (assoc :lupaussanktio lupaussanktio)
                         (assoc :lupausbonus lupausbonus)
                         (assoc :tavoitehinta tavoitehinta)
                         (assoc :tarjous_tavoitehinta tarjouksen-tavoitehinta)
                         (assoc :luvatut_pisteet luvatut-pisteet)
                         (assoc :toteutuneet_pisteet toteutuneet-pisteet)
                         (assoc :sanktioprosentti sanktioprosentti)
                         (assoc :bonusprosentti bonusprosentti)
                         (assoc :indeksi indeksi)
                         (assoc :indeksikorotus indeksikorotus))
          ;; Poista kaikki lupauspäätökset listasta
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Lupaukset")) paatokset)
          ;; Ja lisää muokattu takaisin
          paatokset (sort-by :jarjestys (conj paatokset lupauspaatos))]
      paatokset)
    ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
    (lisaa-paatos-virheellisena paatokset "Lupaukset"
      "Toteutuneita pisteitä, luvattuja pisteitä tai tarjouksen tavoitehintaa ei ole määritelty."
      true 1)))

(defn valimistele-tavoitehinnan-muutospaatos [paatokset urakan-alkuvuosi tavoitehinta kattohinta muokkaa-kattohinta? kuluva-hoitovuosi]
  ;; Ota mukaan oikea tavoitehinnan muutospäätös, jos ehdot täyttyvät
  (if (and kattohinta tavoitehinta)
    (let [;; Korvataan koneelta saatu päätös tässä valistellulta
          tavoitehinnan-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
          tavoitehinnan-muutospaatos (-> tavoitehinnan-muutospaatos
                                       (assoc :tavoitehinta tavoitehinta)
                                       (assoc :kattohinta kattohinta)
                                       (assoc :muokkaa_kattohinta muokkaa-kattohinta?))
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan muutokset")) paatokset)
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-muutospaatos))]
      paatokset)
    ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
    (lisaa-paatos-virheellisena paatokset "Tavoitehinnan muutokset"
      "Tavoitehintaa tai kattohintaa ei ole määritelty."
      true 2)))

(defn valmistele-indeksikorjauspaatos [paatokset tavoitehinta tavoitehinnan-muutokset
                                       hoitokauden-indeksikuukaudet alkuperainen-pisteluku hoitokauden-alkuvuosi]
  ;; Päätöksistä täytyy löytyä
  (if (first (filter #(when (= (:nimi %) "Hoitovuoden lopun indeksikorjaus") %) paatokset))
    ;; Ota mukaan oikea indeksikorjauspäätös, jos ehdot täyttyvät
    (if (and tavoitehinta tavoitehinnan-muutokset hoitokauden-indeksikuukaudet)
      (let [;; Laske pistelukujen muutos
            pisteet (apply + (map #(:indeksiluku %) hoitokauden-indeksikuukaudet))
            piste-keskiarvo (with-precision 4 (/ pisteet (count hoitokauden-indeksikuukaudet)))
            pistelukujen-muutos (round2 1 (- piste-keskiarvo alkuperainen-pisteluku))
            alkuperaisen-pisteluvun-kuukausi (str "elo-/syyskuu " (- hoitokauden-alkuvuosi 1))
            muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- piste-keskiarvo alkuperainen-pisteluku) piste-keskiarvo) 100)))
            ;; Prosenttiosuus otetaan laskentaan mukaan vain 2% ylittävältä osalta
            indeksikorotuksen-prosenttiosuus (if (> muutos-prosentteina 2) (- muutos-prosentteina 2) 0)
            muutosten-summa (if (seq tavoitehinnan-muutokset)
                              (apply + (map #(:summa %) tavoitehinnan-muutokset))
                              0)
            tavoitehinta-ennen (- tavoitehinta muutosten-summa)
            hoitokauden-lopun-indeksikorjaus (* tavoitehinta-ennen (/ indeksikorotuksen-prosenttiosuus 100))
            tavoitehinnan-muutos (apply + (map :summa tavoitehinnan-muutokset))
            ;; Korvataan koneelta saatu päätös tässä valistellulta
            indeksipaatos (first (filter #(when (= (:nimi %) "Hoitovuoden lopun indeksikorjaus") %) paatokset))
            indeksipaatos (-> indeksipaatos
                            (assoc :tavoitehinta tavoitehinta)
                            (assoc :tavoitehinnan_muutokset tavoitehinnan-muutos)
                            (assoc :tavoitehinta_ennen tavoitehinta-ennen)
                            (assoc :hoitokauden_kuukaudet hoitokauden-indeksikuukaudet)
                            (assoc :kuukausien_keskiarvo piste-keskiarvo)
                            (assoc :alkuperainen_pisteluku alkuperainen-pisteluku)
                            (assoc :alkuperaisen_pisteluvun_kuukausi alkuperaisen-pisteluvun-kuukausi)
                            (assoc :pistelukujen_muutos pistelukujen-muutos)
                            (assoc :indeksikorotuksen_prosenttiosuus indeksikorotuksen-prosenttiosuus)
                            (assoc :hoitokauden_lopun_indeksikorjaus hoitokauden-lopun-indeksikorjaus))

            paatokset (remove (fn [paatos] (= (:nimi paatos) "Hoitovuoden lopun indeksikorjaus")) paatokset)
            paatokset (sort-by :jarjestys (conj paatokset indeksipaatos))]
        paatokset)
      ;; Ehdot eivät täyttyneet, otetaan indeksipäätökset pois listasta ja lisätään virheilmoitus päätökselle
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus"
        "Tavoitehintaa, tavoitehinnan muutoksia tai hoitokauden indeksikuukausia ei ole määritelty."
        true 3))
    paatokset))

(defn valimistele-tavoitehinnan-alituspaatos [db urakkaid paatokset urakan-loppuvuosi kuluva-hoitovuosi
                                              hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta kustannukset]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and hoitokauden-alun-tavoitehinta kustannukset (> hoitokauden-alun-tavoitehinta kustannukset))
    (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          tavoitehinnan-alitus (- hoitokauden-alun-tavoitehinta kustannukset)
          ;; Poistetaan päätöskokneen tavoitehinna alituspäätös ja muokataan se alla
          tavoitehinnan-alituspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan alitus") %) paatokset))
          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Tavoitehinnan alitus"))
                      paatokset)

          ;; Jäljelle jäänyt paatos

          ;; (:tavoitepalkkion_maksimi urakan-parametrit) on maksimiprosentti, jota tavoitepalkkiota voidaan maksaa suhteessa hoitokauden alun indeksikorjattuun tavoitehintaan. Yleisimmin 3%
          maksimi-tavoitepalkkio (* (/ (:tavoitepalkkion_maksimi urakan-parametrit) 100) hoitokauden-alun-tavoitehinta)
          tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
          ;; Versiossa 1 - Tavoitepalkkio on alituksesta 30%, mutta max 3% tavoitehinnasta - Mutta viimeisenä vuotena maksetaan kaikki eli 100% alituksesta
          laskennallinen-tavoitepalkkio (* (/ tavoitepalkkion-maksuprosentti 100) tavoitehinnan-alitus)
          tavoitepalkkio (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                           tavoitehinnan-alitus ;; Viimeisenä vuotena maksetaan kaikki. Muuten 30% tai max 3% , tai versiossa 2 maksetaan 75% alituksesta
                           (min maksimi-tavoitepalkkio laskennallinen-tavoitepalkkio))
          ;; Jos alituksesta maksettava tavoitepalkkio on suurempi, kuin 3% tavoitehinnasta, siirretään ylittävä osuus seuraavan hoitovuden alennukseksi - Paitsi tietenkin viimeisenä vuotena
          siirron-maara (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                          nil ;; Viimeisenä vuotena maksetaan kaikki. Eli ei siirretä mitään
                          (when (> laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)
                            (- laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)))
          viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
          tavoitehinnan-alituspaatos (-> tavoitehinnan-alituspaatos
                                       (assoc :hoitokauden_alun_tavoitehinta hoitokauden-alun-tavoitehinta)
                                       (assoc :hoitokauden_lopun_tavoitehinta hoitokauden-lopun-tavoitehinta)
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :alituksen_maara tavoitehinnan-alitus)
                                       (assoc :siirron_maara siirron-maara)
                                       (assoc :tavoitepalkkio tavoitepalkkio)
                                       (assoc :tavoitepalkkion_maksuprosentti tavoitepalkkion-maksuprosentti)
                                       (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?))
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-alituspaatos))]

      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan pöötöstyyppi
    (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus"
      "Hoitokauden alun indeksikorjattua tavoitehintaa tai toteutuneita kustannuksia ei ole määritelty."
      false 5)))

(defn valmistele-tavoitehinnan-ylityspaatos [db urakkaid paatokset urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi tavoitehinta kattohinta kustannukset mhu-tyyppi]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and kattohinta tavoitehinta kustannukset (> kustannukset tavoitehinta))
    (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          ;; Ylitys + tavoitehinta ei voi ylittää kattohintaa. Eli maksettavat rahat on aina tavoitehinnan ja
          ;; kattohinnan väliin jääviä summia. Kattohinnan ylittävät summat menee aina urakoitsijan maksettavaksi
          tavoitehinnan-ylitys (min (- kustannukset tavoitehinta) (- kattohinta tavoitehinta))
          tavoitehinnan-ylityspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan ylitys")
                                                       %) paatokset))
          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Tavoitehinnan ylitys"))
                      paatokset)

          ;; Jäljelle jäänyt paatos
          tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
          urakoitsijan-prosentti (- 100 tilaajan-prosentti)
          viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
          tavoitehinnan-ylityspaatos (-> tavoitehinnan-ylityspaatos
                                       (assoc :urakkaid urakkaid)
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :tavoitehinta tavoitehinta)
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :ylityksen_maara tavoitehinnan-ylitys)
                                       (assoc :tilaajan_prosentti tilaajan-prosentti)
                                       (assoc :urakoitsijan_prosentti urakoitsijan-prosentti)
                                       (assoc :tilaaja_maksaa (* (/ tilaajan-prosentti 100) tavoitehinnan-ylitys))
                                       (assoc :urakoitsija_maksaa (* (/ urakoitsijan-prosentti 100) tavoitehinnan-ylitys))
                                       (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?))
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-ylityspaatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan tavoitehinnan ylitys
    (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys"
      "Tavoitehintaa, kattohintaa tai toteutuneita kustannuksia ei ole määritelty."
      false 6)))

(defn valmistele-kattohinnan-paatokset [db urakkaid paatokset kattohinta kustannukset kuluva-hoitovuosi urakan-loppuvuosi]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and kattohinta kustannukset (> kustannukset kattohinta))
    (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          kattohinnan-ylityspaatos (first (filter #(= (:nimi %) "Kattohinnan ylitys") paatokset))
          ylityksen-maara (- kustannukset kattohinta)
          viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
          siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
          max-siirrettava-maara (if siirtorajoitus-prosentti
                                  (* siirtorajoitus-prosentti kattohinta) ;; Jos rajoitus on käytössä, niin siirretään max annetun prosentin verran)
                                  ylityksen-maara)
          ;; Varmisteatan, että maksimi määrä ei koskaan ylitä ylityksen määrää
          max-siirrettava-maara (min max-siirrettava-maara ylityksen-maara)
          ;; Täytetään pakolliset tiedot
          kattohinnan-ylityspaatos (-> kattohinnan-ylityspaatos
                                     (assoc :urakkaid urakkaid)
                                     (assoc :toteutuneet_kustannukset kustannukset)
                                     (assoc :kattohinta kattohinta)
                                     (assoc :ylityksen_maara ylityksen-maara)
                                     (assoc :urakoitsija_maksaa ylityksen-maara)
                                     (assoc :siirra? false) ;; Päätöksen pohjatietoja asetettaessa siirto on aina defaulttina false. Tietokannasta haettaessa tilanne voi olla eri.
                                     (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?)
                                     (assoc :maksimi_siirrettava_maara max-siirrettava-maara)
                                     (assoc :siirtorajoitus_prosentti siirtorajoitus-prosentti)
                                     (assoc :siirrettava_maara 0) ;; Aseta defaulttina nollaksi
                                     )

          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Kattohinnan ylitys"))
                      paatokset)
          paatokset (sort-by :jarjestys (conj paatokset kattohinnan-ylityspaatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin poistetaan kattohinnan ylitys
    (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys"
      "Kattohintaa tai toteutuneita kustannuksia ei ole määritelty."
      false 7)))

(defn valmistele-hoitokauden-lopun-hintapaatos [paatokset tavoitehinta tavoitehinnan-muutokset hoitokauden-lopun-indeksikorjaus
                                                kattohinta kattohintakerroin lisaa-hoitokauden-lopun-indeksikorjaus]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and tavoitehinta tavoitehinnan-muutokset hoitokauden-lopun-indeksikorjaus kattohinta)
    (let [hintapaatos (first (filter #(= (:nimi %) "Hoitovuoden lopun tavoite- ja kattohinta") paatokset))
          hintamuutos (apply + (map :summa tavoitehinnan-muutokset))
          tavoitehinta_ennen (- tavoitehinta hintamuutos hoitokauden-lopun-indeksikorjaus)
          ;; Täytetään pakolliset tiedot
          hintapaatos (-> hintapaatos
                        (assoc :tavoitehinta_ennen tavoitehinta_ennen)
                        (assoc :tavoitehinta_jalkeen tavoitehinta)
                        (assoc :tavoitehinnan_muutokset hintamuutos)
                        (assoc :hoitokauden_lopun_indeksikorjaus hoitokauden-lopun-indeksikorjaus)
                        (assoc :kattohinta kattohinta)
                        (assoc :kattohintakerroin kattohintakerroin)
                        (assoc :lisaa_tavoitehintaan_lopunindeksikorjaus lisaa-hoitokauden-lopun-indeksikorjaus))

          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Hoitovuoden lopun tavoite- ja kattohinta"))
                      paatokset)
          paatokset (sort-by :jarjestys (conj paatokset hintapaatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin varoitetaan siitä käyttäjää
    (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun tavoite- ja kattohinta"
      "Hoitokauden lopun indeksikorjausta ei ole vielä asetettu."
      true 4)))

(defn valmistele-hoidonjohtopalkkionmuutospaatos [paatokset tavoitehinta tarjouksen-tavoitehinta hoidonjohtopalkkio]
  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  (if (and tavoitehinta tarjouksen-tavoitehinta hoidonjohtopalkkio
        ;; Varmistetaan möys, että päätös on olemassa
        (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset)))
    (let [paatos (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset))
          tulos (with-precision 10 (/ tavoitehinta tarjouksen-tavoitehinta))
          hoidonjohtopalkkio-muutos (if (>= tulos 1)
                                      (* hoidonjohtopalkkio tulos)
                                      (* (* hoidonjohtopalkkio tulos) -1)) ;; Käännetään luku negatiiviseksi
          muutosprosentti (* (- tulos 1) 100)
          ;; Täytetään pakolliset tiedot
          paatos (-> paatos
                   (assoc :tavoitehinta tavoitehinta)
                   (assoc :tarjouksen_tavoitehinta tarjouksen-tavoitehinta)
                   (assoc :hoidonjohtopalkkio hoidonjohtopalkkio)
                   (assoc :muutosprosentti muutosprosentti)
                   (assoc :hoidonjohtopalkkio_muutos hoidonjohtopalkkio-muutos))

          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Hoidonjohtopalkkion muutos"))
                      paatokset)
          paatokset (sort-by :jarjestys (conj paatokset paatos))]
      paatokset)
    ;; Jos tarvittavia tietoja ei ole, niin varoitetaan siitä käyttäjää
    (let [virhe #{}
          virhe (if-not tavoitehinta (conj virhe "Tavoitehintaa ei ole määritelty. ") virhe)
          virhe (if-not tarjouksen-tavoitehinta (conj virhe "Tarjouksen tavoitehintaa ei ole määritelty. ") virhe)
          virhe (if-not hoidonjohtopalkkio (conj virhe "Hoidonjohtopalkkiota ei ole määritelty. ") virhe)]
      (lisaa-paatos-virheellisena paatokset "Hoidonjohtopalkkion muutos" (clojure.string/join " " virhe) true 8))))

(defn nimi->avain [nimi]
  (keyword (str/lower-case (-> nimi
                             (str/replace #"ö" "o")
                             (str/replace #"ä" "a")
                             (str/replace #" " "-")
                             (str/replace #"--" "-")))))
