(ns harja.tiedot.urakka
  "Tämä nimiavaruus hallinnoi urakan usealle toiminnolle yhteisiä tietoja."
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [harja.tiedot.urakka.organisaatio :as organisaatio]
            [harja.tiedot.toimenpidekoodit :as toimenpidekoodit]

            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<! reaction-writable]]
            [cljs-time.core :as t]
            [taoensso.truss :as truss :refer-macros [have]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn urakan-oletussopimus [urakka]
  (let [{:keys [sopimukset paasopimus]} urakka]
    (if paasopimus
      [paasopimus (get sopimukset paasopimus)]
      (first sopimukset))))

(defonce valittu-sopimusnumero
  (reaction-writable
    (urakan-oletussopimus @nav/valittu-urakka)))

(defonce urakan-yks-hint-tyot (atom nil))
(defonce urakan-kok-hint-tyot (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(defn valitse-oletussopimus-jos-valittuna-kaikki! []
  (when (nil? (first @valittu-sopimusnumero))
    (reset! valittu-sopimusnumero (urakan-oletussopimus @nav/valittu-urakka))))

(defn valitse-sopimusnumero-kaikki! []
  (reset! valittu-sopimusnumero [nil "Kaikki"]))

(defonce urakan-toimenpideinstanssit
  (reaction<! [urakka-id (:id @nav/valittu-urakka)]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id
                         (oikeudet/voi-lukea? oikeudet/urakat urakka-id @istunto/kayttaja))
                (urakan-toimenpiteet/hae-urakan-toimenpiteet urakka-id))))

(defonce valitun-toimenpideinstanssin-koodi (atom nil))

