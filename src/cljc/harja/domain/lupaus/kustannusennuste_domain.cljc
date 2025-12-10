(ns harja.domain.lupaus.kustannusennuste-domain
  "Kustannusennuste-lupauksen domain-logiikka.
   
   Sisältää kustannusennusteiden laskentaan, validointiin ja määräpäivien
   käsittelyyn liittyvän puhtaan funktionaalisen logiikan."
  (:require [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defn kustannusennuste?
  "Tarkistaa onko lupaus kustannusennuste-tyyppinen."
  [lupaus]
  (= "kustannusennuste" (:lupaustyyppi lupaus)))

(defn kustannusennuste->ennuste
  "Laskee kustannusennusteen ennusteen kuukausittaisten kustannusennusteiden perusteella.
   Jos kaikki kuukaudet eivät ole täytetty, palauttaa viimeisimmän annetun ennusteen.
   Jos dataa ei löydy, palauttaa 0."
  [{:keys [lupaus-kuukaudet]}]
  (let [;; Hae kaikki kuukaudet, joissa on kustannusennusteita
        kuukaudet-pisteilla (->> lupaus-kuukaudet
                              (filter #(get-in % [:kustannusennuste :pisteet]))
                              (sort-by :kuukausi))
        ;; Jos on annettu kustannusennusteita, käytä viimeisintä
        viimeisin-pisteet (when (seq kuukaudet-pisteilla)
                            (get-in (last kuukaudet-pisteilla) [:kustannusennuste :pisteet]))]
    ;; Palauta 0 jos ei ole dataa, muuten viimeisin pistemäärä
    (or viimeisin-pisteet 0)))

(defn kustannusennuste->toteuma
  "Palauttaa kustannusennusteen toteuman, jos se on laskettu.
   Toteuma haetaan suoraan tietokannasta tallennetusta keskiarvosta.
   Keskiarvo lasketaan välikatselmuksessa kaikista kustannusennustekuukausista.
   
   Parametrit:
   - lopputilanne: Map joka sisältää :kustannusennuste_keskiarvo_pisteet kentän
   
   Palauttaa:
   - Pyöristetyn keskiarvon tai nil jos lopputilannetta ei ole."
  [{:keys [lopputilanne]}]
  (when lopputilanne
    (when-let [keskiarvo (:kustannusennuste_keskiarvo_pisteet lopputilanne)]
      #?(:clj (Math/round (double keskiarvo))
         :cljs (js/Math.round keskiarvo)))))

(defn validoi-kustannusennuste-syotteet
  "Validoi että kaikki syötteet ovat valideja laskentaa varten"
  [{:keys [ennustettu-tavoitehinta toteutunut-tavoitehinta 
           ennustettu-kustannus toteutunut-kustannus
           hoitovuoden-alun-tavoitehinta] :as syotteet}]
  {:pre [syotteet]}
  (cond
    (not (every? number? [ennustettu-tavoitehinta toteutunut-tavoitehinta 
                          ennustettu-kustannus toteutunut-kustannus
                          hoitovuoden-alun-tavoitehinta]))
    {:virhe "Kaikki arvot on oltava numeroita"}

    (zero? hoitovuoden-alun-tavoitehinta)
    {:virhe "Hoitovuoden tavoitehinta ei voi olla nolla (nollajako)"}

    (neg? hoitovuoden-alun-tavoitehinta)
    {:virhe "Hoitovuoden tavoitehinta ei voi olla negatiivinen"}

    :else
    {:ok true}))

(defn turvallinen-jako
  "Turvallinen jakolasku joka välttää BigDecimal-ongelmat ja toimii sekä CLJ että CLJS:ssä"
  [a b]
  (if (zero? b)
    0.0
    #?(:clj (double (/ (double a) (double b)))
       :cljs (/ a b))))

(defn laske-pisteytyshoitovuosi
  "Laskee hoitovuoden, johon kustannusennuste-kuukausi pisteytetään.
  
  Parametrit:
  - vuosi: Kalenterivuosi jolloin kustannus kirjataan (esim. 2024)
  - kuukausi: Kuukausi 1-12 (esim. 8 = elokuu)
  - offset: Siirtymä normaalista pisteytyksestä (0 = normaali, 1 = +1 vuosi)
  
  Logiikka:
  - Jos offset = 0: Kuukausi pisteytetään normaalisti sen omassa hoitokaudessa
    -> Käytetään pvm/hoitokauden-alkuvuosi funktiota
  - Jos offset > 0: Kuukausi pisteytetään N vuotta myöhemmin
    -> Käytetään kalenterivuotta + offset
  
  Esimerkit:
  - Elokuu 2024, offset=1 => 2025 (pisteytetään HK 2025-2026:ssa)
  - Tammikuu 2025, offset=0 => 2024 (pisteytetään HK 2024-2025:ssa)
  - Lokakuu 2024, offset=1 => 2025 (pisteytetään HK 2025-2026:ssa)"
  [vuosi kuukausi offset]
  {:pre [(int? vuosi)
         (int? kuukausi)
         (int? offset)
         (<= 1 kuukausi 12)
         (>= offset 0)]
   :post [(int? %)]}
  (if (pos? offset)
    (+ vuosi offset)
    (pvm/hoitokauden-alkuvuosi vuosi kuukausi)))

(defn laske-kustannusennusteen-tarkkuus
  "Laskee kustannusennusteen tarkkuuden ja palauttaa tietokantaan tallennettavan muodon."
  [{:keys [ennustettu-tavoitehinta toteutunut-tavoitehinta
           ennustettu-kustannus toteutunut-kustannus
           hoitovuoden-alun-tavoitehinta] :as syotteet}]
  (let [validointi (validoi-kustannusennuste-syotteet syotteet)]
    (if (:virhe validointi)
      validointi
      (let [te (double ennustettu-tavoitehinta)
            tt (double toteutunut-tavoitehinta)
            ke (double ennustettu-kustannus)
            kt (double toteutunut-kustannus)
            th (double hoitovuoden-alun-tavoitehinta)

            ;; Laskentavaiheet
            tavoitehinta-ero (turvallinen-jako (- te tt) th)
            kustannus-ero (turvallinen-jako (- ke kt) th)
            riski-ero (turvallinen-jako (- (- ke te) (- kt tt)) th)

            ;; Lopputulos
            tarkkuus (+ (* tavoitehinta-ero 0.05)
                        (* kustannus-ero 0.05)
                        (* riski-ero 0.9))
            ;; Kerrotaan 100:lla (desimaalista prosentiksi) ja pyöristetään yhteen desimaaliin
            tarkkuus-prosentti (fmt/pyorista-desimaaliin (* tarkkuus 100.0) 1)

            ;; Tallennettava data
            kaava-versio "v1.0"
            kaava-teksti "x = [(Te - Tt)/Th × 0.05 + (Ke - Kt)/Th × 0.05 + [(Ke - Te) - (Kt - Tt)]/Th × 0.9]"

            parametrit {:Te te :Tt tt :Ke ke :Kt kt :Th th
                        :kertoimet {:tavoitehinta-kerroin 0.05
                                    :kustannus-kerroin 0.05
                                    :riski-kerroin 0.9}}

            vaiheet {:vaihe-1 {:kuvaus "Tavoitehinnan ero"
                               :kaava "(Te - Tt) / Th"
                               :laskenta (str "(" te " - " tt ") / " th)
                               :tulos tavoitehinta-ero}
                     :vaihe-2 {:kuvaus "Kustannuksen ero"
                               :kaava "(Ke - Kt) / Th"
                               :laskenta (str "(" ke " - " kt ") / " th)
                               :tulos kustannus-ero}
                     :vaihe-3 {:kuvaus "Riskin ero"
                               :kaava "[(Ke - Te) - (Kt - Tt)] / Th"
                               :laskenta (str "[(" ke " - " te ") - (" kt " - " tt ")] / " th)
                               :tulos riski-ero}
                     :vaihe-4 {:kuvaus "Lopputulos"
                               :kaava "vaihe-1 × 0.05 + vaihe-2 × 0.05 + vaihe-3 × 0.9"
                               :laskenta (str tavoitehinta-ero " × 0.05 + " kustannus-ero " × 0.05 + " riski-ero " × 0.9")
                               :tulos tarkkuus}
                     :lopputulos-prosentti tarkkuus-prosentti}]

        {:tarkkuus-prosentti tarkkuus-prosentti
         :laskentakaava-versio kaava-versio
         :laskentakaava-teksti kaava-teksti
         :laskentakaava-parametrit parametrit
         :laskentakaava-vaiheet vaiheet}))))

(defn kustannusennuste-maarapaiva-paattely
  "Palauttaa määräpäivän päättelyyn liittyvät boolean-arvot.
   
   Parametrit:
   - nykyhetki: DateTime - vertailtava nykyinen aika
   - maarapaiva-pvm: DateTime - määräpäivä
   - tiedot-syotetty-ajoissa?: boolean - onko tiedot syötetty ennen määräpäivää
   - disabled?: boolean - ulkoinen disabled-tila (esim. oikeudet)"
  [nykyhetki maarapaiva-pvm tiedot-syotetty-ajoissa? disabled?]
  {:pre [(some? nykyhetki)
         (boolean? disabled?)]}
  (let [maarapaiva-mennyt-ohi? (and maarapaiva-pvm
                                    (not (pvm/sama-tai-ennen? nykyhetki maarapaiva-pvm)))
        
        ;; Tarkista ovatko kuukaudet ja vuodet eri
        maarapaivan-kuukausi (when maarapaiva-pvm (pvm/kuukausi maarapaiva-pvm))
        maarapaivan-vuosi (when maarapaiva-pvm (pvm/vuosi maarapaiva-pvm))
        nykyinen-kuukausi (pvm/kuukausi nykyhetki)
        nykyinen-vuosi (pvm/vuosi nykyhetki)
        
        ei-maarapaivan-kuukausi? (and maarapaivan-kuukausi maarapaivan-vuosi
                                      (or (not= nykyinen-kuukausi maarapaivan-kuukausi)
                                          (not= nykyinen-vuosi maarapaivan-vuosi)))
        
        ;; Read-only näkymä vain, jos määräpäivä ohitettu JA tiedot syötetty ajoissa
        kayta-readonly-nakymaa? (and maarapaiva-mennyt-ohi? tiedot-syotetty-ajoissa?)
        
        ;; Yhdistetty disabled-tila - estetään jos väärä kuukausi TAI määräpäivä ohitettu
        disabled? (or disabled? ei-maarapaivan-kuukausi? maarapaiva-mennyt-ohi?)]
    
    {:maarapaiva-mennyt-ohi? maarapaiva-mennyt-ohi?
     :ei-maarapaivan-kuukausi? ei-maarapaivan-kuukausi?
     :kayta-readonly-nakymaa? kayta-readonly-nakymaa?
     :disabled? disabled?}))