(defonce valittu-toimenpideinstanssi
  (reaction-writable
    (let [koodi @valitun-toimenpideinstanssin-koodi
          toimenpideinstanssit @urakan-toimenpideinstanssit]
      (or (and koodi (first (filter #(= (:t3_koodi %) koodi) toimenpideinstanssit)))
          (first toimenpideinstanssit)))))

(defonce urakan-tehtavat
         (reaction<! [urakka-id (:id @nav/valittu-urakka)]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and urakka-id
                                (oikeudet/voi-lukea? oikeudet/urakat urakka-id @istunto/kayttaja))
                       (urakan-toimenpiteet/hae-urakan-tehtavat urakka-id))))

(defonce valittu-tehtava (atom
                           (when @urakan-tehtavat
                             (first @urakan-tehtavat))))

(defn- urakan-toimenpideinstanssi-avaimella [avain arvo]
  (first (filter #(= arvo (avain %)) @urakan-toimenpideinstanssit)))

(defn urakan-toimenpideinstanssi-toimenpidekoodille [tpk]
  (have integer? tpk)
  (urakan-toimenpideinstanssi-avaimella :id tpk))

(defn urakan-toimenpideinstanssi-tpille [tpi]
  (have integer? tpi)
  (urakan-toimenpideinstanssi-avaimella :tpi_id tpi))

(defn valitse-toimenpideinstanssi-koodilla!
  "Valitsee urakan toimenpideinstanssin 3. tason SAMPO koodin perusteella."
  [koodi]
  (reset! valitun-toimenpideinstanssin-koodi koodi))

(defn valitse-toimenpideinstanssi! [{koodi :t3_koodi :as tpi}]
  (if-not koodi
    ;; Kooditon erikoisvalinta, kuten "Kaikki" tai "Muut"
    (reset! valittu-toimenpideinstanssi tpi)
    (do (when (and (nil? (:t3_koodi @valittu-toimenpideinstanssi))
                   (= koodi @valitun-toimenpideinstanssin-koodi))
          (valitse-toimenpideinstanssi-koodilla! nil))
        (valitse-toimenpideinstanssi-koodilla! koodi))))


(defn valitse-tehtava! [{koodi :t4_id :as tpk}]
  (reset! valittu-tehtava tpk))

(defn- vesivaylien-sopimuskaudet [ensimmainen-vuosi viimeinen-vuosi]
  (mapv (fn [vuosi]
          [(pvm/vesivaylien-hoitokauden-alkupvm vuosi)
           (pvm/paivan-lopussa (pvm/vesivaylien-hoitokauden-loppupvm (inc vuosi)))])
        (range ensimmainen-vuosi viimeinen-vuosi)))

(defn hoito-tai-sopimuskaudet
  "Palauttaa urakan hoitokaudet, jos kyseessä on hoidon alueurakka. Muille urakoille palauttaa
  urakan sopimuskaudet. Sopimuskaudet ovat sopimuksen kesto jaettuna sopimusvuosille (ensimmäinen
  ja viimeinen voivat olla vajaat)."
  [{:keys [alkupvm loppupvm tyyppi] :as ur}]
  (let [ensimmainen-vuosi (pvm/vuosi alkupvm)
        viimeinen-vuosi (pvm/vuosi loppupvm)]
    (cond
      (or (= :hoito tyyppi) (= :teiden-hoito tyyppi))
      ;; Hoidon alueurakan hoitokaudet
      (mapv (fn [vuosi]
              [(pvm/hoitokauden-alkupvm vuosi)
               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
            (range ensimmainen-vuosi viimeinen-vuosi))

      (= :vesivayla-hoito tyyppi)
      (vesivaylien-sopimuskaudet ensimmainen-vuosi viimeinen-vuosi)

      :default
      ;; Muiden urakoiden sopimusaika pilkottuna vuosiin
      (pvm/urakan-vuodet alkupvm (pvm/paivan-lopussa loppupvm)))))

(defn edelliset-hoitokaudet
  "Palauttaa N edellistä hoitokautta alkaen nykyajasta."
  ([n] (edelliset-hoitokaudet n false :hoito))
  ([n nykyinenkin? valittu-urakkatyyppi]
   (let [ensimmainen-vuosi (- (t/year (pvm/nyt)) n)
         viimeinen-vuosi (+ (t/year (pvm/nyt))
                            (if nykyinenkin? 1 0))
         hoitokauden-alkupvm-fn (fn [vuosi]
                                  (if (= :vesivayla valittu-urakkatyyppi)
                                    (pvm/vesivaylien-hoitokauden-alkupvm vuosi)
                                    (pvm/hoitokauden-alkupvm vuosi)))
         hoitokauden-loppupvm-fn (fn [vuosi]
                                   (if (= :vesivayla valittu-urakkatyyppi)
                                     (pvm/vesivaylien-hoitokauden-loppupvm vuosi)
                                     (pvm/hoitokauden-loppupvm vuosi)))]
     (mapv (fn [vuosi]
             [(hoitokauden-alkupvm-fn vuosi)
              (hoitokauden-loppupvm-fn (inc vuosi))])
           (range ensimmainen-vuosi viimeinen-vuosi)))))

(defonce valitun-urakan-hoitokaudet
  (reaction (when-let [ur @nav/valittu-urakka]
              (log "MUODOSTA VALITUN URAKAN SOPPARIKAUDET " (:nimi ur))
              (hoito-tai-sopimuskaudet ur))))

(defn hoitokausi-kaynnissa? [[alku loppu]]
  (pvm/valissa? (pvm/nyt) alku loppu))

(defn urakka-kaynnissa? [urakka]
  (->> urakka
       hoito-tai-sopimuskaudet
       (some hoitokausi-kaynnissa?)))

(defonce valittu-urakka-kaynnissa?
  (reaction (some hoitokausi-kaynnissa? @valitun-urakan-hoitokaudet)))

(defn paivystys-kaytossa?
  "Kertoo urakkatyypin perusteella onko päivystys käytössä."
  [ur]
  (boolean
    (or
      (= (:tyyppi ur) :hoito)
      (= (:tyyppi ur) :teiden-hoito)
      (= (:tyyppi ur) :valaistus)
      (= (:tyyppi ur) :siltakorjaus)
      (= (:tyyppi ur) :tekniset-laitteet)
      (or
        (and (= (:tyyppi ur) :paallystys)
             (= (:sopimustyyppi ur) :palvelusopimus))))))

(defn paattele-valittu-hoitokausi [hoitokaudet]
  (when-not (empty? hoitokaudet)
    (let [[alku-pvm _] (first hoitokaudet)
          [_ loppu-pvm] (last hoitokaudet)
          nyt (pvm/nyt)]
      (cond
        ;; Jos urakka ei ole vielä alkanut, valitaan 1. hoitokausi
        (pvm/ennen? nyt alku-pvm)
        (first hoitokaudet)

        ;; Jos urakka on jo päättynyt, valitaan viimeinen hoitokausi
        (pvm/jalkeen? nyt loppu-pvm)
        (last hoitokaudet)

        ;; Jos urakka on käynnissä, valitaan hoitokausi, joka on käynnissä
        :default
        (or (first (filter (fn [[alku loppu]]
                             (pvm/valissa? nyt alku loppu))
                           hoitokaudet))
            ;; ultimate fallback, jos ei löydy jostain syystä, käytä ensimmäistä
            (first hoitokaudet))))))


(defonce valittu-hoitokausi
  (reaction-writable (paattele-valittu-hoitokausi @valitun-urakan-hoitokaudet)))

(defonce valittu-aikavali (reaction-writable @valittu-hoitokausi))

(defn valitse-aikavali! [alku loppu]
  (reset! valittu-aikavali [alku loppu]))

(defn valitse-hoitokausi! [hk]
  (log "------- VALITAAN HOITOKAUSI:" (pr-str hk))
  (reset! valittu-hoitokausi hk)
  (reset! valittu-aikavali [(first hk) (second hk)]))

(def aseta-kuluva-kk-jos-hoitokaudella? (atom false))

(defn- urakan-oletusvuosi [urakka]
  (when urakka
    (min (t/year (:loppupvm urakka))
         (t/year (pvm/nyt)))))

(def valittu-urakan-vuosi (reaction-writable
                            (let [ur @nav/valittu-urakka]
                              (urakan-oletusvuosi ur))))

(defn valitse-urakan-vuosi! [uusi-vuosi]
  (reset! valittu-urakan-vuosi uusi-vuosi))

(defn valitse-urakan-oletusvuosi! [urakka]
  (reset! valittu-urakan-vuosi (urakan-oletusvuosi urakka)))

(defonce valittu-hoitokauden-kuukausi
  (reaction-writable
    (let [hk @valittu-hoitokausi
          ur @nav/valittu-urakka
          kuuluu-hoitokauteen? #(pvm/valissa? (second %) (first hk) (second hk))
          nykyinen-kk (pvm/kuukauden-aikavali (pvm/nyt))
          edellinen-kk (pvm/ed-kk-aikavalina (pvm/nyt))]
      (when (and hk ur)
        (cond
          ;; mm. toteumanäkymissä halutaan käynnissä oleva kuukausi,
          ;; heidän liputettava anna-kuluva-kk-jos-hoitokaudella
          (and @aseta-kuluva-kk-jos-hoitokaudella? (kuuluu-hoitokauteen? nykyinen-kk))
          nykyinen-kk
          ;; raportointinäkymissä halutaan edellinen kuukausi
          ;; Jos nykyhetkeä edeltävä kuukausi kuuluu valittuun hoitokauteen,
          ;; valitaan se. (yleensä raportoidaan aiempaa kuukautta)
          (kuuluu-hoitokauteen? edellinen-kk)
          edellinen-kk

          ;; Valitaan tämä kuukausi, jos se kuuluu hoitokauteen
          (kuuluu-hoitokauteen? nykyinen-kk)
          nykyinen-kk

          ;; Jos hoitokausi ei vielä ole alkanut, valitaan ensimmäinen
          (pvm/ennen? (pvm/nyt) (first hk))
          (first (pvm/aikavalin-kuukausivalit hk))

          ;; fallback on hoitokauden viimeinen kuukausi
          :default
          (last (pvm/aikavalin-kuukausivalit hk)))))))

(defn valitse-hoitokauden-kuukausi! [hk-kk]
  (reset! valittu-hoitokauden-kuukausi hk-kk))

;; rivit ryhmitelty tehtävittäin, rivissä oltava :alkupvm ja :loppupvm
(defn jaljella-olevien-hoitokausien-rivit
  "Palauttaa ne rivit joiden loppupvm on joku jaljella olevien kausien pvm:stä"
  [rivit-tehtavittain jaljella-olevat-kaudet]
  (mapv (fn [tehtavan-rivit]
          (filter (fn [tehtavan-rivi]
                    (some #(pvm/sama-pvm? (second %) (:loppupvm tehtavan-rivi)) jaljella-olevat-kaudet))
                  tehtavan-rivit)) rivit-tehtavittain))

(defn tulevat-hoitokaudet [ur hoitokausi]
  (drop-while #(not (pvm/sama-pvm? (second %) (second hoitokausi)))
              (hoito-tai-sopimuskaudet ur)))

(defn rivit-tulevillekin-kausille [ur rivit hoitokausi]
  (into []
        (mapcat (fn [[alku loppu]]
                  (map (fn [rivi]
                         ;; tässä hoitokausien alkupvm ja loppupvm liitetään töihin
                         (assoc rivi :alkupvm alku :loppupvm loppu)) rivit)))
        (tulevat-hoitokaudet ur hoitokausi)))


;; fixme if you can, man. En saanut kohtuullisessa ajassa tätä generalisoitua
;; siistiksi osaksi rivit-tulevillekin-kausille-funktiota
(defn rivit-tulevillekin-kausille-kok-hint-tyot [ur rivit hoitokausi]
  (into []
        (mapcat (fn [[alku loppu]]
                  (map (fn [rivi]
                         ;; maksupvm:n vuotta täytyy päivittää eikä se välttämättä ole sama kuin työn :vuosi
                         (let [urakan-aloitus-kk (pvm/kuukausi (:alkupvm ur))
                               tyon-kalenteri-vuosi (if (<= urakan-aloitus-kk (:kuukausi rivi) 12)
                                                      (pvm/vuosi alku)
                                                      (pvm/vuosi loppu))
                               maksupvmn-vuoden-erotus (if (:maksupvm rivi)
                                                         (- (time/year (:maksupvm rivi)) (:vuosi rivi))
                                                         0)
                               uusi-maksupvm (if (:maksupvm rivi)
                                               (pvm/luo-pvm (+ tyon-kalenteri-vuosi maksupvmn-vuoden-erotus)
                                                            (- (time/month (:maksupvm rivi)) 1)
                                                            (time/day (:maksupvm rivi)))
                                               nil)]
                           (assoc rivi :alkupvm alku
                                       :loppupvm loppu
                                       :vuosi tyon-kalenteri-vuosi
                                       :maksupvm uusi-maksupvm)))
                       rivit)))
        (tulevat-hoitokaudet ur hoitokausi)))

(defn ryhmittele-hoitokausittain
  "Ottaa rivejä, jotka sisältävät :alkupvm ja :loppupvm, ja palauttaa ne ryhmiteltynä hoitokausiin.
  Palauttaa mäpin, jossa avaimena on hoitokauden [alku loppu] ja arvona on sen hoitokauden rivit.
  Jos sekvenssi hoitokausia on annettu, varmistetaan että mäpissä on kaikille niille avaimet. Tällä tavalla
  voidaan luoda tyhjät ryhmät myös hoitokausille, joilla ei ole yhtään riviä."
  ([rivit] (ryhmittele-hoitokausittain rivit nil))
  ([rivit hoitokaudet]
   (loop [ryhmitelty (into {}
                           (map (fn [[[alkupvm loppupvm] arvot]]
                                  [[(pvm/paivan-alussa alkupvm) (pvm/paivan-lopussa loppupvm)] arvot])
                                (group-by (juxt :alkupvm :loppupvm)
                                          rivit)))
          [kausi & hoitokaudet] hoitokaudet]
     (if-not kausi
       ryhmitelty
       (if (contains? ryhmitelty kausi)
         (recur ryhmitelty hoitokaudet)
         (recur (assoc ryhmitelty kausi []) hoitokaudet))))))


(defonce urakan-toimenpiteet-ja-tehtavat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               ;; pitää hakea uudelleen jos toimenpidekoodeja muokataan
               _ @toimenpidekoodit/koodit]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id
                         (oikeudet/voi-lukea? oikeudet/urakat urakka-id @istunto/kayttaja))
                (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat urakka-id))))

(defonce urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-tehtavat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               ;; pitää hakea uudelleen jos toimenpidekoodeja muokataan
               _ @toimenpidekoodit/koodit]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id
                         (oikeudet/voi-lukea? oikeudet/urakat urakka-id @istunto/kayttaja))
                (urakan-toimenpiteet/hae-urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat urakka-id))))

(defonce valittu-kokonaishintainen-tehtava (atom nil))

(defn valitse-kokonaishintainen-tehtava! [tehtava]
  (reset! valittu-kokonaishintainen-tehtava tehtava))

(defonce urakan-tpin-kokonaishintaiset-tehtavat
  (reaction (let [tpi @valittu-toimenpideinstanssi
                  tehtavat @urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-tehtavat]
              (reset! valittu-kokonaishintainen-tehtava nil)
              (let [tehtavat (into [] (keep (fn [[_ _ t3 t4]]
                                              (when (= (:koodi t3) (:t3_koodi tpi))
                                                t4))
                                            tehtavat))]
                (sort-by :nimi tehtavat)))))

(defonce urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               ;; pitää hakea uudelleen jos toimenpidekoodeja muokataan
               _ @toimenpidekoodit/koodit]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakka-id
                         (oikeudet/voi-lukea? oikeudet/urakat urakka-id @istunto/kayttaja))
                (urakan-toimenpiteet/hae-urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat urakka-id))))

(defonce valittu-yksikkohintainen-tehtava (atom nil))

(defn valitse-yksikkohintainen-tehtava! [tehtava]
  (reset! valittu-yksikkohintainen-tehtava tehtava))

(defonce urakan-tpin-yksikkohintaiset-tehtavat
  (reaction (let [tpi @valittu-toimenpideinstanssi
                  tehtavat @urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat]
              (reset! valittu-yksikkohintainen-tehtava nil)
              (let [tehtavat (into [] (keep (fn [[_ _ t3 t4]]
                                              (when (= (:koodi t3) (:t3_koodi tpi))
                                                t4))
                                            tehtavat))]
                (sort-by :nimi tehtavat)))))

(defonce urakan-muutoshintaiset-toimenpiteet-ja-tehtavat
  (reaction<! [ur (:id @nav/valittu-urakka)

               nakymassa? (or
                            (= :muut (nav/valittu-valilehti :suunnittelu))
                            (= :muut-tyot (nav/valittu-valilehti :toteumat)))]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur nakymassa?)
                (urakan-toimenpiteet/hae-urakan-muutoshintaiset-toimenpiteet-ja-tehtavat ur))))

(defonce urakan-organisaatio
  (reaction<! [ur (:id @nav/valittu-urakka)]
              {:nil-kun-haku-kaynnissa? true}
              (when ur
                (organisaatio/hae-urakan-organisaatio ur))))

(defonce muutoshintaiset-tyot
  (reaction<! [ur (:id @nav/valittu-urakka)
               suunnittelun-sivu (nav/valittu-valilehti :suunnittelu)
               toteuman-sivu (nav/valittu-valilehti :toteumat)]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur (or
                              (= :muut suunnittelun-sivu)
                              (= :muut-tyot toteuman-sivu)))
                (muut-tyot/hae-urakan-muutoshintaiset-tyot ur))))

(defonce toteutuneet-muut-tyot-hoitokaudella
  (reaction<! [ur (:id @nav/valittu-urakka)
               sopimus-id (first @valittu-sopimusnumero)
               aikavali @valittu-hoitokausi
               sivu (nav/valittu-valilehti :toteumat)]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur sopimus-id aikavali (= :muut-tyot sivu))
                (toteumat/hae-urakan-toteutuneet-muut-tyot ur sopimus-id aikavali))))

(defonce erilliskustannukset-hoitokaudella
  (reaction<! [ur (:id @nav/valittu-urakka)
               aikavali @valittu-hoitokausi
               nakymassa? @toteumat/erilliskustannukset-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and ur aikavali nakymassa?)
                (toteumat/hae-urakan-erilliskustannukset ur aikavali))))

(defn vaihda-urakkatyyppi
  [urakka-id uusi-urakkatyyppi]
  (k/post! :tallenna-urakan-tyyppi
           {:urakka-id urakka-id
            :urakkatyyppi uusi-urakkatyyppi}))

(defn aseta-takuu-loppupvm [urakka-id loppupvm]
  (k/post! :aseta-takuun-loppupvm
           {:urakka-id urakka-id
            :takuu {:loppupvm loppupvm}}
           nil true))

(def urakassa-kaytetty-indeksi
  (reaction (when-let [ur @nav/valittu-urakka]
              (when (= :hoito (:tyyppi ur))
                (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm ur))]
                  (cond
                    (< urakan-alkuvuosi 2017) "MAKU 2005"
                    (< urakan-alkuvuosi 2019) "MAKU 2010"
                    :default "MAKU 2015"))))))

(defn lukitse-urakan-yha-sidonta! [urakka-id]
  (when (= @nav/valittu-urakka-id urakka-id)
    (nav/paivita-urakan-tiedot! @nav/valittu-urakka-id assoc-in [:yhatiedot :sidonta-lukittu?] true)))

(defn indeksi-kaytossa?
  "Onko valitussa urakassa indeksi käytössä?"
  []
  (some? (:indeksi @nav/valittu-urakka)))

(def urakan-tiedot-ladattu?
  ;; Kertoo, onko ladattu urakasta kaikki sellaiset tiedot, joihin
  ;; viitataan urakan eri näkymistä. Ei tulisi näyttää urakkaa,
  ;; jos nämä tiedot puuttuu, sillä aiheuttaa ongelmia
  ;; (esim. silloin kun tullaan suoraan lomakkeelle
  ;; ja toimenpideinnstanssit puuttuu).
  (reaction
    (let [toimenpideinstanssit @urakan-toimenpideinstanssit
          tehtavat @urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat]
      (boolean (and toimenpideinstanssit tehtavat)))))

(def urakkatyypin-sanktiolajit
  (reaction<! [urakka @nav/valittu-urakka
               ls-sivu (nav/valittu-valilehti :laadunseuranta)
               vv-ls-sivu (nav/valittu-valilehti :laadunseuranta-vesivaylat)]
              (when (and urakka (or (= :laatupoikkeamat ls-sivu)
                                    (= :sanktiot ls-sivu)
                                    (= :vesivayla-sanktiot vv-ls-sivu)))
                (k/post! :hae-urakkatyypin-sanktiolajit {:urakka-id (:id urakka)
                                                         :urakkatyyppi (:tyyppi urakka)}))))

(def yllapitokohdeurakka?
  (reaction (when-let [urakkatyyppi (:tyyppi @nav/valittu-urakka)]
              (or (= :paallystys urakkatyyppi)
                  (= :paikkaus urakkatyyppi)
                  (= :tiemerkinta urakkatyyppi)))))

(def yllapidon-urakka?
  (reaction (when-let [urakkatyyppi (:tyyppi @nav/valittu-urakka)]
              (or (= :paallystys urakkatyyppi)
                  (= :paikkaus urakkatyyppi)
                  (= :tiemerkinta urakkatyyppi)
                  (= :valaistus urakkatyyppi)))))

(def paallystysurakan-indeksitiedot (atom nil))

(defn ensimmainen-hoitokausi? [urakka hoitokausi]
  (= hoitokausi (first (hoito-tai-sopimuskaudet urakka))))

(defn yllapidon-palvelusopimus? [ur]
  (or
    (and (= (:tyyppi ur) :paallystys)
         (= (:sopimustyyppi ur) :palvelusopimus))
    (and (= (:tyyppi ur) :tiemerkinta)
         (= (:sopimustyyppi ur) :palvelusopimus))))
